// src/main/java/com/Aplication/HARO/Service/VerificationService.java
package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.OtpToken;
import com.Aplication.HARO.Repository.OtpTokenRepository;
import com.Aplication.HARO.Security.OtpHasher;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

@Service
public class VerificationService {

    private final OtpTokenRepository repo;
    private final MailService mail;
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

    /** Ruta del logo inline (resources/email-assets/LogoHARO.png) */
    private static final String LOGO_CLASSPATH = "email-assets/LogoHARO.png";

    /** CID usado dentro del HTML: <img src="cid:logoHaro"> */
    private static final String CID = "logoHaro";

    public VerificationService(OtpTokenRepository repo, MailService mail) {
        this.repo = repo;
        this.mail = mail;
    }

    @PostConstruct
    void init() {
        otpLength = Math.max(4, otpLength);
        ttlSeconds = Math.max(60, ttlSeconds);
        cooldownSeconds = Math.max(5, cooldownSeconds);
        maxAttempts = Math.max(1, maxAttempts);
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

        // 6) Enviar correo (HTML + plain + imagen inline si existe)
        if (logo.exists()) {
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

    /* =========================================================
       Helpers
       ========================================================= */

    private String generateNumericOtp(int length) {
        int mod = (int) Math.pow(10, Math.max(4, length));
        int code = rng.nextInt(mod);
        return String.format("%0" + Math.max(4, length) + "d", code);
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
            if ((f == '"' && l == '"') || (f == '\'' && l == '\'')
                || (f == '“' && l == '”') || (f == '‘' && l == '’')) {
                return x.substring(1, x.length() - 1);
            }
        }
        return x;
    }

    private String buildHtml(String email, String code) {
        // HTML EXACTO (no modificado), con placeholders %s (código) y %d (minutos)
        String html = """
<div style="background-color:#f4f4f4;padding:24px;font-family:'Segoe UI',Arial,sans-serif;color:#1a1a1a;">
  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:480px;margin:0 auto;border-collapse:collapse;">
    
    <!-- ENCABEZADO CON LOGO -->
    <tr>
      <td style="text-align:center;background:#ffffff;border-radius:8px 8px 0 0;padding:0px 24px;">
        <img src="cid:logoHaro"
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
        """.formatted(code, ttlSeconds / 60);
        return html;
    }

    private String buildPlain(String email, String code) {
        long mins = Math.max(1, ttlSeconds / 60);
        return "CEA HARO - Verificación de correo\n\n"
            + "Tu código de verificación es: " + code + "\n\n"
            + "El código vence en " + mins + " minutos. Por seguridad, no lo compartas con nadie.\n\n"
            + "Si no solicitaste esta verificación, ignora este mensaje. Tu cuenta no se verá afectada.";
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
}
