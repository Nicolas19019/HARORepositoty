package com.Aplication.HARO.Controller;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador REST para SMTP diag.
 */
@RestController
@RequestMapping("/api/_smtp")
public class SmtpDiagController {
  private final JavaMailSender sender;
/**
 * Inyecta las dependencias necesarias del controlador.
 */
  public SmtpDiagController(JavaMailSender sender){ this.sender = sender; }

/**
 * Prueba la conectividad SMTP configurada en la aplicacion.
 */
  @GetMapping("/test")
  public Map<String, Object> test() {
    try {
      ((JavaMailSenderImpl) sender).testConnection();
      return Map.of("ok", true, "hint", "ConexiÃ³n SMTP OK");
    } catch (Exception e) {
      return Map.of("ok", false, "error", e.getClass().getSimpleName(), "message", String.valueOf(e.getMessage()));
    }
  }
}
