// com/Aplication/HARO/Service/MailService.java
package com.Aplication.HARO.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private final JavaMailSender sender;

    @Value("${app.verification.from:}")
    private String fromProp;

    public MailService(JavaMailSender sender) {
        this.sender = sender;
    }

    /**
     * Método existente: envía HTML + texto plano fallback.
     * Lo dejamos igual para cualquier correo que no necesite logo.
     */
    public void sendHtml(String to, String subject, String html, String plainFallback) {
        try {
            MimeMessage msg = sender.createMimeMessage();

            // "true" => multipart, nos deja tener texto plano + html
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            // From dinámico
            String username = ((JavaMailSenderImpl) sender).getUsername();
            String from = (fromProp != null && !fromProp.isBlank()) ? fromProp : username;
            helper.setFrom(new InternetAddress(from));

            helper.setTo(to);
            helper.setSubject(subject);

            String plain = (plainFallback == null || plainFallback.isBlank())
                ? "Tu código de verificación está en la versión HTML de este correo."
                : plainFallback;

            // setText(plain, html) => primera parte es texto plano, segunda es HTML
            helper.setText(plain, html);

            sender.send(msg);
        } catch (MessagingException e) {
            throw new IllegalStateException(
                "Error enviando correo (MessagingException). Revisa remitente, destinatario y adjuntos. Detalle: " + e.getMessage(), e
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                "Error general enviando correo. Verifica SMTP (host/puerto/credenciales) y que la App Password sea válida. Detalle: " + e.getMessage(), e
            );
        }
    }

    /**
     * NUEVO: igual que sendHtml, pero además inserta una imagen inline accesible
     * dentro del HTML usando <img src="cid:logoHaro">.
     *
     * @param to            destinatario
     * @param subject       asunto
     * @param html          cuerpo HTML (debe tener <img src="cid:{contentId}">)
     * @param plainFallback cuerpo texto plano
     * @param contentId     el CID, por ejemplo "logoHaro"
     * @param imageResource el recurso (logo) que cargamos del classpath
     * @param mimeType      "image/png" o "image/jpeg"
     */
    public void sendHtmlWithInlineImage(
            String to,
            String subject,
            String html,
            String plainFallback,
            String contentId,
            Resource imageResource,
            String mimeType
    ) {
        try {
            MimeMessage msg = sender.createMimeMessage();

            // MUY IMPORTANTE:
            // multipart = true para permitir partes relacionadas (html + inline img)
            MimeMessageHelper helper = new MimeMessageHelper(
                msg,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                "UTF-8"
            );

            // From dinámico igual que arriba
            String username = ((JavaMailSenderImpl) sender).getUsername();
            String from = (fromProp != null && !fromProp.isBlank()) ? fromProp : username;
            helper.setFrom(new InternetAddress(from));

            helper.setTo(to);
            helper.setSubject(subject);

            String plain = (plainFallback == null || plainFallback.isBlank())
                ? "Tu código de verificación está en la versión HTML de este correo."
                : plainFallback;

            // HTML + fallback de texto plano
            helper.setText(plain, html);

            // Adjuntar el logo como imagen inline
            helper.addInline(contentId, imageResource, mimeType);

            sender.send(msg);
        } catch (MessagingException e) {
            throw new IllegalStateException(
                "Error enviando correo con imagen inline. Detalle: " + e.getMessage(), e
            );
        } catch (Exception e) {
            throw new IllegalStateException(
                "Error general enviando correo con imagen inline. Detalle: " + e.getMessage(), e
            );
        }
    }
}
