// src/main/java/com/Aplication/HARO/Controller/VerificationController.java
package com.Aplication.HARO.Controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.Aplication.HARO.Service.VerificationService;

@Validated
@RestController
@RequestMapping("/api/verification")
public class VerificationController {

  private final VerificationService svc;
  public VerificationController(VerificationService svc) { this.svc = svc; }

  // El body es un JSON string: "user@example.com"
  @PostMapping("/email/send")
  public ResponseEntity<?> send(@RequestBody String emailRaw) {
    // Dejamos que el service sanee y valide; si está mal, lanzará IllegalArgumentException
    svc.sendEmailVerification(emailRaw);
    return ResponseEntity.ok().build();
  }

  public static record VerifyReq(@Email String email, @NotBlank String code) {}

  @PostMapping("/email/verify")
  public ResponseEntity<?> verify(@RequestBody VerifyReq req) {
    boolean ok = svc.verifyEmailOtp(req.email(), req.code());
    return ok ? ResponseEntity.ok().build()
              : ResponseEntity.badRequest().body("Código inválido o vencido");
  }
}
