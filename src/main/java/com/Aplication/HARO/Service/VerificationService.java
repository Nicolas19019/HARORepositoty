// src/main/java/com/Aplication/HARO/Service/VerificationService.java
package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Model.OtpToken;
import com.Aplication.HARO.Repository.OtpTokenRepository;
import com.Aplication.HARO.Security.OtpHasher;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.util.StringUtils;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final OtpTokenRepository repo;
    private final MailService mail;
    private final EstudianteService estudianteService;
    private final ChatbotProcesoService chatbotProcesoService;
    private final WhatsAppTemplateService waService;
    private final ContractDocumentStorageService contractDocumentStorageService;
    private final SecureRandom rng = new SecureRandom();

    // ===== Config =====
    @Value("${app.verification.brand:CEA HARO}")
    private String brand;

    @Value("${app.verification.subject:Verificación de correo – CEA HARO}")
    private String subject;

    @Value("${app.verification.otp.length:6}")
    private int otpLength;

    /** TTL en segundos del código (p. ej. 10 min = 600) */
    @Value("${app.verification.ttlSeconds:600}")
    private long ttlSeconds;

    /** Cooldown entre envíos (para evitar spam). p. ej. 30s */
    @Value("${app.verification.cooldownSeconds:30}")
    private long cooldownSeconds;

    /** Intentos máximos de verificación antes de invalidar el código */
    @Value("${app.verification.maxAttempts:5}")
    private int maxAttempts;

    @Value("${app.verification.logo-url:}")
    private String verificationLogoUrl;

    /** Ruta del logo inline (resources/email-assets/LogoHARO.png) */
    private static final String LOGO_CLASSPATH = "email-assets/LogoHARO.png";

    /** CID usado dentro del HTML: <img src="cid:logoHaro"> */
    private static final String CID = "logoHaro";

    // ===== Contract verification config =====
    @Value("${app.contract.verify.code.length:10}")
    private int contractCodeLength;

    @Value("${app.contract.verify.ttlSeconds:2400}") // 40 min
    private long contractTtlSeconds;

    @Value("${app.contract.verify.base-url:http://localhost:8081}")
    private String contractVerifyBaseUrl;

    @Value("${app.contract.verify.path:/api/verification/contract/verify}")
    private String contractVerifyPath;

    @Value("${chatbot.auto-send-enrollment-whatsapp:false}")
    private boolean autoSendEnrollmentWhatsapp;

    public VerificationService(OtpTokenRepository repo,
                               MailService mail,
                               EstudianteService estudianteService,
                               ChatbotProcesoService chatbotProcesoService,
                               WhatsAppTemplateService waService,
                               ContractDocumentStorageService contractDocumentStorageService) {
        this.repo = repo;
        this.mail = mail;
        this.estudianteService = estudianteService;
        this.chatbotProcesoService = chatbotProcesoService;
        this.waService = waService;
        this.contractDocumentStorageService = contractDocumentStorageService;
    }

    @PostConstruct
    void init() {
        otpLength = Math.max(4, otpLength);
        ttlSeconds = Math.max(60, ttlSeconds);
        cooldownSeconds = Math.max(5, cooldownSeconds);
        maxAttempts = Math.max(1, maxAttempts);
        contractCodeLength = Math.max(8, contractCodeLength);
        contractTtlSeconds = Math.max(60, contractTtlSeconds);
    }

    /* =========================================================
       API utilizada por tu VerificationController
       ========================================================= */

    /** Envía/renueva OTP al correo. Respeta cooldown y persiste el token (hash). */
    @Transactional
    public void sendEmailVerification(String rawEmail) {
        final String email = normalizeEmail(rawEmail);
        final Instant now = Instant.now();

        // 1) Borrar tokens expirados (sanidad)
        repo.deleteByEmailAndPurposeAndExpiresAtBefore(email, "EMAIL_VERIFY", now);

        // 2) Anti-spam: cooldown contra último envío aún no consumido
        Optional<OtpToken> lastNotConsumed =
                repo.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc(email, "EMAIL_VERIFY");
        if (lastNotConsumed.isPresent()) {
            OtpToken last = lastNotConsumed.get();
            if (last.getSentAt() != null && now.isBefore(last.getSentAt().plusSeconds(cooldownSeconds))) {
                long wait = Math.max(1, ChronoUnit.SECONDS.between(now, last.getSentAt().plusSeconds(cooldownSeconds)));
                throw new IllegalStateException("Espera " + wait + "s para reenviar el código.");
            }
        }

        // 3) Generar OTP + hash y persistir
        String code = generateNumericOtp(otpLength);
        String hash = OtpHasher.sha256(code);

        OtpToken token = new OtpToken();
        token.setEmail(email);
        token.setPurpose("EMAIL_VERIFY");
        token.setOtpHash(hash);
        token.setExpiresAt(now.plus(ttlSeconds, ChronoUnit.SECONDS));
        token.setSentAt(now);
        token.setAttempts(0);
        token.setConsumedAt(null);
        repo.save(token);

        // 4) Construir HTML EXACTO (no modificado) + texto plano
        String html  = buildHtml(email, code);
        String plain = buildPlain(email, code);

        // 5) Logo inline
        Resource logo = new ClassPathResource(LOGO_CLASSPATH);
        boolean useInlineLogo = !StringUtils.hasText(trim(verificationLogoUrl)) && logo.exists();

        // 6) Enviar correo (HTML + plain + imagen inline si existe)
        if (useInlineLogo) {
            mail.sendHtmlWithInlineImage(
                email,
                subject,
                html,
                plain,
                CID,
                logo,
                "image/png"
            );
        } else {
            mail.sendHtml(email, subject, html, plain);
        }
    }

    /** Envía OTP SOLO para activación de cuenta de estudiante del módulo de aprendizaje. */
    @Transactional
    public void sendStudentActivationEmailVerification(String rawEmail) {
        final String email = normalizeEmail(rawEmail);
        if (!estudianteService.existePorCorreo(email)) {
            throw new IllegalArgumentException("No existe un estudiante registrado con ese correo.");
        }

        final Instant now = Instant.now();

        repo.deleteByEmailAndPurposeAndExpiresAtBefore(email, "EMAIL_VERIFY", now);

        Optional<OtpToken> lastNotConsumed =
                repo.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc(email, "EMAIL_VERIFY");
        if (lastNotConsumed.isPresent()) {
            OtpToken last = lastNotConsumed.get();
            if (last.getSentAt() != null && now.isBefore(last.getSentAt().plusSeconds(cooldownSeconds))) {
                long wait = Math.max(1, ChronoUnit.SECONDS.between(now, last.getSentAt().plusSeconds(cooldownSeconds)));
                throw new IllegalStateException("Espera " + wait + "s para reenviar el código.");
            }
        }

        String code = generateNumericOtp(otpLength);
        String hash = OtpHasher.sha256(code);

        OtpToken token = new OtpToken();
        token.setEmail(email);
        token.setPurpose("EMAIL_VERIFY");
        token.setOtpHash(hash);
        token.setExpiresAt(now.plus(ttlSeconds, ChronoUnit.SECONDS));
        token.setSentAt(now);
        token.setAttempts(0);
        token.setConsumedAt(null);
        repo.save(token);

        String html = buildHtmlLearningActivation(email, code);
        String plain = buildPlainLearningActivation(email, code);

        Resource logo = new ClassPathResource(LOGO_CLASSPATH);
        boolean useInlineLogo = !StringUtils.hasText(trim(verificationLogoUrl)) && logo.exists();
        if (useInlineLogo) {
            mail.sendHtmlWithInlineImage(
                email,
                subject,
                html,
                plain,
                CID,
                logo,
                "image/png"
            );
        } else {
            mail.sendHtml(email, subject, html, plain);
        }
    }

    /** Verifica OTP: true si válido (marca consumido), false si inválido/expirado. */
    @Transactional
    public boolean verifyEmailOtp(String rawEmail, String rawCode) {
        final String email = normalizeEmail(rawEmail);
        final String code  = trim(rawCode);
        final Instant now  = Instant.now();

        // Buscar el último token no consumido
        Optional<OtpToken> lastOpt =
                repo.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc(email, "EMAIL_VERIFY");
        if (lastOpt.isEmpty()) return false;

        OtpToken token = lastOpt.get();

        // Expirado → invalidar
        if (token.getExpiresAt() == null || now.isAfter(token.getExpiresAt())) {
            token.setConsumedAt(now);
            repo.save(token);
            return false;
        }

        // ======= BLOQUE NULL-SAFE DE INTENTOS (esto reemplaza tu fragmento) =======
        int attempts = Optional.ofNullable(token.getAttempts())
                               .map(Number::intValue)   // Integer o Long → int
                               .orElse(0);

        if (attempts >= maxAttempts) {
            token.setConsumedAt(now);
            repo.save(token);
            return false;
        }
        // ==========================================================================

        // Incrementar y validar
        token.setAttempts(attempts + 1);

        boolean match = OtpHasher.sha256(code).equals(token.getOtpHash());
        if (match) {
            token.setConsumedAt(now);
            repo.save(token);
            return true;
        } else {
            if ((attempts + 1) >= maxAttempts) {
                token.setConsumedAt(now);
            }
            repo.save(token);
            return false;
        }
    }

    public record ContractLinkResult(
            String email,
            String code,
            String url,
            Instant expiresAt
    ) {}

    public record ContractAccessResult(
            boolean ok,
            String message,
            Instant expiresAt
    ) {}

    public record ContractCompletionResult(
            boolean ok,
            String message,
            String email,
            String documento,
            Long studentId,
            String flowStatus,
            String paymentStatus
    ) {}

    public record ContractUploadResult(
            boolean ok,
            String message,
            String email,
            String documento,
            String signerFolder,
            String objectKey,
            String fileName,
            String fileUrl
    ) {}

    @Transactional(readOnly = true)
    public Map<String, Object> buildContractAccessPayload(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        try {
            return chatbotProcesoService.buildContractAccessPayloadByEmail(email);
        } catch (Exception ex) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("email", email);
            return fallback;
        }
    }

    /** Genera código alfanumérico para firma y devuelve URL verificable. */
    @Transactional
    public ContractLinkResult createContractVerificationLink(String rawEmail, String rawBaseUrl) {
        final String email = normalizeEmail(rawEmail);
        final Instant now = Instant.now();
        final String purpose = "CONTRACT_SIGN";

        // Limpiar expirados del mismo flujo
        repo.deleteByEmailAndPurposeAndExpiresAtBefore(email, purpose, now);

        String code = generateAlphaNumericCode(contractCodeLength);
        OtpToken token = new OtpToken();
        token.setEmail(email);
        token.setPurpose(purpose);
        token.setOtpHash(OtpHasher.sha256(code));
        token.setExpiresAt(now.plus(contractTtlSeconds, ChronoUnit.SECONDS));
        token.setSentAt(now);
        token.setAttempts(0);
        token.setConsumedAt(null);
        repo.save(token);

        String base = trim(rawBaseUrl);
        if (base.isBlank()) {
            base = trim(contractVerifyBaseUrl);
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        String path = trim(contractVerifyPath);
        if (path.isBlank()) {
            path = "/api/verification/contract/verify";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        String url = base + path
                + "?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);

        return new ContractLinkResult(email, code, url, token.getExpiresAt());
    }

    /** Valida y consume código de firma de contrato. */
    @Transactional
    public boolean verifyContractCode(String rawEmail, String rawCode) {
        final String email = normalizeEmail(rawEmail);
        final String code = trim(rawCode);
        final Instant now = Instant.now();
        final String purpose = "CONTRACT_SIGN";
        final String codeHash = OtpHasher.sha256(code);

        Optional<OtpToken> matchingOpt =
                repo.findTopByEmailAndPurposeAndOtpHashAndConsumedAtIsNullOrderByIdDesc(email, purpose, codeHash);
        Optional<OtpToken> lastOpt = matchingOpt.isPresent()
                ? matchingOpt
                : repo.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc(email, purpose);
        if (lastOpt.isEmpty()) return false;

        OtpToken token = lastOpt.get();
        if (token.getExpiresAt() == null || now.isAfter(token.getExpiresAt())) {
            token.setConsumedAt(now);
            repo.save(token);
            return false;
        }

        int attempts = Optional.ofNullable(token.getAttempts())
                .map(Number::intValue)
                .orElse(0);

        if (attempts >= maxAttempts) {
            token.setConsumedAt(now);
            repo.save(token);
            return false;
        }

        token.setAttempts(attempts + 1);
        boolean ok = codeHash.equals(token.getOtpHash());
        if (ok || (attempts + 1) >= maxAttempts) {
            token.setConsumedAt(now); // one-time use
        }
        repo.save(token);
        return ok;
    }

    /** Valida código de acceso a contratos sin consumirlo. */
    @Transactional
    public ContractAccessResult validateContractAccessCode(String rawEmail, String rawCode) {
        final String email = normalizeEmail(rawEmail);
        final String code = trim(rawCode);
        final Instant now = Instant.now();
        final String purpose = "CONTRACT_SIGN";

        if (code.isBlank()) {
            return new ContractAccessResult(false, "Codigo requerido", null);
        }

        final String codeHash = OtpHasher.sha256(code);
        Optional<OtpToken> matchingOpt =
                repo.findTopByEmailAndPurposeAndOtpHashAndConsumedAtIsNullOrderByIdDesc(email, purpose, codeHash);
        Optional<OtpToken> lastOpt = matchingOpt.isPresent()
                ? matchingOpt
                : repo.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc(email, purpose);
        if (lastOpt.isEmpty()) {
            return new ContractAccessResult(false, "Codigo invalido o vencido", null);
        }

        OtpToken token = lastOpt.get();
        if (token.getExpiresAt() == null || now.isAfter(token.getExpiresAt())) {
            token.setConsumedAt(now);
            repo.save(token);
            return new ContractAccessResult(false, "Codigo vencido", null);
        }

        int attempts = Optional.ofNullable(token.getAttempts())
                .map(Number::intValue)
                .orElse(0);
        if (attempts >= maxAttempts) {
            token.setConsumedAt(now);
            repo.save(token);
            return new ContractAccessResult(false, "Codigo invalido por maximo de intentos", null);
        }

        boolean ok = codeHash.equals(token.getOtpHash());
        if (!ok) {
            token.setAttempts(attempts + 1);
            if ((attempts + 1) >= maxAttempts) {
                token.setConsumedAt(now);
            }
            repo.save(token);
            return new ContractAccessResult(false, "Codigo invalido o vencido", null);
        }

        return new ContractAccessResult(true, "Codigo valido", token.getExpiresAt());
    }

    /** Consume código de contrato y activa matrícula (creación de estudiante) si aplica. */
    public ContractCompletionResult completeContractSigning(String rawEmail, String rawCode) {
        final String email = normalizeEmail(rawEmail);
        final boolean verified;
        try {
            verified = verifyContractCode(email, rawCode);
        } catch (Exception ex) {
            log.error("Error validando codigo de contrato email={}: {}", email, ex.getMessage(), ex);
            return new ContractCompletionResult(
                    false,
                    "No se pudo validar el contrato en este momento. Intenta nuevamente.",
                    email,
                    "",
                    null,
                    "",
                    ""
            );
        }
        if (!verified) {
            return new ContractCompletionResult(
                    false,
                    "Codigo invalido o vencido",
                    email,
                    "",
                    null,
                    "",
                    ""
            );
        }

        // Best-effort: actualiza el proceso del chatbot sin afectar el consumo del OTP.
        try {
            chatbotProcesoService.markContractSignedByEmail(email);
        } catch (Exception ex) {
            log.warn("No se pudo marcar contrato firmado por email={}: {}", email, ex.getMessage());
        }

        Optional<ChatbotMatriculaProceso> procesoOpt = chatbotProcesoService.findLatestProcesoByEmail(email);
        if (procesoOpt.isEmpty()) {
            return new ContractCompletionResult(
                    true,
                    "Contrato validado correctamente",
                    email,
                    "",
                    null,
                    "CONTRACT_SIGNED",
                    ""
            );
        }

        ChatbotMatriculaProceso proceso = procesoOpt.get();
        String documento = trim(proceso.getNumeroDocumento());
        Long studentId = null;
        String message = "Contrato validado correctamente";

        if (StringUtils.hasText(documento)) {
            try {
                studentId = chatbotProcesoService
                        .tryFinalizeEnrollmentIfReadyByDocumento(documento)
                        .orElse(null);
                if (studentId != null) {
                    message = "Contrato validado, estudiante, estado de cuenta y pago actualizados";
                } else {
                    // Pago aun pendiente o no se cumplen precondiciones: no debe romper el flujo.
                    message = "Contrato validado. Pago pendiente o en proceso de verificacion.";
                }
            } catch (Exception ex) {
                log.error("No se pudo finalizar matricula doc={} email={}: {}", documento, email, ex.getMessage(), ex);
                message = "Contrato validado, pero no se pudo activar matricula en este momento.";
            }
        }

        ChatbotMatriculaProceso updated = StringUtils.hasText(documento)
                ? chatbotProcesoService.findProcesoByDocumento(documento).orElse(proceso)
                : proceso;

        notifyContractCompletionByWhatsApp(updated, studentId);

        return new ContractCompletionResult(
                true,
                message,
                email,
                documento,
                studentId,
                safe(updated.getFlowStatus()),
                safe(updated.getPaymentStatus())
        );
    }

    public ContractUploadResult uploadSignedContractDocument(String rawEmail,
                                                             String rawCode,
                                                             String rawSignerName,
                                                             String rawContractName,
                                                             String rawPdfFile,
                                                             String rawFormDataJson,
                                                             MultipartFile file) {
        final String email = normalizeEmail(rawEmail);
        ContractAccessResult access = validateContractAccessCode(email, rawCode);
        if (!access.ok()) {
            return new ContractUploadResult(
                    false,
                    access.message(),
                    email,
                    "",
                    "",
                    "",
                    "",
                    ""
            );
        }

        Optional<ChatbotMatriculaProceso> procesoOpt = chatbotProcesoService.findLatestProcesoByEmail(email);
        String documento = procesoOpt.map(ChatbotMatriculaProceso::getNumeroDocumento).map(this::safe).orElse("");
        String signerName = trim(rawSignerName);
        if (signerName.isBlank()) {
            signerName = procesoOpt.map(ChatbotMatriculaProceso::getNombreCompleto).map(this::safe).orElse("");
        }
        if (signerName.isBlank()) {
            signerName = email.split("@")[0];
        }

        String contractName = trim(rawContractName);
        if (contractName.isBlank()) {
            contractName = "contrato-firmado";
        }

        ContractDocumentStorageService.StoredDocument stored =
                contractDocumentStorageService.storeSignedContract(file, signerName, documento, contractName);

        try {
            chatbotProcesoService.mergeContractSubmissionByEmail(
                    email,
                    contractName,
                    trim(rawPdfFile),
                    trim(rawFormDataJson),
                    stored.fileName(),
                    stored.publicUrl(),
                    stored.objectKey(),
                    stored.signerFolder()
            );
        } catch (Exception ex) {
            // El archivo ya fue almacenado; no queremos fallar toda la respuesta por un error de persistencia.
            log.error("Contrato subido a almacenamiento pero no se pudo persistir metadata/form. email={} doc={} fileName={}: {}",
                    email, documento, safe(stored.fileName()), ex.getMessage(), ex);
        }

        return new ContractUploadResult(
                true,
                "Contrato cargado correctamente",
                email,
                documento,
                safe(stored.signerFolder()),
                safe(stored.objectKey()),
                safe(stored.fileName()),
                safe(stored.publicUrl())
        );
    }

    /* =========================================================
       Helpers
       ========================================================= */

    private String generateNumericOtp(int length) {
        int mod = (int) Math.pow(10, Math.max(4, length));
        int code = rng.nextInt(mod);
        return String.format("%0" + Math.max(4, length) + "d", code);
    }

    private String generateAlphaNumericCode(int length) {
        final char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
        StringBuilder sb = new StringBuilder(Math.max(8, length));
        for (int i = 0; i < Math.max(8, length); i++) {
            sb.append(alphabet[rng.nextInt(alphabet.length)]);
        }
        return sb.toString();
    }

    /** NORMALIZA y VALIDA el email: quita comillas externas, invisibles y valida con InternetAddress estricto. */
    private String normalizeEmail(String emailRaw) {
        if (emailRaw == null) throw new IllegalArgumentException("email requerido");

        // 1) Quitar comillas externas si vienen del JSON: "\"user@dom.com\""
        String e = stripOuterQuotes(emailRaw);

        // 2) Eliminar caracteres invisibles comunes y NBSP→espacio normal
        e = e.replace("\uFEFF", "")   // BOM
             .replace("\u200B", "")   // ZERO WIDTH SPACE
             .replace("\u200C", "")   // ZERO WIDTH NON-JOINER
             .replace("\u200D", "")   // ZERO WIDTH JOINER
             .replace("\u00A0", " "); // NBSP

        // 3) Trim + lowercase
        e = e.trim().toLowerCase(Locale.ROOT);
        if (e.isEmpty()) throw new IllegalArgumentException("email requerido");

        // 4) Validación robusta
        try {
            InternetAddress addr = new InternetAddress(e, true);
            addr.validate();
        } catch (Exception ex) {
            throw new IllegalArgumentException("send.email: debe ser una dirección de correo electrónico con formato correcto");
        }

        // 5) Longitud defensiva
        if (e.length() > 254) {
            throw new IllegalArgumentException("send.email: longitud inválida");
        }
        return e;
    }

    private static String stripOuterQuotes(String s) {
        String x = (s == null ? "" : s).trim();
        if (x.length() >= 2) {
            char f = x.charAt(0), l = x.charAt(x.length() - 1);
            if ((f == '"' && l == '"') || (f == '\'' && l == '\'')) {
                return x.substring(1, x.length() - 1);
            }
        }
        return x;
    }

    private void notifyContractCompletionByWhatsApp(ChatbotMatriculaProceso proceso, Long studentId) {
        if (!autoSendEnrollmentWhatsapp) return;
        if (proceso == null || studentId == null) return;
        if (!waService.getConfigStatus().ready()) return;

        String phone = trim(proceso.getPhone());
        if (!StringUtils.hasText(phone)) {
            phone = trim(proceso.getTelefono());
        }
        if (!StringUtils.hasText(phone)) return;

        try {
            waService.sendTextMessage(
                    phone,
                    "✅ Contrato validado y matricula activada.\n\n" +
                            "📌 Categoria: " + safe(proceso.getCategoria()) + "\n" +
                            "🆔 Documento: " + safe(proceso.getNumeroDocumento()) + "\n" +
                            "🧾 Ref estudiante: " + studentId + "\n\n" +
                            "Tu estado de cuenta y pago quedaron registrados.\n\n" +
                            "Si deseas consultar o agendar clases, escribe MENU y luego 'Soy estudiante'."
            );
        } catch (Exception ignored) {
            // La activacion no debe fallar por un error de notificacion.
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String buildHtml(String email, String code) {
        // HTML EXACTO (no modificado), con placeholders %s (código) y %d (minutos)
        String html = """
<div style="background-color:#f4f4f4;padding:24px;font-family:'Segoe UI',Arial,sans-serif;color:#1a1a1a;">
  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:480px;margin:0 auto;border-collapse:collapse;">
    
    <!-- ENCABEZADO CON LOGO -->
    <tr>
      <td style="text-align:center;background:#ffffff;border-radius:8px 8px 0 0;padding:0px 24px;">
        <img src="%s"
             alt="CEA HARO"
             style="max-width:140px;height:auto;display:inline-block;border:0;outline:none;text-decoration:none;background:#ffffff;padding:8px 12px;border-radius:6px;">
      </td>
    </tr>

    <!-- CUERPO TARJETA -->
    <tr>
      <td style="background-color:#ffffff;border-radius:0 0 8px 8px;box-shadow:0 4px 12px rgba(0,0,0,0.07);padding:24px;border-top:4px solid #ffcc00;">
        
        <h2 style="margin:0 0 12px 0;font-size:18px;font-weight:600;color:#111827;">
          Verificación de correo electrónico
        </h2>

        <p style="margin:0 0 16px 0;font-size:14px;line-height:1.5;color:#374151;">
          Hola,
          <br><br>
          Estamos confirmando que este correo electrónico te pertenece para continuar con tu proceso en
          <strong style="color:#d00000;">CEA HARO</strong>.
        </p>

        <p style="margin:0 0 8px 0;font-size:14px;line-height:1.5;color:#374151;">
          Tu código de verificación es:
        </p>

        <div style="font-size:26px;font-weight:700;letter-spacing:3px;
                    text-align:center;color:#d00000;
                    background-color:#ffffff;
                    border:2px solid #d00000;
                    border-radius:8px;
                    padding:12px 16px;
                    margin:0 0 16px 0;
                    box-shadow:0 2px 6px rgba(0,0,0,0.08);">
          %s
        </div>

        <p style="margin:0 0 16px 0;font-size:13px;line-height:1.5;color:#6b7280;">
          Este código vence en <strong>%d minutos</strong>. Por seguridad, no lo compartas con nadie.
        </p>

        <p style="margin:0 0 16px 0;font-size:12px;line-height:1.5;color:#6b7280;">
          Si no solicitaste esta verificación, puedes ignorar este mensaje. Tu cuenta no se verá afectada.
        </p>

        <hr style="border:none;border-top:1px solid #e5e7eb;margin:16px 0;">

        <p style="margin:0;font-size:12px;line-height:1.4;color:#6b7280;">
          Atentamente,<br>
          <strong style="color:#d00000;">CEA HARO</strong><br>
          Centro de Enseñanza Automovilística<br>
          Tel: (322) 329  2939
        </p>

        <div style="margin-top:16px;font-size:11px;line-height:1.4;color:#9ca3af;text-align:center;border-left:4px solid #ffcc00;padding-left:8px;">
          Este es un mensaje automático, por favor no respondas a este correo.
        </div>

      </td>
    </tr>

  </table>
</div>
        """.formatted(resolveVerificationLogoSrc(), code, ttlSeconds / 60);
        return html;
    }

    private String buildPlain(String email, String code) {
        long mins = Math.max(1, ttlSeconds / 60);
        return "CEA HARO - Verificación de correo\n\n"
            + "Tu código de verificación es: " + code + "\n\n"
            + "El código vence en " + mins + " minutos. Por seguridad, no lo compartas con nadie.\n\n"
            + "Si no solicitaste esta verificación, ignora este mensaje. Tu cuenta no se verá afectada.";
    }

    private String buildHtmlLearningActivation(String email, String code) {
        String html = """
<div style="background-color:#f4f4f4;padding:24px;font-family:'Segoe UI',Arial,sans-serif;color:#1a1a1a;">
  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:480px;margin:0 auto;border-collapse:collapse;">
    
    <!-- ENCABEZADO CON LOGO -->
    <tr>
      <td style="text-align:center;background:#ffffff;border-radius:8px 8px 0 0;padding:0px 24px;">
        <img src="%s"
             alt="CEA HARO"
             style="max-width:140px;height:auto;display:inline-block;border:0;outline:none;text-decoration:none;background:#ffffff;padding:8px 12px;border-radius:6px;">
      </td>
    </tr>

    <!-- CUERPO TARJETA -->
    <tr>
      <td style="background-color:#ffffff;border-radius:0 0 8px 8px;box-shadow:0 4px 12px rgba(0,0,0,0.07);padding:24px;border-top:4px solid #ffcc00;">
        
        <h2 style="margin:0 0 12px 0;font-size:18px;font-weight:600;color:#111827;">
          Activacion del modulo de gestion de aprendizaje
        </h2>

        <p style="margin:0 0 16px 0;font-size:14px;line-height:1.5;color:#374151;">
          Hola,
          <br><br>
          Este correo y su codigo OTP se envian unicamente para activar el
          <strong style="color:#d00000;">modulo de gestion de aprendizaje</strong>.
        </p>

        <p style="margin:0 0 8px 0;font-size:14px;line-height:1.5;color:#374151;">
          Tu codigo de verificacion es:
        </p>

        <div style="font-size:26px;font-weight:700;letter-spacing:3px;
                    text-align:center;color:#d00000;
                    background-color:#ffffff;
                    border:2px solid #d00000;
                    border-radius:8px;
                    padding:12px 16px;
                    margin:0 0 16px 0;
                    box-shadow:0 2px 6px rgba(0,0,0,0.08);">
          %s
        </div>

        <p style="margin:0 0 16px 0;font-size:13px;line-height:1.5;color:#6b7280;">
          Este codigo vence en <strong>%d minutos</strong>. Por seguridad, no lo compartas con nadie.
        </p>

        <p style="margin:0 0 16px 0;font-size:12px;line-height:1.5;color:#6b7280;">
          Si no solicitaste esta activacion, puedes ignorar este mensaje. Tu cuenta no se vera afectada.
        </p>

        <hr style="border:none;border-top:1px solid #e5e7eb;margin:16px 0;">

        <p style="margin:0;font-size:12px;line-height:1.4;color:#6b7280;">
          Atentamente,<br>
          <strong style="color:#d00000;">CEA HARO</strong><br>
          Centro de Ensenanza Automovilistica<br>
          Tel: (322) 329  2939
        </p>

        <div style="margin-top:16px;font-size:11px;line-height:1.4;color:#9ca3af;text-align:center;border-left:4px solid #ffcc00;padding-left:8px;">
          Este es un mensaje automatico, por favor no respondas a este correo.
        </div>

      </td>
    </tr>

  </table>
</div>
        """.formatted(resolveVerificationLogoSrc(), code, ttlSeconds / 60);
        return html;
    }

    private String buildPlainLearningActivation(String email, String code) {
        long mins = Math.max(1, ttlSeconds / 60);
        return "CEA HARO - Activacion modulo de gestion de aprendizaje\n\n"
            + "Este codigo OTP se envia unicamente para activar el modulo de gestion de aprendizaje.\n\n"
            + "Tu codigo de verificacion es: " + code + "\n\n"
            + "El codigo vence en " + mins + " minutos. Por seguridad, no lo compartas con nadie.\n\n"
            + "Si no solicitaste esta activacion, ignora este mensaje. Tu cuenta no se vera afectada.";
    }

    private String resolveVerificationLogoSrc() {
        String logoUrl = trim(verificationLogoUrl);
        if (StringUtils.hasText(logoUrl)) {
            return logoUrl.replace("\"", "%22");
        }
        return "cid:" + CID;
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
}
