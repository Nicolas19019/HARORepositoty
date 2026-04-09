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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.util.StringUtils;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private static final String[] REQUIRED_SIGNED_CONTRACTS = {
            "Contrato1.pdf",
            "Contrato2.pdf",
            "Contrato3.pdf",
            "Contrato4.pdf"
    };

    private final OtpTokenRepository repo;
    private final MailService mail;
    private final EstudianteService estudianteService;
    private final ChatbotProcesoService chatbotProcesoService;
    private final WhatsAppTemplateService waService;
    private final ContractDocumentStorageService contractDocumentStorageService;
    private final ContractEnrollmentFinalizeService contractEnrollmentFinalizeService;
    private final ObjectProvider<VerificationService> selfProvider;
    private final PlatformTransactionManager txManager;
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

    @Value("${app.contract.verify.ttlSeconds:7200}") // default: 2h
    private long contractTtlSeconds;

    @Value("${app.contract.verify.base-url:http://localhost:8081}")
    private String contractVerifyBaseUrl;

    @Value("${app.contract.verify.path:/api/verification/contract/verify}")
    private String contractVerifyPath;

    // En el flujo de contratos, se espera notificar al usuario al finalizar.
    @Value("${chatbot.auto-send-enrollment-whatsapp:true}")
    private boolean autoSendEnrollmentWhatsapp;

    @Value("${app.internal-api-base-url:}")
    private String internalApiBaseUrl;

    @Value("${chatbot.theory.whatsapp-group-link:}")
    private String theoryWhatsappGroupLink;

    private final RestTemplate restTemplate;

    public VerificationService(OtpTokenRepository repo,
                               MailService mail,
                               EstudianteService estudianteService,
                               ChatbotProcesoService chatbotProcesoService,
                               WhatsAppTemplateService waService,
                               ContractDocumentStorageService contractDocumentStorageService,
                               ContractEnrollmentFinalizeService contractEnrollmentFinalizeService,
                               ObjectProvider<VerificationService> selfProvider,
                               PlatformTransactionManager txManager,
                               RestTemplate restTemplate) {
        this.repo = repo;
        this.mail = mail;
        this.estudianteService = estudianteService;
        this.chatbotProcesoService = chatbotProcesoService;
        this.waService = waService;
        this.contractDocumentStorageService = contractDocumentStorageService;
        this.contractEnrollmentFinalizeService = contractEnrollmentFinalizeService;
        this.selfProvider = selfProvider;
        this.txManager = txManager;
        this.restTemplate = restTemplate;
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
        long t0 = System.nanoTime();
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

        long ms = Math.max(0, (System.nanoTime() - t0) / 1_000_000);
        log.info("OTP email send flow done email={} elapsedMs={}", email, ms);
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
            String fileUrl,
            boolean contractCompleted,
            Long studentId,
            String flowStatus,
            String paymentStatus
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

        // Solo puede existir 1 link activo por email: si se genera uno nuevo, el anterior debe dejar de servir.
        // Esto evita que queden varios links validos circulando al reenviar el correo/WhatsApp.
        repo.deleteByEmailAndPurposeAndConsumedAtIsNull(email, purpose);

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

    /**
     * Verifica si un codigo de contrato sigue vigente SIN mutar el token (no consume ni incrementa intentos).
     * Se usa para decidir si se debe regenerar un link antes de enviarlo por WhatsApp/chatbot.
     */
    @Transactional(readOnly = true)
    public ContractAccessResult peekContractAccessCode(String rawEmail, String rawCode) {
        final String email = normalizeEmail(rawEmail);
        final String code = trim(rawCode);
        final Instant now = Instant.now();
        final String purpose = "CONTRACT_SIGN";

        if (code.isBlank()) {
            return new ContractAccessResult(false, "Codigo requerido", null);
        }

        final String codeHash = OtpHasher.sha256(code);
        Optional<OtpToken> tokenOpt =
                repo.findTopByEmailAndPurposeAndOtpHashAndConsumedAtIsNullOrderByIdDesc(email, purpose, codeHash);
        if (tokenOpt.isEmpty()) {
            return new ContractAccessResult(false, "Codigo invalido o vencido", null);
        }

        OtpToken token = tokenOpt.get();
        Instant expiresAt = token.getExpiresAt();
        if (expiresAt == null || now.isAfter(expiresAt)) {
            return new ContractAccessResult(false, "Codigo vencido", expiresAt);
        }

        return new ContractAccessResult(true, "Codigo valido", expiresAt);
    }

    /** Consume código de contrato y activa matrícula (creación de estudiante) si aplica. */
    // Keep this method non-transactional so a caught exception cannot poison the request with rollback-only.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ContractCompletionResult completeContractSigning(String rawEmail, String rawCode) {
    final String email = normalizeEmail(rawEmail);
    final boolean verified;

    Optional<ChatbotMatriculaProceso> procesoOpt = chatbotProcesoService.findLatestProcesoByEmail(email);
    if (procesoOpt.isPresent()) {
        ChatbotMatriculaProceso procesoBeforeVerification = procesoOpt.get();
        try {
            ChatbotProcesoService.ContractCategoryFlowSnapshot contractFlow = chatbotProcesoService.getContractCategoryFlowByEmail(email);
            if (!contractFlow.allCompleted()) {
                return new ContractCompletionResult(
                        false,
                        "Aun faltan contratos por firmar antes de finalizar el proceso.",
                        email,
                        trim(procesoBeforeVerification.getNumeroDocumento()),
                        null,
                        safe(procesoBeforeVerification.getFlowStatus()),
                        safe(procesoBeforeVerification.getPaymentStatus())
                );
            }
        } catch (Exception ex) {
            log.warn("No se pudo validar flujo por categorias antes de consumir el codigo email={}: {}", email, ex.getMessage());
            return new ContractCompletionResult(
                    false,
                    "No se pudo validar el estado de contratos por categoria. Intenta nuevamente.",
                    email,
                    trim(procesoBeforeVerification.getNumeroDocumento()),
                    null,
                    safe(procesoBeforeVerification.getFlowStatus()),
                    safe(procesoBeforeVerification.getPaymentStatus())
            );
        }
    }

    // Marker to confirm the deployed revision is running the non-transactional flow.
    // If you still see UnexpectedRollbackException on commit, this marker likely won't appear (old revision).
    log.info("[contract.complete] v2026-03-19 nonTx txActive={}", TransactionSynchronizationManager.isActualTransactionActive());

    try {
        // Self-invocation bypasses Spring AOP; call through proxy so @Transactional is applied.
        verified = selfProvider.getObject().verifyContractCode(email, rawCode);
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
        // Idempotency: if the contract is already signed, allow re-running completion even if the OTP was consumed.
        Optional<ChatbotMatriculaProceso> signedOpt = chatbotProcesoService.findLatestProcesoByEmail(email);
        boolean alreadySigned = signedOpt.isPresent()
                && "SIGNED".equalsIgnoreCase(trim(signedOpt.get().getContractStatus()));
        if (!alreadySigned) {
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
        log.info("[contract.complete] codigo ya consumido pero contrato ya esta SIGNED. email={}", email);
    }

    procesoOpt = chatbotProcesoService.findLatestProcesoByEmail(email);
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
    try {
        chatbotProcesoService.markContractSignedByEmail(email);
    } catch (Exception ex) {
        log.warn("No se pudo marcar contrato firmado por email={}: {}", email, ex.getMessage(), ex);
    }
    String documento = trim(proceso.getNumeroDocumento());
    Long studentId = null;
    String message = "Contrato validado correctamente";
    boolean okResult = true;

    if (StringUtils.hasText(documento)) {
        try {
            // Force creation of student + estado de cuenta + pago as a hard requirement after signing.
            studentId = contractEnrollmentFinalizeService.forceFinalizeEnrollment(documento);
            if (studentId != null) {
                message = "Contrato validado, estudiante, estado de cuenta y pago actualizados";
            } else {
                okResult = false;
                message = "Contrato validado, pero no fue posible crear el estudiante/estado/pago en este momento.";
            }
        } catch (Exception ex) {
            log.error("No se pudo finalizar matricula doc={} email={}: {}", documento, email, ex.getMessage(), ex);
            okResult = false;
            message = "Fallo creando estudiante/estado/pago: " + safe(ex.getMessage());
        }
    }

    ChatbotMatriculaProceso updated = StringUtils.hasText(documento)
            ? chatbotProcesoService.findProcesoByDocumento(documento).orElse(proceso)
            : proceso;

    return new ContractCompletionResult(
            okResult,
            message,
            email,
            documento,
            studentId,
            safe(updated.getFlowStatus()),
            safe(updated.getPaymentStatus())
    );
}

    /**
     * Recovery path for cases where an old transactional flow throws UnexpectedRollbackException at commit time.
     * This method is non-transactional and uses inner REQUIRES_NEW / transactional methods for each step.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ContractCompletionResult completeContractSigningRecovery(String rawEmail, String rawCode) {
        final String email = normalizeEmail(rawEmail);
        log.warn("[contract.complete.recover] email={}", email);

        Optional<ChatbotMatriculaProceso> procesoOpt = chatbotProcesoService.findLatestProcesoByEmail(email);
        if (procesoOpt.isPresent()) {
            ChatbotMatriculaProceso procesoBeforeVerification = procesoOpt.get();
            try {
                ChatbotProcesoService.ContractCategoryFlowSnapshot contractFlow = chatbotProcesoService.getContractCategoryFlowByEmail(email);
                if (!contractFlow.allCompleted()) {
                    return new ContractCompletionResult(
                            false,
                            "Aun faltan contratos por firmar antes de finalizar el proceso.",
                            email,
                            trim(procesoBeforeVerification.getNumeroDocumento()),
                            null,
                            safe(procesoBeforeVerification.getFlowStatus()),
                            safe(procesoBeforeVerification.getPaymentStatus())
                    );
                }
            } catch (Exception ex) {
                log.warn("[contract.complete.recover] No se pudo validar flujo por categorias antes de consumir el codigo email={}: {}", email, ex.getMessage());
                return new ContractCompletionResult(
                        false,
                        "No se pudo validar el estado de contratos por categoria. Intenta nuevamente.",
                        email,
                        trim(procesoBeforeVerification.getNumeroDocumento()),
                        null,
                        safe(procesoBeforeVerification.getFlowStatus()),
                        safe(procesoBeforeVerification.getPaymentStatus())
                );
            }
        }

        boolean verified = false;
        try {
            verified = selfProvider.getObject().verifyContractCode(email, rawCode);
        } catch (Exception ex) {
            log.error("[contract.complete.recover] Error verificando codigo email={}: {}", email, ex.getMessage(), ex);
        }

        procesoOpt = chatbotProcesoService.findLatestProcesoByEmail(email);
        if (procesoOpt.isEmpty()) {
            return new ContractCompletionResult(
                    verified,
                    verified ? "Contrato validado correctamente" : "Codigo invalido o vencido",
                    email,
                    "",
                    null,
                    verified ? "CONTRACT_SIGNED" : "",
                    ""
            );
        }

        ChatbotMatriculaProceso proceso = procesoOpt.get();
        String documento = trim(proceso.getNumeroDocumento());

        // If code verification failed but the process is already signed, we can continue idempotently.
        boolean alreadySigned = "SIGNED".equalsIgnoreCase(trim(proceso.getContractStatus()));
        if (!verified && !alreadySigned) {
            return new ContractCompletionResult(
                    false,
                    "Codigo invalido o vencido",
                    email,
                    documento,
                    null,
                    safe(proceso.getFlowStatus()),
                    safe(proceso.getPaymentStatus())
            );
        }

        Long studentId = null;
        String message = "Contrato validado correctamente";
        boolean okResult = true;
        try {
            chatbotProcesoService.markContractSignedByEmail(email);
        } catch (Exception ex) {
            log.warn("[contract.complete.recover] No se pudo marcar contrato firmado email={}: {}", email, ex.getMessage(), ex);
        }

        if (StringUtils.hasText(documento)) {
            try {
                studentId = contractEnrollmentFinalizeService.forceFinalizeEnrollment(documento);
                if (studentId != null) {
                    message = "Contrato validado, estudiante, estado de cuenta y pago actualizados";
                } else {
                    okResult = false;
                    message = "Contrato validado, pero no fue posible crear el estudiante/estado/pago en este momento.";
                }
            } catch (Exception ex) {
                log.error("[contract.complete.recover] No se pudo forzar matricula doc={} email={}: {}", documento, email, ex.getMessage(), ex);
                okResult = false;
                message = "Fallo creando estudiante/estado/pago: " + safe(ex.getMessage());
            }
        }

        ChatbotMatriculaProceso updated = StringUtils.hasText(documento)
                ? chatbotProcesoService.findProcesoByDocumento(documento).orElse(proceso)
                : proceso;

        return new ContractCompletionResult(
                okResult,
                message,
                email,
                documento,
                studentId,
                safe(updated.getFlowStatus()),
                safe(updated.getPaymentStatus())
        );
    }

    private TransactionTemplate newTx(int propagationBehavior) {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.setPropagationBehavior(propagationBehavior);
        return tt;
    }

    /**
     * Root implementation for contract completion. No outer @Transactional.
     * Each step runs in its own transaction so rollback-only cannot crash the whole request.
     */
    public ContractCompletionResult completeContractSigningV2(String rawEmail, String rawCode) {
        final String email = normalizeEmail(rawEmail);
        log.info("[contract.complete.v2] email={} outerTxActive={}", email, TransactionSynchronizationManager.isActualTransactionActive());

        Optional<ChatbotMatriculaProceso> procesoOpt = chatbotProcesoService.findLatestProcesoByEmail(email);
        if (procesoOpt.isPresent()) {
            ChatbotMatriculaProceso procesoBeforeVerification = procesoOpt.get();
            try {
                ChatbotProcesoService.ContractCategoryFlowSnapshot contractFlow = chatbotProcesoService.getContractCategoryFlowByEmail(email);
                if (!contractFlow.allCompleted()) {
                    return new ContractCompletionResult(
                            false,
                            "Aun faltan contratos por firmar antes de finalizar el proceso.",
                            email,
                            trim(procesoBeforeVerification.getNumeroDocumento()),
                            null,
                            safe(procesoBeforeVerification.getFlowStatus()),
                            safe(procesoBeforeVerification.getPaymentStatus())
                    );
                }
            } catch (Exception ex) {
                log.warn("[contract.complete.v2] No se pudo validar flujo por categorias antes de consumir el codigo email={}: {}", email, ex.getMessage());
                return new ContractCompletionResult(
                        false,
                        "No se pudo validar el estado de contratos por categoria. Intenta nuevamente.",
                        email,
                        trim(procesoBeforeVerification.getNumeroDocumento()),
                        null,
                        safe(procesoBeforeVerification.getFlowStatus()),
                        safe(procesoBeforeVerification.getPaymentStatus())
                );
            }
        }

        Boolean verified = newTx(TransactionDefinition.PROPAGATION_REQUIRES_NEW).execute(status -> {
            try {
                return selfProvider.getObject().verifyContractCode(email, rawCode);
            } catch (Exception ex) {
                log.error("[contract.complete.v2] Error verificando codigo email={}: {}", email, ex.getMessage(), ex);
                return false;
            }
        });

        if (verified == null || !verified) {
            // Idempotency: if already signed, proceed even if OTP was consumed.
            procesoOpt = chatbotProcesoService.findLatestProcesoByEmail(email);
            boolean alreadySigned = procesoOpt.isPresent()
                    && "SIGNED".equalsIgnoreCase(trim(procesoOpt.get().getContractStatus()));
            if (!alreadySigned) {
                return new ContractCompletionResult(false, "Codigo invalido o vencido", email, "", null, "", "");
            }
            log.info("[contract.complete.v2] codigo ya consumido pero contrato ya esta SIGNED. email={}", email);
        }

        procesoOpt = chatbotProcesoService.findLatestProcesoByEmail(email);
        if (procesoOpt.isEmpty()) {
            return new ContractCompletionResult(true, "Contrato validado correctamente", email, "", null, "CONTRACT_SIGNED", "");
        }

        ChatbotMatriculaProceso proceso = procesoOpt.get();
        newTx(TransactionDefinition.PROPAGATION_REQUIRES_NEW).execute(status -> {
            try {
                chatbotProcesoService.markContractSignedByEmail(email);
            } catch (Exception ex) {
                log.warn("[contract.complete.v2] No se pudo marcar contrato firmado email={}: {}", email, ex.getMessage(), ex);
            }
            return null;
        });
        String documento = trim(proceso.getNumeroDocumento());
        Long studentId = null;
        String message = "Contrato validado correctamente";
        boolean okResult = true;

        if (StringUtils.hasText(documento)) {
            try {
                studentId = contractEnrollmentFinalizeService.forceFinalizeEnrollment(documento);
                if (studentId != null) {
                    message = "Contrato validado, estudiante, estado de cuenta y pago actualizados";
                } else {
                    okResult = false;
                    message = "Contrato validado, pero no fue posible crear el estudiante/estado/pago en este momento.";
                }
            } catch (Exception ex) {
                log.error("[contract.complete.v2] Fallo forzando matricula doc={} email={}: {}", documento, email, ex.getMessage(), ex);
                okResult = false;
                message = "Fallo creando estudiante/estado/pago: " + safe(ex.getMessage());
            }
        }

        ChatbotMatriculaProceso updated = StringUtils.hasText(documento)
                ? chatbotProcesoService.findProcesoByDocumento(documento).orElse(proceso)
                : proceso;

        return new ContractCompletionResult(
                okResult,
                message,
                email,
                documento,
                studentId,
                safe(updated.getFlowStatus()),
                safe(updated.getPaymentStatus())
        );
    }

    public boolean notifyContractCompletionAfterCommit(String email, Long studentId) {
        if (!autoSendEnrollmentWhatsapp) return false;
        if (studentId == null) return false;

        try {
            Optional<ChatbotMatriculaProceso> procesoOpt = chatbotProcesoService.findLatestProcesoByEmail(email);
            if (procesoOpt.isEmpty()) return false;
            return notifyContractCompletionByWhatsApp(procesoOpt.get(), studentId);
        } catch (Exception ex) {
            log.warn("La matricula se activo, pero fallo la notificacion de WhatsApp para email={}: {}", email, ex.getMessage());
            return false;
        }
    }

    public ContractUploadResult uploadSignedContractDocument(String rawEmail,
                                                             String rawCode,
                                                             String rawCategoryCode,
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
                    "",
                    false,
                    null,
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

        String categoryLabel = trim(rawCategoryCode);
        String sede = "";
        try {
            categoryLabel = chatbotProcesoService.validateAndResolveUploadCategoryLabel(
                    email,
                    trim(rawCategoryCode),
                    trim(rawPdfFile),
                    contractName
            );
        } catch (Exception ex) {
            if (ex instanceof ChatbotProcesoService.ContractUploadValidationException validationEx) {
                throw validationEx;
            }
            log.warn("No se pudo resolver categoria actual para almacenamiento email={} categoryCode={}: {}",
                    email, trim(rawCategoryCode), ex.getMessage());
        }
        try {
            sede = chatbotProcesoService.resolveSedeByEmail(email);
        } catch (Exception ex) {
            log.warn("No se pudo resolver sede para almacenamiento email={}: {}", email, ex.getMessage());
        }

        ContractDocumentStorageService.StoredDocument stored =
                contractDocumentStorageService.storeSignedContract(file, signerName, documento, contractName, sede, categoryLabel);

        Optional<ChatbotMatriculaProceso> mergedProcesoOpt = Optional.empty();
        try {
            mergedProcesoOpt = chatbotProcesoService.mergeContractSubmissionByEmail(
                    email,
                    trim(rawCategoryCode),
                    contractName,
                    trim(rawPdfFile),
                    trim(rawFormDataJson),
                    stored.fileName(),
                    stored.publicUrl(),
                    stored.objectKey(),
                    stored.signerFolder()
            );
        } catch (ChatbotProcesoService.ContractUploadValidationException ex) {
            // The file was already stored (S3/local). Roll it back so the response remains consistent.
            try {
                contractDocumentStorageService.deleteStoredContract(stored.objectKey());
            } catch (Exception deleteEx) {
                log.warn("No se pudo hacer rollback del almacenamiento de contrato email={} key={}: {}",
                        email, safe(stored.objectKey()), deleteEx.getMessage());
            }
            throw ex;
        } catch (Exception ex) {
            // El archivo ya fue almacenado; no queremos fallar toda la respuesta por un error de persistencia.
            log.error("Contrato subido a almacenamiento pero no se pudo persistir metadata/form. email={} doc={} fileName={}: {}",
                    email, documento, safe(stored.fileName()), ex.getMessage(), ex);
        }

        boolean contractCompleted = false;
        Long studentId = procesoOpt.map(ChatbotMatriculaProceso::getStudentId).orElse(null);
        String flowStatus = procesoOpt.map(ChatbotMatriculaProceso::getFlowStatus).map(this::safe).orElse("");
        String paymentStatus = procesoOpt.map(ChatbotMatriculaProceso::getPaymentStatus).map(this::safe).orElse("");
        String message = "Contrato cargado correctamente";

        ChatbotMatriculaProceso statusSource = mergedProcesoOpt.orElseGet(() -> procesoOpt.orElse(null));
        ChatbotProcesoService.ContractCategoryFlowSnapshot contractFlow = null;
        try {
            contractFlow = chatbotProcesoService.getContractCategoryFlowByEmail(email);
        } catch (Exception ex) {
            log.warn("No se pudo resolver estado de categorias tras upload email={}: {}", email, ex.getMessage());
        }
        if (contractFlow != null && contractFlow.allCompleted()) {
            message = "Contrato cargado correctamente. Se detectaron todas las categorias firmadas.";

            try {
                chatbotProcesoService.markContractSignedByEmail(email);
                contractCompleted = true;
            } catch (Exception ex) {
                log.error("No se pudo completar la firma tras el ultimo upload email={} doc={}: {}",
                        email, documento, ex.getMessage(), ex);
                message = "Contrato cargado correctamente, pero la finalizacion del proceso quedo pendiente.";
            }

            if (contractCompleted && StringUtils.hasText(documento)) {
                try {
                    studentId = contractEnrollmentFinalizeService.forceFinalizeEnrollment(documento);
                    if (studentId != null) {
                        message = "Contrato cargado correctamente. Firma y matricula finalizadas.";
                    } else {
                        message = "Contrato cargado correctamente. Firma completada; la matricula queda pendiente.";
                    }
                } catch (Exception ex) {
                    log.error("No se pudo finalizar matricula tras upload final email={} doc={}: {}",
                            email, documento, ex.getMessage(), ex);
                    message = "Contrato cargado correctamente. La firma se completo, pero la matricula quedo pendiente.";
                }
            }
        } else if (contractFlow != null && StringUtils.hasText(contractFlow.currentCategoryLabel())) {
            message = "Contrato cargado correctamente. Continua con la categoria " + contractFlow.currentCategoryLabel() + ".";
        }

        ChatbotMatriculaProceso refreshed = resolveLatestContractStatus(email, documento).orElse(statusSource);
        if (refreshed != null) {
            documento = safe(refreshed.getNumeroDocumento());
            studentId = refreshed.getStudentId();
            flowStatus = safe(refreshed.getFlowStatus());
            paymentStatus = safe(refreshed.getPaymentStatus());
        }

        return new ContractUploadResult(
                true,
                message,
                email,
                documento,
                safe(stored.signerFolder()),
                safe(stored.objectKey()),
                safe(stored.fileName()),
                safe(stored.publicUrl()),
                contractCompleted,
                studentId,
                flowStatus,
                paymentStatus
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

    private boolean notifyContractCompletionByWhatsApp(ChatbotMatriculaProceso proceso, Long studentId) {
        if (!autoSendEnrollmentWhatsapp) return false;
        if (proceso == null || studentId == null) return false;
        if (!waService.getConfigStatus().ready()) {
            log.warn("WhatsApp no configurado. Se omite notificacion de contrato. doc={} email={}",
                    safe(proceso.getNumeroDocumento()), safe(proceso.getEmail()));
            return false;
        }

        String phone = trim(proceso.getPhone());
        if (!StringUtils.hasText(phone)) {
            phone = trim(proceso.getTelefono());
        }
        if (!StringUtils.hasText(phone)) {
            log.warn("No hay telefono para notificar por WhatsApp. doc={} email={}",
                    safe(proceso.getNumeroDocumento()), safe(proceso.getEmail()));
            return false;
        }

        String normalizedPhone = normalizePhone(phone);
        String firstMessage = buildContractReceivedWhatsappMessage(proceso, studentId);
        String secondMessage = buildNewStudentWhatsappMessage();

        log.info("\uD83D\uDCF2 Preparando envio de confirmacion de contrato por WhatsApp a {} doc={} email={}",
                maskPhone(normalizedPhone), safe(proceso.getNumeroDocumento()), safe(proceso.getEmail()));

        try {
            sendWhatsappText(normalizedPhone, firstMessage);
            sendWhatsappText(normalizedPhone, secondMessage);
            log.info("\u2705 Secuencia de WhatsApp enviada correctamente (contrato completo) to={}",
                    maskPhone(normalizedPhone));
            return true;
        } catch (RestClientException ex) {
            log.error("\u274c Error enviando secuencia de WhatsApp via endpoint interno (contrato completo) to={}: {}",
                    maskPhone(normalizedPhone), ex.getMessage(), ex);
            return false;
        } catch (Exception ex) {
            log.error("\u274c Error enviando secuencia de WhatsApp (contrato completo) to={}: {}",
                    maskPhone(normalizedPhone), ex.getMessage(), ex);
            return false;
        }
    }

    private String buildContractReceivedWhatsappMessage(ChatbotMatriculaProceso proceso, Long studentId) {
        StringBuilder message = new StringBuilder()
                .append("\u2705 \u00a1Listo! Tu contrato fue recibido.\n\n")
                .append("\uD83D\uDCCD Ac\u00e9rcate a la academia para registrar tus datos biom\u00e9tricos.\n\n")
                .append("\uD83D\uDCDD Categor\u00eda: ").append(safe(proceso.getCategoria())).append("\n")
                .append("\uD83C\uDD94 Documento: ").append(safe(proceso.getNumeroDocumento())).append("\n")
                .append("Ref estudiante: ").append(studentId).append("\n\n");

        if (StringUtils.hasText(theoryWhatsappGroupLink)) {
            message.append("\uD83D\uDC65 Grupo de WhatsApp:\n")
                    .append(trim(theoryWhatsappGroupLink))
                    .append("\n\n");
        }

        message.append("\uD83D\uDCC5 En este grupo se enviar\u00e1 la programaci\u00f3n de las clases del d\u00eda siguiente.\n\n")
                .append("Si necesitas ayuda, responde 9 para ver el menú y elige contactar un asesor.");

        return message.toString();
    }

    private String buildNewStudentWhatsappMessage() {
        return "\uD83C\uDF93 \u00a1Ya eres estudiante de HARO!\n\n"
                + "\u23F0 En tu pr\u00f3xima clase debes llegar 30 minutos antes para la toma de biom\u00e9tricos "
                + "y finalizar tu proceso de matr\u00edcula.\n\n"
                + "Gracias por escoger CEA HARO.\n\n"
                + "Para ver opciones, responde 9.";
    }

    private void sendWhatsappText(String normalizedPhone, String message) {
        if (StringUtils.hasText(internalApiBaseUrl)) {
            String base = trim(internalApiBaseUrl);
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            String url = base + "/api/whatsapp/text/send";
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("to", normalizedPhone);
            payload.put("text", message);
            payload.put("message", message);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, payload, Map.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException("Estado no exitoso al enviar WhatsApp: " + response.getStatusCode().value());
            }
            return;
        }

        waService.sendTextMessage(normalizedPhone, message);
    }

    private boolean hasAllRequiredSignedContracts(String signedContractFilesJson) {
        String signedFiles = safe(signedContractFilesJson);
        return Arrays.stream(REQUIRED_SIGNED_CONTRACTS).allMatch(signedFiles::contains);
    }

    private Optional<ChatbotMatriculaProceso> resolveLatestContractStatus(String email, String documento) {
        if (StringUtils.hasText(documento)) {
            Optional<ChatbotMatriculaProceso> byDocument = chatbotProcesoService.findProcesoByDocumento(documento);
            if (byDocument.isPresent()) {
                return byDocument;
            }
        }
        return chatbotProcesoService.findLatestProcesoByEmail(email);
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

    private static String normalizePhone(String value) {
        String normalized = trim(value).replaceAll("[\\s\\-()]", "");
        if (normalized.startsWith("+")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static String maskPhone(String phone) {
        String normalized = normalizePhone(phone);
        if (!StringUtils.hasText(normalized) || normalized.length() <= 4) {
            return "****";
        }
        return "*".repeat(normalized.length() - 4) + normalized.substring(normalized.length() - 4);
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
}
