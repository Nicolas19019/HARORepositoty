// com/Aplication/HARO/Service/MailService.java
package com.Aplication.HARO.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
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

  public void sendHtml(String to, String subject, String html, String plainFallback) {
    try {
      MimeMessage msg = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

      // From: usa la propiedad si existe; si no, el username del sender
      String username = ((JavaMailSenderImpl) sender).getUsername();
      String from = (fromProp != null && !fromProp.isBlank()) ? fromProp : username;
      helper.setFrom(new InternetAddress(from));

      helper.setTo(to);
      helper.setSubject(subject);

      String plain = (plainFallback == null || plainFallback.isBlank())
          ? "Tu código de verificación está en la versión HTML de este correo."
          : plainFallback;

      helper.setText(plain, html); // multipart/alternative
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
}
