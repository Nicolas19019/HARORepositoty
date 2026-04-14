package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Administrador;
import com.Aplication.HARO.Model.OtpToken;
import com.Aplication.HARO.Repository.AdministradorRepository;
import com.Aplication.HARO.Repository.OtpTokenRepository;
import com.Aplication.HARO.Security.OtpHasher;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * Servicio de recuperacion de contrasena para administradores.
 *
 * Genera, verifica y consume codigos temporales enviados por correo para
 * restablecer contrasenas administrativas.
 */
@Service
@Transactional
public class AdminPasswordRecoveryService {

    private static final String PURPOSE = "ADMIN_PASSWORD_RESET";

    private final AdministradorRepository adminRepository;
    private final OtpTokenRepository otpTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final SecureRandom rng = new SecureRandom();

    @Value("${app.admin-recovery.otp.length:6}")
    private int otpLength;

    @Value("${app.admin-recovery.ttl-seconds:900}")
    private long ttlSeconds;

    @Value("${app.admin-recovery.cooldown-seconds:30}")
    private long cooldownSeconds;

    @Value("${app.admin-recovery.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.admin-recovery.subject:Recuperacion de contrasena - HaroGestion}")
    private String subject;

    /**
     * Resultado resumido de verification.
     */
    public record VerificationResult(boolean ok, String message) {}

    public AdminPasswordRecoveryService(AdministradorRepository adminRepository,
                                        OtpTokenRepository otpTokenRepository,
                                        @Qualifier("passwordEncoder") PasswordEncoder passwordEncoder,
                                        MailService mailService) {
        this.adminRepository = adminRepository;
        this.otpTokenRepository = otpTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
    }

    /**
     * Inicia la solicitud del flujo correspondiente.
     */
    public void requestRecovery(String rawCorreo) {
        String correo = normalizeEmail(rawCorreo);
        Instant now = Instant.now();

        otpTokenRepository.deleteByEmailAndPurposeAndExpiresAtBefore(correo, PURPOSE, now);

        Optional<OtpToken> lastNotConsumed = otpTokenRepository
                .findTopByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc(correo, PURPOSE);
        if (lastNotConsumed.isPresent()) {
            OtpToken last = lastNotConsumed.get();
            if (last.getSentAt() != null && now.isBefore(last.getSentAt().plusSeconds(Math.max(5, cooldownSeconds)))) {
                // Respuesta silenciosa para no exponer si existe o no el correo.
                return;
            }
        }

        Optional<Administrador> adminOpt = adminRepository.findByCorreoNormalizado(correo)
                .filter(admin -> !Boolean.FALSE.equals(admin.getActivo()));
        if (adminOpt.isEmpty()) {
            return;
        }

        otpTokenRepository.deleteByEmailAndPurposeAndConsumedAtIsNull(correo, PURPOSE);

        String code = generateNumericOtp(Math.max(4, otpLength));
        OtpToken token = new OtpToken();
        token.setEmail(correo);
        token.setPurpose(PURPOSE);
        token.setOtpHash(OtpHasher.sha256(code));
        token.setExpiresAt(now.plus(Math.max(60, ttlSeconds), ChronoUnit.SECONDS));
        token.setSentAt(now);
        token.setAttempts(0);
        token.setConsumedAt(null);
        otpTokenRepository.save(token);

        Administrador admin = adminOpt.get();
        mailService.sendHtml(
                correo,
                subject,
                buildHtml(admin, code),
                buildPlain(admin, code)
        );
    }

    /**
     * Verifica el codigo o token recibido y devuelve el resultado de validacion.
     */
    public VerificationResult verifyCode(String rawCorreo, String rawCode) {
        String correo = normalizeEmail(rawCorreo);
        String code = normalizeCode(rawCode);
        Instant now = Instant.now();

        if (code.isBlank()) {
            return new VerificationResult(false, "Codigo requerido");
        }

        Optional<OtpToken> tokenOpt = otpTokenRepository
                .findTopByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc(correo, PURPOSE);
        if (tokenOpt.isEmpty()) {
            return new VerificationResult(false, "Codigo invalido o vencido");
        }

        OtpToken token = tokenOpt.get();
        if (token.getExpiresAt() == null || now.isAfter(token.getExpiresAt())) {
            token.setConsumedAt(now);
            otpTokenRepository.save(token);
            return new VerificationResult(false, "Codigo expirado");
        }

        int attempts = Optional.ofNullable(token.getAttempts()).map(Number::intValue).orElse(0);
        if (attempts >= Math.max(1, maxAttempts)) {
            token.setConsumedAt(now);
            otpTokenRepository.save(token);
            return new VerificationResult(false, "Codigo invalido o expirado");
        }

        boolean match = OtpHasher.sha256(code).equals(token.getOtpHash());
        if (!match) {
            token.setAttempts(attempts + 1);
            if ((attempts + 1) >= Math.max(1, maxAttempts)) {
                token.setConsumedAt(now);
            }
            otpTokenRepository.save(token);
            return new VerificationResult(false, "Codigo invalido o expirado");
        }

        return new VerificationResult(true, "Codigo valido");
    }

    /**
     * Restablece la contrasena despues de validar el codigo temporal.
     */
    public VerificationResult resetPassword(String rawCorreo, String rawCode, String nuevaContrasena) {
        String correo = normalizeEmail(rawCorreo);
        String code = normalizeCode(rawCode);
        String nueva = normalizePassword(nuevaContrasena);
        Instant now = Instant.now();

        Optional<Administrador> adminOpt = adminRepository.findByCorreoNormalizado(correo)
                .filter(admin -> !Boolean.FALSE.equals(admin.getActivo()));
        if (adminOpt.isEmpty()) {
            return new VerificationResult(false, "No fue posible restablecer la contrasena");
        }

        Optional<OtpToken> tokenOpt = otpTokenRepository
                .findTopByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc(correo, PURPOSE);
        if (tokenOpt.isEmpty()) {
            return new VerificationResult(false, "Codigo invalido o vencido");
        }

        OtpToken token = tokenOpt.get();
        if (token.getExpiresAt() == null || now.isAfter(token.getExpiresAt())) {
            token.setConsumedAt(now);
            otpTokenRepository.save(token);
            return new VerificationResult(false, "Codigo expirado");
        }

        int attempts = Optional.ofNullable(token.getAttempts()).map(Number::intValue).orElse(0);
        if (attempts >= Math.max(1, maxAttempts)) {
            token.setConsumedAt(now);
            otpTokenRepository.save(token);
            return new VerificationResult(false, "Codigo invalido o expirado");
        }

        boolean match = OtpHasher.sha256(code).equals(token.getOtpHash());
        token.setAttempts(attempts + 1);
        if (!match) {
            if ((attempts + 1) >= Math.max(1, maxAttempts)) {
                token.setConsumedAt(now);
            }
            otpTokenRepository.save(token);
            return new VerificationResult(false, "Codigo invalido o vencido");
        }

        Administrador admin = adminOpt.get();
        admin.setContrasenaHash(passwordEncoder.encode(nueva));
        adminRepository.save(admin);

        token.setConsumedAt(now);
        otpTokenRepository.save(token);
        return new VerificationResult(true, "Contrasena actualizada correctamente");
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeEmail(String rawCorreo) {
        String correo = rawCorreo == null ? "" : rawCorreo.trim().toLowerCase(Locale.ROOT);
        if (correo.isBlank()) {
            throw new IllegalArgumentException("correo requerido");
        }
        try {
            InternetAddress address = new InternetAddress(correo, true);
            address.validate();
        } catch (Exception ex) {
            throw new IllegalArgumentException("correo invalido");
        }
        return correo;
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeCode(String rawCode) {
        return rawCode == null ? "" : rawCode.trim();
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizePassword(String rawPassword) {
        String password = rawPassword == null ? "" : rawPassword.trim();
        if (password.length() < 8) {
            throw new IllegalArgumentException("La nueva contrasena debe tener al menos 8 caracteres");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("La nueva contrasena debe incluir al menos una mayuscula");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("La nueva contrasena debe incluir al menos una minuscula");
        }
        if (!password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("La nueva contrasena debe incluir al menos un numero");
        }
        return password;
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String generateNumericOtp(int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(rng.nextInt(10));
        }
        return out.toString();
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String safeName(Administrador admin) {
        String nombre = admin == null || admin.getNombre() == null ? "" : admin.getNombre().trim();
        return nombre.isBlank() ? "administrador" : nombre;
    }

    /**
     * Construye el valor auxiliar necesario para el flujo del servicio.
     */
    private String buildHtml(Administrador admin, String code) {
        return """
<div style="background:#f4f4f4;padding:24px;font-family:Segoe UI,Arial,sans-serif;color:#1a1a1a;">
  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:520px;margin:0 auto;background:#fff;border-radius:8px;border-top:4px solid #ffcc00;padding:24px;">
    <tr><td>
      <h2 style="margin:0 0 12px 0;color:#111827;">Recuperacion de contrasena</h2>
      <p style="margin:0 0 12px 0;">Hola %s,</p>
      <p style="margin:0 0 12px 0;">Recibimos una solicitud para restablecer la contrasena de tu cuenta de administrador en <strong>HaroGestion</strong>.</p>
      <p style="margin:0 0 8px 0;">Usa este codigo OTP en HaroGestion:</p>
      <div style="font-size:24px;font-weight:700;letter-spacing:4px;text-align:center;color:#d00000;border:2px solid #d00000;border-radius:8px;padding:12px 16px;margin:0 0 16px 0;">
        %s
      </div>
      <p style="margin:0 0 12px 0;">Este codigo vence pronto y solo puede usarse una vez.</p>
      <p style="margin:0;color:#6b7280;font-size:12px;">Si no solicitaste este cambio, ignora este mensaje.</p>
    </td></tr>
  </table>
</div>
""".formatted(safeName(admin), code);
    }

    /**
     * Construye el valor auxiliar necesario para el flujo del servicio.
     */
    private String buildPlain(Administrador admin, String code) {
        return "HaroGestion - Recuperacion de contrasena\n\n"
                + "Hola " + safeName(admin) + ",\n"
                + "Recibimos una solicitud para restablecer la contrasena de tu cuenta de administrador en HaroGestion.\n"
                + "Tu codigo OTP es: " + code + "\n"
                + "El codigo vence pronto y solo puede usarse una vez.";
    }
}
