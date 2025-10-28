package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.OtpToken;
import com.Aplication.HARO.Repository.OtpTokenRepository;
import com.Aplication.HARO.Security.OtpHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class VerificationService {

    private final OtpTokenRepository repo;
    private final MailService mail;
    private final SecureRandom rng = new SecureRandom();

    @Value("${app.verification.otp.length:6}")
    private int otpLength;

    @Value("${app.verification.otp.ttl-seconds:600}")
    private int ttlSeconds;

    public VerificationService(OtpTokenRepository repo, MailService mail) {
        this.repo = repo;
        this.mail = mail;
    }

    private String generateNumericOtp() {
        int mod = (int) Math.pow(10, otpLength);
        int code = rng.nextInt(mod);
        return String.format("%0" + otpLength + "d", code);
    }

    @Transactional
    public void sendEmailVerification(String email) {
        String normalized = email.toLowerCase().trim();

        // 1. Limpia tokens viejos expirados
        repo.deleteByEmailAndPurposeAndExpiresAtBefore(
                normalized,
                "EMAIL_VERIFY",
                Instant.now()
        );

        // 2. Genera OTP y guarda hash
        String code = generateNumericOtp();
        String hash = OtpHasher.sha256(code);

        OtpToken token = new OtpToken();
        token.setEmail(normalized);
        token.setPurpose("EMAIL_VERIFY");
        token.setOtpHash(hash);
        token.setExpiresAt(Instant.now().plus(ttlSeconds, ChronoUnit.SECONDS));
        token.setSentAt(Instant.now());
        repo.save(token);

        // 3. Construye el HTML con placeholders dinámicos (código y minutos)
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
          Tel: (XXX) XXX XXXX
        </p>

        <div style="margin-top:16px;font-size:11px;line-height:1.4;color:#9ca3af;text-align:center;border-left:4px solid #ffcc00;padding-left:8px;">
          Este es un mensaje automático, por favor no respondas a este correo.
        </div>

      </td>
    </tr>

  </table>
</div>
        """.formatted(code, ttlSeconds / 60);

        String plain = """
CEA HARO - Verificación de correo

Tu código de verificación es: %s

El código vence en %d minutos. Por seguridad, no lo compartas con nadie.

Si no solicitaste esta verificación, ignora este mensaje. Tu cuenta no se verá afectada.
        """.formatted(code, ttlSeconds / 60);

        // 4. Cargar el logo desde el classpath para enviarlo inline
        ClassPathResource logo = new ClassPathResource("email-assets/LogoHARO.png");

        // 5. Enviar correo usando HTML + texto plano + imagen inline
        mail.sendHtmlWithInlineImage(
                normalized,
                "Verificación de correo – CEA HARO",
                html,
                plain,
                "logoHaro",    // <-- este es el cid que usamos en el <img src="cid:logoHaro">
                logo,
                "image/png"
        );
    }

    @Transactional
    public boolean verifyEmailOtp(String email, String code) {
        String normalized = email.toLowerCase().trim();

        var opt = repo.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc(
                normalized,
                "EMAIL_VERIFY"
        );
        if (opt.isEmpty()) return false;

        var token = opt.get();
        if (token.getAttempts() >= 5) return false;

        token.setAttempts(token.getAttempts() + 1);

        boolean notExpired = token.getExpiresAt().isAfter(Instant.now());
        boolean match = OtpHasher.sha256(code).equals(token.getOtpHash());

        if (notExpired && match) {
            token.setConsumedAt(Instant.now());
            repo.save(token);
            return true;
        } else {
            repo.save(token);
            return false;
        }
    }
}
