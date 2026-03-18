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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class PaymentApprovalService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApprovalService.class);

    private final ChatbotMatriculaProcesoRepository procesoRepository;
    private final VerificationService verificationService;
    private final ChatbotProcesoService chatbotProcesoService;
    private final WhatsAppTemplateService waService;
    private final RestTemplate restTemplate;

    @Value("${chatbot.contract.base-url:}")
    private String contractBaseUrl;

    @Value("${chatbot.contract.ui-url:}")
    private String contractUiUrl;

    @Value("${chatbot.auto-send-contract-on-payment:true}")
    private boolean autoSendContractOnPayment;

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
                                  WhatsAppTemplateService waService) {
        this.procesoRepository = procesoRepository;
        this.verificationService = verificationService;
        this.chatbotProcesoService = chatbotProcesoService;
        this.waService = waService;
        this.restTemplate = new RestTemplate();
    }

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
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(contractLink)) {
            log.warn("\u26A0\uFE0F No se pudo preparar WhatsApp por datos incompletos doc={} phonePresent={} contractLinkPresent={}",
                    documento, StringUtils.hasText(phone), StringUtils.hasText(contractLink));
            return toResult("OK", proceso, false, false);
        }

        whatsappSent = sendPaymentApprovedMessage(phone, contractLink);
        if (whatsappSent) {
            proceso.setContractStatus("LINK_SENT");
            procesoRepository.save(proceso);
        }

        return toResult("OK", proceso, whatsappSent, false);
    }

    public boolean sendPaymentApprovedMessage(String phoneRaw, String contractLinkRaw) {
        String phone = normalizePhone(phoneRaw);
        String contractLink = normalizeStoredContractLink(contractLinkRaw);
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(contractLink)) {
            return false;
        }

        String message = "\u2705 Tu pago fue aprobado.\n\n\uD83D\uDCC4 Continua con tu contrato aqui:\n" + contractLink;
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
        String ui = normalizeContractUiUrl(contractUiUrl);
        if (!StringUtils.hasText(ui)) {
            return trim(out.url());
        }
        String link = appendQueryParam(ui, "email", out.email());
        link = appendQueryParam(link, "code", out.code());
        if (StringUtils.hasText(contractBaseUrl)) {
            link = appendQueryParam(link, "apiBase", trim(contractBaseUrl));
        }
        return link;
    }

    private boolean shouldRefreshContractLink(String contractLinkRaw) {
        String contractLink = normalizeStoredContractLink(contractLinkRaw);
        if (!StringUtils.hasText(contractLink)) {
            return true;
        }
        String expectedUi = normalizeContractUiUrl(contractUiUrl);
        if (!StringUtils.hasText(expectedUi)) {
            return false;
        }
        return !contractLink.startsWith(expectedUi);
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
