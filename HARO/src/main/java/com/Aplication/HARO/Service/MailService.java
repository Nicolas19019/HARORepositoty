package com.Aplication.HARO.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailService {

  private final JavaMailSender sender;

  public MailService(JavaMailSender sender) {
    this.sender = sender;
  }

  public void sendHtml(String to, String subject, String html, String plainFallback) {
    try {
      MimeMessage msg = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

      String username = ((JavaMailSenderImpl) sender).getUsername();
      helper.setFrom(new InternetAddress(username)); // From = tu Gmail exacto
      helper.setTo(to);
      helper.setSubject(subject);

      String plain = (plainFallback == null || plainFallback.isBlank())
          ? "Tu código de verificación está en la versión HTML de este correo."
          : plainFallback;

      helper.setText(plain, html); // multipart/alternative
      sender.send(msg);
    } catch (MessagingException e) {
      throw new IllegalStateException("Error enviando correo: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new IllegalStateException("Error general enviando correo: " + e.getMessage(), e);
    }
  }
}
