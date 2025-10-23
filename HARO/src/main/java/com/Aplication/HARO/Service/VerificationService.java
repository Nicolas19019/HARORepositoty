package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.OtpToken;
import com.Aplication.HARO.Repository.OtpTokenRepository;
import com.Aplication.HARO.Security.OtpHasher;
import com.Aplication.HARO.Service.MailService;
import org.springframework.beans.factory.annotation.Value;
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

    repo.deleteByEmailAndPurposeAndExpiresAtBefore(normalized, "EMAIL_VERIFY", Instant.now());

    String code = generateNumericOtp();
    String hash = OtpHasher.sha256(code);

    OtpToken token = new OtpToken();
    token.setEmail(normalized);
    token.setPurpose("EMAIL_VERIFY");
    token.setOtpHash(hash);
    token.setExpiresAt(Instant.now().plus(ttlSeconds, ChronoUnit.SECONDS));
    token.setSentAt(Instant.now());
    repo.save(token);

    String subject = "Código de verificación";
    String html = """
      <div style="font-family:Arial,sans-serif;line-height:1.5">
        <h3>Verificación de correo</h3>
        <p>Tu código de verificación es: <b>%s</b></p>
        <p>Vence en %d minutos.</p>
        <small>Si no lo solicitaste, ignora este correo.</small>
      </div>
    """.formatted(code, ttlSeconds / 60);

    String plain = "Tu código de verificación es: " + code +
                   " (vence en " + (ttlSeconds / 60) + " minutos).";

    mail.sendHtml(normalized, subject, html, plain);
  }

  @Transactional
  public boolean verifyEmailOtp(String email, String code) {
    String normalized = email.toLowerCase().trim();

    var opt = repo.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc(normalized, "EMAIL_VERIFY");
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
