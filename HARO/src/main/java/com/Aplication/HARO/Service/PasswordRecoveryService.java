package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.Estudiante;
import com.Aplication.HARO.Repository.EstudianteRepository;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional
public class PasswordRecoveryService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
    private final SecureRandom rng = new SecureRandom();

    private final EstudianteRepository estudianteRepository;
    private final PasswordEncoder encoder;
    private final MailService mailService;

    @Value("${app.recovery.subject:Recuperacion de acceso - Modulo de gestion de aprendizaje}")
    private String recoverySubject;

    @Value("${app.recovery.temp-password.length:10}")
    private int tempPasswordLength;

    public PasswordRecoveryService(EstudianteRepository estudianteRepository,
                                   @Qualifier("passwordEncoder") PasswordEncoder encoder,
                                   MailService mailService) {
        this.estudianteRepository = estudianteRepository;
        this.encoder = encoder;
        this.mailService = mailService;
    }

    public void requestLearningModuleRecovery(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        Optional<Estudiante> estudianteOpt = estudianteRepository.findByEmailNormalizadoVisible(email);
        if (estudianteOpt.isEmpty()) {
            // Respuesta silenciosa para no filtrar existencia de cuentas.
            return;
        }

        Estudiante estudiante = estudianteOpt.get();
        String tempPassword = generateTempPassword();
        estudiante.setContrasena(encoder.encode(tempPassword));
        estudianteRepository.save(estudiante);

        String html = buildRecoveryHtml(estudiante, tempPassword);
        String plain = buildRecoveryPlain(estudiante, tempPassword);
        mailService.sendHtml(email, recoverySubject, html, plain);
    }

    private String generateTempPassword() {
        int len = Math.max(8, tempPasswordLength);
        StringBuilder out = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            out.append(ALPHABET.charAt(rng.nextInt(ALPHABET.length())));
        }
        return out.toString();
    }

    private String normalizeEmail(String raw) {
        String email = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (email.isBlank()) {
            throw new IllegalArgumentException("correo requerido");
        }
        try {
            InternetAddress addr = new InternetAddress(email, true);
            addr.validate();
        } catch (Exception ex) {
            throw new IllegalArgumentException("correo invalido");
        }
        return email;
    }

    private String safeName(Estudiante e) {
        String n = e.getNombre() == null ? "" : e.getNombre().trim();
        return n.isBlank() ? "estudiante" : n;
    }

    private String buildRecoveryHtml(Estudiante e, String tempPassword) {
        return """
<div style="background:#f4f4f4;padding:24px;font-family:Segoe UI,Arial,sans-serif;color:#1a1a1a;">
  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:520px;margin:0 auto;background:#fff;border-radius:8px;border-top:4px solid #ffcc00;padding:24px;">
    <tr><td>
      <h2 style="margin:0 0 12px 0;color:#111827;">Solicitud aceptada</h2>
      <p style="margin:0 0 12px 0;">Hola %s,</p>
      <p style="margin:0 0 12px 0;">
        Recibimos tu solicitud de recuperacion para el
        <strong>modulo de gestion de aprendizaje</strong>.
      </p>
      <p style="margin:0 0 8px 0;">Ingresa con esta contrasena temporal:</p>
      <div style="font-size:24px;font-weight:700;letter-spacing:2px;text-align:center;color:#d00000;border:2px solid #d00000;border-radius:8px;padding:12px 16px;margin:0 0 16px 0;">
        %s
      </div>
      <p style="margin:0 0 12px 0;">Por seguridad, entra a tu perfil y actualizala inmediatamente.</p>
      <p style="margin:0;color:#6b7280;font-size:12px;">Si no solicitaste este cambio, contacta soporte.</p>
    </td></tr>
  </table>
</div>
""".formatted(safeName(e), tempPassword);
    }

    private String buildRecoveryPlain(Estudiante e, String tempPassword) {
        return "CEA HARO - Recuperacion de acceso\n\n"
                + "Solicitud aceptada para modulo de gestion de aprendizaje.\n"
                + "Contrasena temporal: " + tempPassword + "\n\n"
                + "Ingresa con esta contrasena y cambiala dentro de tu perfil.";
    }
}

