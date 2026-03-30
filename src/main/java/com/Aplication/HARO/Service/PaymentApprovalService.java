package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Repository.ChatbotMatriculaProcesoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class PaymentApprovalService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApprovalService.class);

    private static final ZoneId CONTRACT_ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter CONTRACT_EXPIRES_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(CONTRACT_ZONE);

    private final ChatbotMatriculaProcesoRepository procesoRepository;
    private final VerificationService verificationService;
    private final ChatbotProcesoService chatbotProcesoService;
    private final WhatsAppTemplateService waService;
    private final ProspectoService prospectoService;
    private final MailService mailService;
    private final RestTemplate restTemplate;

    @Value("${chatbot.contract.base-url:}")
    private String contractBaseUrl;

    @Value("${chatbot.contract.ui-url:}")
    private String contractUiUrl;

    @Value("${chatbot.auto-send-contract-on-payment:true}")
    private boolean autoSendContractOnPayment;

    @Value("${chatbot.auto-send-contract-email-on-payment:true}")
    private boolean autoSendContractEmailOnPayment;

    @Value("${chatbot.contract.email.subject:Enlace de contratos - CEA HARO}")
    private String contractEmailSubject;

    @Value("${app.internal-api-base-url:}")
    private String internalApiBaseUrl;

    public record ApprovalResult(
            String status,
            String paymentStatus,
            String flowStatus,
            String contractLink,
            boolean whatsappSent,
            boolean duplicate
    ) {}

    public PaymentApprovalService(ChatbotMatriculaProcesoRepository procesoRepository,
                                  VerificationService verificationService,
                                  ChatbotProcesoService chatbotProcesoService,
                                  WhatsAppTemplateService waService,
                                  ProspectoService prospectoService,
                                  MailService mailService,
                                  RestTemplate restTemplate) {
        this.procesoRepository = procesoRepository;
        this.verificationService = verificationService;
        this.chatbotProcesoService = chatbotProcesoService;
        this.waService = waService;
        this.prospectoService = prospectoService;
        this.mailService = mailService;
        this.restTemplate = restTemplate;
    }

    public record ContractSendResult(
            boolean ok,
            String message,
            String paymentStatus,
            String flowStatus,
            String contractStatus,
            String contractLink,
            boolean sent,
            Instant expiresAt
    ) {}

    @Transactional
    public ApprovalResult handleApprovedPayment(String documentoRaw, BigDecimal amount) {
        return handleApprovedPayment(documentoRaw, amount, true);
    }

    @Transactional
    public ApprovalResult handleApprovedPayment(String documentoRaw, BigDecimal amount, boolean notifyContractLinkByChatbot) {
        String documento = normalizeDocumento(documentoRaw);
        if (!StringUtils.hasText(documento)) {
            throw new IllegalArgumentException("documento es requerido");
        }

        log.info("\uD83D\uDCB0 Pago aprobado detectado doc={} amount={}", documento, amount);

        ChatbotMatriculaProceso proceso = procesoRepository.findByNumeroDocumentoForUpdate(documento)
                .orElseThrow(() -> new NoSuchElementException("No existe proceso de matricula para documento " + documento));

        if (isAlreadyProcessed(proceso)) {
            String currentContractLink = resolveCurrentContractLink(proceso);
            if (!Objects.equals(trim(proceso.getContractLink()), currentContractLink)) {
                proceso.setContractLink(currentContractLink);
                procesoRepository.save(proceso);
            }
            log.info("\uD83D\uDD01 Pago ya procesado anteriormente doc={} paymentStatus={} flowStatus={} contractStatus={}",
                    documento, trim(proceso.getPaymentStatus()), trim(proceso.getFlowStatus()), trim(proceso.getContractStatus()));
            return toResult("OK", proceso, false, true);
        }

        proceso.setPaymentStatus("APPROVED");
        proceso.setFlowStatus("PAID");
        if (trim(proceso.getMetodoPago()).isBlank()) {
            proceso.setMetodoPago("EPAYCO");
        }
        if (proceso.getPaymentConfirmedAt() == null) {
            proceso.setPaymentConfirmedAt(Instant.now());
        }
        if (amount != null && amount.signum() >= 0) {
            proceso.setPaymentAmount(amount);
        }

        String contractLink = normalizeStoredContractLink(proceso.getContractLink());
        if (!Objects.equals(trim(proceso.getContractLink()), contractLink)) {
            proceso.setContractLink(contractLink);
        }

        if (shouldRefreshContractLink(contractLink)) {

            VerificationService.ContractLinkResult out =
                    verificationService.createContractVerificationLink(
                            proceso.getEmail(),
                            contractBaseUrl
                    );

            contractLink = buildContractUserLink(out);
            proceso.setContractLink(contractLink);
        }

        procesoRepository.save(proceso);

        // Si el contrato ya esta firmado, intenta finalizar la matricula automaticamente.
        try {
            chatbotProcesoService.tryFinalizeEnrollmentIfReadyByDocumento(documento);
        } catch (Exception ex) {
            log.warn("No se pudo finalizar matricula automaticamente tras pago aprobado doc={}: {}", documento, ex.getMessage());
        }

        log.info("\u2705 Pago persistido doc={} paymentStatus={} flowStatus={}",
                documento, trim(proceso.getPaymentStatus()), trim(proceso.getFlowStatus()));

        // Correo: enviar link de contratos (best-effort, no revienta el flujo si falla).
        if (autoSendContractEmailOnPayment) {
            try {
                sendContractLinkByEmailInternal(proceso, contractLink, contractExpiresAtFromLink(contractLink), false);
            } catch (Exception ex) {
                log.warn("\u26A0\uFE0F No se pudo enviar correo de contratos doc={}: {}", documento, ex.getMessage());
            }
        }

        boolean whatsappSent = false;
        if (!notifyContractLinkByChatbot) {
            log.info("\u2139\uFE0F Pago aprobado sin envio automatico de WhatsApp. doc={}", documento);
            return toResult("OK", proceso, false, false);
        }

        if (!autoSendContractOnPayment) {
            log.info("\u2139\uFE0F Envio de contrato por WhatsApp deshabilitado por configuracion. doc={}", documento);
            return toResult("OK", proceso, false, false);
        }

        String phone = trim(proceso.getPhone());
        if (!StringUtils.hasText(phone)) {
            // Compat: procesos antiguos guardaban el numero en "telefono" y no en "phone".
            phone = trim(proceso.getTelefono());
        }
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(contractLink)) {
            log.warn("\u26A0\uFE0F No se pudo preparar WhatsApp por datos incompletos doc={} phonePresent={} contractLinkPresent={}",
                    documento, StringUtils.hasText(phone), StringUtils.hasText(contractLink));
            return toResult("OK", proceso, false, false);
        }

        whatsappSent = sendPaymentApprovedMessage(phone, contractLink);
        if (whatsappSent) {
            proceso.setContractStatus("LINK_SENT");
            proceso.setContractChatbotSent(true);
            proceso.setContractChatbotSentAt(Instant.now());
            procesoRepository.save(proceso);
        }

        return toResult("OK", proceso, whatsappSent, false);
    }

    @Transactional
    public ContractSendResult confirmCashPaymentManual(String documentoRaw,
                                                       BigDecimal amount,
                                                       Long adminId,
                                                       String observation,
                                                       boolean sendEmail,
                                                       boolean sendChatbot,
                                                       boolean requireProspect) {
        String documento = normalizeDocumento(documentoRaw);
        if (!StringUtils.hasText(documento)) {
            return new ContractSendResult(false, "documento es requerido", "", "", "", "", false, null);
        }

        ChatbotMatriculaProceso proceso = procesoRepository.findByNumeroDocumentoForUpdate(documento)
                .orElseThrow(() -> new NoSuchElementException("No existe proceso de matricula para documento " + documento));

        proceso.setMetodoPago("EFECTIVO");
        proceso.setPaymentValidatedByAdminId(adminId);
        proceso.setPaymentObservation(trim(observation));
        proceso.setPaymentConfirmedAt(Instant.now());

        if (!"APPROVED".equalsIgnoreCase(trim(proceso.getPaymentStatus()))) {
            proceso.setPaymentStatus("APPROVED");
        }
        if (amount != null && amount.signum() >= 0) {
            proceso.setPaymentAmount(amount);
        }
        if (trim(proceso.getFlowStatus()).isBlank() || "DRAFT".equalsIgnoreCase(trim(proceso.getFlowStatus()))) {
            proceso.setFlowStatus("PAID");
        }

        String contractLink = ensureValidContractLink(proceso);
        procesoRepository.save(proceso);

        Instant expiresAt = contractExpiresAtFromLink(contractLink);

        boolean emailed = false;
        if (sendEmail) {
            emailed = sendContractLinkByEmailInternal(proceso, contractLink, expiresAt, true);
        }

        boolean chatted = false;
        if (sendChatbot) {
            ContractSendResult out = sendContractLinkByChatbot(documento, requireProspect, true);
            chatted = out.sent();
        }

        // Si el contrato ya estaba firmado, intenta finalizar matricula (best effort).
        try {
            chatbotProcesoService.tryFinalizeEnrollmentIfReadyByDocumento(documento);
        } catch (Exception ex) {
            log.warn("No se pudo finalizar matricula automaticamente tras pago manual doc={}: {}", documento, ex.getMessage());
        }

        String msg = emailed
                ? "Pago confirmado y enlace de contratos enviado al correo."
                : "Pago confirmado. Enlace de contratos preparado.";

        return new ContractSendResult(true, msg,
                trim(proceso.getPaymentStatus()),
                trim(proceso.getFlowStatus()),
                trim(proceso.getContractStatus()),
                contractLink,
                emailed || chatted,
                expiresAt);
    }

    @Transactional
    public ContractSendResult sendContractLinkByEmail(String documentoRaw, boolean forceResend) {
        String documento = normalizeDocumento(documentoRaw);
        if (!StringUtils.hasText(documento)) {
            return new ContractSendResult(false, "documento es requerido", "", "", "", "", false, null);
        }

        ChatbotMatriculaProceso proceso = procesoRepository.findByNumeroDocumentoForUpdate(documento)
                .orElseThrow(() -> new NoSuchElementException("No existe proceso de matricula para documento " + documento));

        if (!"APPROVED".equalsIgnoreCase(trim(proceso.getPaymentStatus()))) {
            return new ContractSendResult(false, "El pago aun no esta confirmado.", trim(proceso.getPaymentStatus()),
                    trim(proceso.getFlowStatus()), trim(proceso.getContractStatus()), "", false, null);
        }

        String contractLink = ensureValidContractLink(proceso);
        procesoRepository.save(proceso);

        Instant expiresAt = contractExpiresAtFromLink(contractLink);
        boolean sent = sendContractLinkByEmailInternal(proceso, contractLink, expiresAt, forceResend);
        return new ContractSendResult(true,
                sent ? "Enlace de contratos enviado por correo." : "El enlace ya habia sido enviado por correo.",
                trim(proceso.getPaymentStatus()),
                trim(proceso.getFlowStatus()),
                trim(proceso.getContractStatus()),
                contractLink,
                sent,
                expiresAt);
    }

    @Transactional
    public ContractSendResult sendContractLinkByChatbot(String documentoRaw, boolean requireProspect, boolean forceResend) {
        String documento = normalizeDocumento(documentoRaw);
        if (!StringUtils.hasText(documento)) {
            return new ContractSendResult(false, "documento es requerido", "", "", "", "", false, null);
        }

        ChatbotMatriculaProceso proceso = procesoRepository.findByNumeroDocumentoForUpdate(documento)
                .orElseThrow(() -> new NoSuchElementException("No existe proceso de matricula para documento " + documento));

        if (!"APPROVED".equalsIgnoreCase(trim(proceso.getPaymentStatus()))) {
            return new ContractSendResult(false, "El pago aun no esta confirmado.", trim(proceso.getPaymentStatus()),
                    trim(proceso.getFlowStatus()), trim(proceso.getContractStatus()), "", false, null);
        }

        String phone = firstNotBlank(trim(proceso.getPhone()), trim(proceso.getTelefono()));
        if (!StringUtils.hasText(phone)) {
            return new ContractSendResult(false, "No hay telefono para enviar por chatbot.",
                    trim(proceso.getPaymentStatus()), trim(proceso.getFlowStatus()), trim(proceso.getContractStatus()), "", false, null);
        }

        if (requireProspect && !prospectoService.existeProspectoActivoPorTelefono(phone)) {
            return new ContractSendResult(false,
                    "No es posible enviar por chatbot: el estudiante no se encuentra en prospectos activos.",
                    trim(proceso.getPaymentStatus()), trim(proceso.getFlowStatus()), trim(proceso.getContractStatus()), "", false, null);
        }

        if (!forceResend && Boolean.TRUE.equals(proceso.getContractChatbotSent())) {
            return new ContractSendResult(true,
                    "El enlace ya habia sido enviado por chatbot.",
                    trim(proceso.getPaymentStatus()), trim(proceso.getFlowStatus()), trim(proceso.getContractStatus()),
                    normalizeStoredContractLink(proceso.getContractLink()), false, contractExpiresAtFromLink(proceso.getContractLink()));
        }

        String contractLink = ensureValidContractLink(proceso);
        Instant expiresAt = contractExpiresAtFromLink(contractLink);

        String message = "Hola, tu proceso de matricula ya tiene habilitada la etapa de firma de contratos.\n\n"
                + "Por favor ingresa al siguiente enlace para revisarlos y firmarlos:\n"
                + contractLink
                + "\n\n"
                + buildContractExpiryHint(expiresAt);

        boolean sent = false;
        try {
            sent = sendWhatsappText(phone, message);
        } catch (Exception ex) {
            log.error("No se pudo enviar mensaje de contratos por WhatsApp doc={}: {}", documento, ex.getMessage(), ex);
            sent = false;
        }

        if (sent) {
            proceso.setContractChatbotSent(true);
            proceso.setContractChatbotSentAt(Instant.now());
            proceso.setContractStatus("LINK_SENT");
            if (trim(proceso.getFlowStatus()).isBlank() || "DRAFT".equalsIgnoreCase(trim(proceso.getFlowStatus()))) {
                proceso.setFlowStatus("CONTRACT_LINK_SENT");
            }
            procesoRepository.save(proceso);
        }

        return new ContractSendResult(true,
                sent ? "Enlace de contratos enviado por chatbot." : "No se pudo enviar el enlace por chatbot.",
                trim(proceso.getPaymentStatus()), trim(proceso.getFlowStatus()), trim(proceso.getContractStatus()),
                contractLink, sent, expiresAt);
    }

    public boolean sendPaymentApprovedMessage(String phoneRaw, String contractLinkRaw) {
        String phone = normalizePhone(phoneRaw);
        String contractLink = normalizeStoredContractLink(contractLinkRaw);
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(contractLink)) {
            return false;
        }

        Instant expiresAt = null;
        try {
            VerificationService.ContractAccessResult peek = peekContractLinkAccess(contractLink);
            expiresAt = peek == null ? null : peek.expiresAt();
        } catch (Exception ignored) {
            expiresAt = null;
        }

        String message = "\u2705 Tu pago fue aprobado.\n\n\uD83D\uDCC4 Continua con tu contrato aqui:\n"
                + contractLink
                + "\n\n"
                + buildContractExpiryHint(expiresAt);
        log.info("\uD83D\uDCF2 Preparando envio por WhatsApp a {} con contractUiUrl={} contractLink={}",
                maskPhone(phone),
                normalizeContractUiUrl(contractUiUrl),
                contractLink);

        try {
            if (StringUtils.hasText(internalApiBaseUrl)) {
                String base = trim(internalApiBaseUrl);
                while (base.endsWith("/")) {
                    base = base.substring(0, base.length() - 1);
                }
                String url = base + "/api/whatsapp/text/send";
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("to", phone);
                payload.put("text", message);
                payload.put("message", message);

                ResponseEntity<Map> response = restTemplate.postForEntity(url, payload, Map.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("\u2705 Mensaje de WhatsApp enviado correctamente to={}", maskPhone(phone));
                    return true;
                }
                log.warn("\u26A0\uFE0F WhatsApp endpoint respondio con estado no exitoso. statusCode={} to={}",
                        response.getStatusCode().value(), maskPhone(phone));
                return false;
            }

            waService.sendTextMessage(phone, message);
            log.info("\u2705 Mensaje de WhatsApp enviado correctamente to={}", maskPhone(phone));
            return true;
        } catch (RestClientException ex) {
            log.error("\u274C Error enviando WhatsApp via endpoint interno to={}: {}", maskPhone(phone), ex.getMessage(), ex);
            return false;
        } catch (Exception ex) {
            log.error("\u274C Error enviando WhatsApp to={}: {}", maskPhone(phone), ex.getMessage(), ex);
            return false;
        }
    }

    private ApprovalResult toResult(String status, ChatbotMatriculaProceso proceso, boolean whatsappSent, boolean duplicate) {
        return new ApprovalResult(
                status,
                trim(proceso.getPaymentStatus()),
                trim(proceso.getFlowStatus()),
                resolveCurrentContractLink(proceso),
                whatsappSent,
                duplicate
        );
    }

    private boolean isAlreadyProcessed(ChatbotMatriculaProceso proceso) {
        String paymentStatus = trim(proceso.getPaymentStatus()).toUpperCase(Locale.ROOT);
        String contractStatus = trim(proceso.getContractStatus()).toUpperCase(Locale.ROOT);
        String flowStatus = trim(proceso.getFlowStatus()).toUpperCase(Locale.ROOT);
        return "APPROVED".equals(paymentStatus)
                && ("LINK_SENT".equals(contractStatus)
                || "SIGNED".equals(contractStatus)
                || "CONTRACT_LINK_SENT".equals(flowStatus)
                || "CONTRACT_SIGNED".equals(flowStatus)
                || "STUDENT_CREATED".equals(flowStatus));
    }

    private String buildContractUserLink(VerificationService.ContractLinkResult out) {
        if (out == null) return "";
        String ui = resolveContractUiUrl(out);
        if (!StringUtils.hasText(ui)) return trim(out.url());
        String link = appendQueryParam(ui, "email", out.email());
        link = appendQueryParam(link, "code", out.code());
        // apiBase is optional. Only add when it looks like a real backend base URL.
        if (looksLikeBackendBaseUrl(contractBaseUrl)) link = appendQueryParam(link, "apiBase", trim(contractBaseUrl));
        return link;
    }

    private String resolveContractUiUrl(VerificationService.ContractLinkResult out) {
        String ui = normalizeContractUiUrl(contractUiUrl);
        if (StringUtils.hasText(ui)) return ui;

        // Fallback 1: use configured base and point to the actual UI (avoid /api/verification/... links).
        String base = trim(contractBaseUrl);
        if (StringUtils.hasText(base) && !looksLikeBackendBaseUrl(base)) {
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            return base + "/Contratos/contrato.html";
        }

        // Fallback 2: derive origin from the verification URL returned by backend.
        String verificationUrl = out == null ? "" : trim(out.url());
        if (!StringUtils.hasText(verificationUrl)) return "";
        try {
            java.net.URL u = new java.net.URL(verificationUrl);
            return u.getProtocol() + "://" + u.getAuthority() + "/Contratos/contrato.html";
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean looksLikeBackendBaseUrl(String raw) {
        String v = trim(raw).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(v)) return false;
        // Heuristic: avoid passing the website domain as apiBase (causes extra 404s).
        return v.contains("run.app")
                || v.contains("localhost")
                || v.matches(".*:\\d{2,5}$");
    }

    private boolean shouldRefreshContractLink(String contractLinkRaw) {
        String contractLink = normalizeStoredContractLink(contractLinkRaw);
        if (!StringUtils.hasText(contractLink)) {
            return true;
        }

        // Refresh if the access code embedded in the link is no longer valid (expired/consumed/not found).
        try {
            VerificationService.ContractAccessResult peek = peekContractLinkAccess(contractLink);
            if (peek == null || !peek.ok()) {
                return true;
            }
        } catch (Exception ex) {
            // If peeking fails, do not force refresh to avoid loops.
        }

        String expectedUi = normalizeContractUiUrl(contractUiUrl);
        if (!StringUtils.hasText(expectedUi)) {
            return false;
        }
        return !contractLink.startsWith(expectedUi);
    }

    private record ContractAccessParams(String email, String code) {}

    private VerificationService.ContractAccessResult peekContractLinkAccess(String contractLinkRaw) {
        ContractAccessParams params = parseContractAccessParams(contractLinkRaw);
        if (!StringUtils.hasText(params.email()) || !StringUtils.hasText(params.code())) {
            return new VerificationService.ContractAccessResult(false, "Codigo requerido", null);
        }
        return verificationService.peekContractAccessCode(params.email(), params.code());
    }

    private ContractAccessParams parseContractAccessParams(String contractLinkRaw) {
        String link = trim(contractLinkRaw);
        if (!StringUtils.hasText(link)) {
            return new ContractAccessParams("", "");
        }
        return new ContractAccessParams(
                readQueryParam(link, "email"),
                readQueryParam(link, "code")
        );
    }

    private String readQueryParam(String urlRaw, String keyRaw) {
        String url = trim(urlRaw);
        String key = trim(keyRaw);
        if (!StringUtils.hasText(url) || !StringUtils.hasText(key)) {
            return "";
        }
        try {
            URI uri = URI.create(url);
            String rawQuery = uri.getRawQuery();
            if (!StringUtils.hasText(rawQuery)) {
                return "";
            }
            for (String part : rawQuery.split("&")) {
                if (!StringUtils.hasText(part)) continue;
                int idx = part.indexOf('=');
                String k = idx >= 0 ? part.substring(0, idx) : part;
                String v = idx >= 0 ? part.substring(idx + 1) : "";
                String dk = URLDecoder.decode(k, StandardCharsets.UTF_8);
                if (!dk.equalsIgnoreCase(key)) continue;
                return URLDecoder.decode(v, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private String buildContractExpiryHint(Instant expiresAt) {
        if (expiresAt == null) {
            return "\u23f3 Este enlace es temporal. Si se vence, escribe *LINK* para generar otro.";
        }
        String until = CONTRACT_EXPIRES_FMT.format(expiresAt);
        return "\u23f3 Vigente hasta: " + until + " (hora Colombia). Si se vence, escribe *LINK* para generar otro.";
    }

    private String normalizeContractUiUrl(String rawUiUrl) {
        String ui = trim(rawUiUrl);
        if (!StringUtils.hasText(ui)) {
            return ui;
        }
        return ui.replaceFirst("(?i)(?:/Contratos)*/contrato\\.html(?=($|[?#]))", "/Contratos/contrato.html");
    }

    private String resolveCurrentContractLink(ChatbotMatriculaProceso proceso) {
        if (proceso == null) {
            return "";
        }

        String currentLink = normalizeStoredContractLink(proceso.getContractLink());
        if (!shouldRefreshContractLink(currentLink)) {
            return currentLink;
        }

        String email = trim(proceso.getEmail());
        if (!StringUtils.hasText(email)) {
            return currentLink;
        }

        try {
            VerificationService.ContractLinkResult out =
                    verificationService.createContractVerificationLink(email, contractBaseUrl);
            return buildContractUserLink(out);
        } catch (Exception ex) {
            log.warn("No se pudo reconstruir contractLink para proceso id={}: {}",
                    proceso.getId(), ex.getMessage());
            return currentLink;
        }
    }

    private Instant contractExpiresAtFromLink(String contractLinkRaw) {
        String link = normalizeStoredContractLink(contractLinkRaw);
        if (!StringUtils.hasText(link)) return null;
        try {
            VerificationService.ContractAccessResult peek = peekContractLinkAccess(link);
            return peek == null ? null : peek.expiresAt();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String ensureValidContractLink(ChatbotMatriculaProceso proceso) {
        if (proceso == null) return "";
        String current = resolveCurrentContractLink(proceso);
        if (!Objects.equals(trim(proceso.getContractLink()), current)) {
            proceso.setContractLink(current);
        }
        return current;
    }

    private boolean sendContractLinkByEmailInternal(ChatbotMatriculaProceso proceso,
                                                   String contractLinkRaw,
                                                   Instant expiresAt,
                                                   boolean forceResend) {
        if (proceso == null) return false;

        String email = trim(proceso.getEmail());
        String contractLink = normalizeStoredContractLink(contractLinkRaw);
        if (!StringUtils.hasText(email) || !StringUtils.hasText(contractLink)) {
            return false;
        }

        if (!forceResend && Boolean.TRUE.equals(proceso.getContractEmailSent())) {
            return false;
        }

        String subject = trim(contractEmailSubject);
        if (!StringUtils.hasText(subject)) {
            subject = "Enlace de contratos - CEA HARO";
        }

        String html = buildContractLinkEmailHtml(proceso, contractLink, expiresAt);
        String plain = buildContractLinkEmailPlain(proceso, contractLink, expiresAt);

        try {
            mailService.sendHtml(email, subject, html, plain);
        } catch (Exception ex) {
            log.error("No se pudo enviar correo de contratos email={} doc={}: {}",
                    maskEmail(email), safe(proceso.getNumeroDocumento()), ex.getMessage(), ex);
            return false;
        }

        proceso.setContractEmailSent(true);
        proceso.setContractEmailSentAt(Instant.now());
        procesoRepository.save(proceso);
        return true;
    }

    private String buildContractLinkEmailHtml(ChatbotMatriculaProceso proceso, String contractLink, Instant expiresAt) {
        String nombre = escapeHtml(firstNotBlank(trim(proceso.getNombreCompleto()), "Estudiante"));
        String expiry = escapeHtml(buildContractExpiryHint(expiresAt));
        String linkEscaped = escapeHtml(contractLink);
        return """
                <div style="font-family:Segoe UI, Arial, sans-serif; color:#111827; line-height:1.5;">
                  <h2 style="margin:0 0 12px 0;">Enlace para firma de contratos</h2>
                  <p style="margin:0 0 12px 0;">Hola %s,</p>
                  <p style="margin:0 0 12px 0;">Tu proceso ya tiene habilitada la etapa de firma de contratos. Ingresa al siguiente enlace:</p>
                  <p style="margin:0 0 12px 0;"><a href="%s" target="_blank" rel="noopener">%s</a></p>
                  <p style="margin:0 0 12px 0;">%s</p>
                  <p style="margin:0;">Si necesitas ayuda, responde a este mensaje o escribe ASESOR por WhatsApp.</p>
                </div>
                """.formatted(nombre, linkEscaped, linkEscaped, expiry);
    }

    private String buildContractLinkEmailPlain(ChatbotMatriculaProceso proceso, String contractLink, Instant expiresAt) {
        String nombre = firstNotBlank(trim(proceso == null ? "" : proceso.getNombreCompleto()), "Estudiante");
        return "Enlace para firma de contratos\n\n"
                + "Hola " + nombre + ",\n\n"
                + "Tu proceso ya tiene habilitada la etapa de firma de contratos. Ingresa al siguiente enlace:\n"
                + contractLink + "\n\n"
                + buildContractExpiryHint(expiresAt) + "\n\n"
                + "Si necesitas ayuda, escribe ASESOR.";
    }

    private boolean sendWhatsappText(String phoneRaw, String message) {
        String phone = normalizePhone(phoneRaw);
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(message)) {
            return false;
        }

        try {
            if (StringUtils.hasText(internalApiBaseUrl)) {
                String base = trim(internalApiBaseUrl);
                while (base.endsWith("/")) {
                    base = base.substring(0, base.length() - 1);
                }
                String url = base + "/api/whatsapp/text/send";
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("to", phone);
                payload.put("text", message);
                payload.put("message", message);

                ResponseEntity<Map> response = restTemplate.postForEntity(url, payload, Map.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    return true;
                }
                return false;
            }

            waService.sendTextMessage(phone, message);
            return true;
        } catch (Exception ex) {
            log.error("Error enviando WhatsApp to={}: {}", maskPhone(phone), ex.getMessage(), ex);
            return false;
        }
    }

    private String firstNotBlank(String... values) {
        if (values == null) return "";
        for (String v : values) {
            String t = trim(v);
            if (!t.isBlank()) return t;
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String maskEmail(String email) {
        String e = trim(email);
        int at = e.indexOf('@');
        if (at <= 1) return "***";
        String left = e.substring(0, at);
        String domain = e.substring(at);
        String maskedLeft = left.substring(0, 1) + "***" + left.substring(Math.max(1, left.length() - 1));
        return maskedLeft + domain;
    }

    private String escapeHtml(String raw) {
        String v = raw == null ? "" : raw;
        return v.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String normalizeStoredContractLink(String rawContractLink) {
        return normalizeContractUiUrl(trim(rawContractLink));
    }

    private String appendQueryParam(String baseUrl, String key, String value) {
        String base = trim(baseUrl);
        if (!StringUtils.hasText(base) || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return base;
        }
        String separator = base.contains("?") ? "&" : "?";
        return base + separator
                + URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String normalizeDocumento(String value) {
        return trim(value).replaceAll("\\s+", "");
    }

    private String normalizePhone(String value) {
        String normalized = trim(value).replaceAll("[\\s\\-()]", "");
        if (normalized.startsWith("+")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String maskPhone(String phone) {
        String normalized = normalizePhone(phone);
        if (!StringUtils.hasText(normalized) || normalized.length() <= 4) {
            return "****";
        }
        return "*".repeat(normalized.length() - 4) + normalized.substring(normalized.length() - 4);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
