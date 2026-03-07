package com.Aplication.HARO.Controller;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/_smtp")
public class SmtpDiagController {
  private final JavaMailSender sender;
  public SmtpDiagController(JavaMailSender sender){ this.sender = sender; }

  @GetMapping("/test")
  public Map<String, Object> test() {
    try {
      ((JavaMailSenderImpl) sender).testConnection();
      return Map.of("ok", true, "hint", "Conexión SMTP OK");
    } catch (Exception e) {
      return Map.of("ok", false, "error", e.getClass().getSimpleName(), "message", String.valueOf(e.getMessage()));
    }
  }
}
