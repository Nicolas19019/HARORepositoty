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

  @PostMapping("/email/send")
  public ResponseEntity<?> send(@RequestParam @Email String email) {
    svc.sendEmailVerification(email);
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
