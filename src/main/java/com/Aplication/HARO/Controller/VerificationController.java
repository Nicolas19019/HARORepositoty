// src/main/java/com/Aplication/HARO/Controller/VerificationController.java
package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Service.VerificationService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/verification")
public class VerificationController {

  private final VerificationService svc;

  // Feature flags (can be overridden via env vars on Cloud Run):
  // - app.contract.complete.use-v2 -> APP_CONTRACT_COMPLETE_USE_V2
  // - app.contract.complete.enable-recovery -> APP_CONTRACT_COMPLETE_ENABLE_RECOVERY
  @Value("${app.contract.complete.use-v2:true}")
  private boolean contractCompleteUseV2;

  @Value("${app.contract.complete.enable-recovery:true}")
  private boolean contractCompleteEnableRecovery;

  public VerificationController(VerificationService svc) {
    this.svc = svc;
  }

  // El body es un JSON string: "user@example.com"
  @PostMapping("/email/send")
  public ResponseEntity<?> send(@RequestBody String emailRaw) {
    // Dejamos que el service sanee y valide; si esta mal, lanzara IllegalArgumentException
    svc.sendEmailVerification(emailRaw);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/email/send/student-activation")
  public ResponseEntity<?> sendStudentActivation(@RequestBody String emailRaw) {
    svc.sendStudentActivationEmailVerification(emailRaw);
    return ResponseEntity.ok().build();
  }

  public static record VerifyReq(@Email String email, @NotBlank String code) {}

  public static record ContractLinkReq(@Email String email, String baseUrl) {}

  @PostMapping("/email/verify")
  public ResponseEntity<?> verify(@RequestBody VerifyReq req) {
    boolean ok = svc.verifyEmailOtp(req.email(), req.code());
    return ok ? ResponseEntity.ok().build()
              : ResponseEntity.badRequest().body("Codigo invalido o vencido");
  }

  @PostMapping("/contract/link")
  public ResponseEntity<VerificationService.ContractLinkResult> createContractLink(@RequestBody ContractLinkReq req) {
    VerificationService.ContractLinkResult out = svc.createContractVerificationLink(req.email(), req.baseUrl());
    return ResponseEntity.ok(out);
  }

  @PostMapping("/contract/access")
  public ResponseEntity<?> validateContractAccess(@RequestBody VerifyReq req) {
    VerificationService.ContractAccessResult out = svc.validateContractAccessCode(req.email(), req.code());
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("ok", out.ok());
    body.put("message", out.message());
    body.put("expiresAt", out.expiresAt());
    if (out.ok()) {
      body.put("data", svc.buildContractAccessPayload(req.email()));
      return ResponseEntity.ok(body);
    }
    return ResponseEntity.badRequest().body(body);
  }

@PostMapping("/contract/complete")
public ResponseEntity<?> completeContract(@RequestBody VerifyReq req) {
    VerificationService.ContractCompletionResult result;
    boolean recovered = false;
    String mode = contractCompleteUseV2 ? "V2" : "V1";
    try {
        result = contractCompleteUseV2
                ? svc.completeContractSigningV2(req.email(), req.code())
                : svc.completeContractSigning(req.email(), req.code());
    } catch (UnexpectedRollbackException ex) {
        if (!contractCompleteEnableRecovery) {
            throw ex;
        }
        // Recovery path: the original flow got rollback-only and crashed at commit time.
        // Re-run the completion in a non-transactional flow so we can still create the student/estado/pago.
        recovered = true;
        mode = "RECOVERY";
        result = svc.completeContractSigningRecovery(req.email(), req.code());
    }

    if (result.ok() && result.studentId() != null) {
        svc.notifyContractCompletionAfterCommit(result.email(), result.studentId());
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("ok", result.ok());
    body.put("message", result.message());
    body.put("email", result.email());
    body.put("document", result.documento());
    body.put("studentId", result.studentId());
    body.put("flowStatus", result.flowStatus());
    body.put("paymentStatus", result.paymentStatus());
    body.put("mode", mode);
    body.put("recovered", recovered);

    return result.ok()
            ? ResponseEntity.ok(body)
            : ResponseEntity.badRequest().body(body);
}

  @PostMapping(value = "/contract/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> uploadContractSigned(@RequestParam @Email String email,
                                                @RequestParam @NotBlank String code,
                                                @RequestParam(name = "signerName", required = false) String signerName,
                                                @RequestParam(name = "contractName", required = false) String contractName,
                                                @RequestParam(name = "pdfFile", required = false) String pdfFile,
                                                @RequestParam(name = "formData", required = false) String formData,
                                                @RequestPart("file") MultipartFile file) {
    VerificationService.ContractUploadResult out =
            svc.uploadSignedContractDocument(email, code, signerName, contractName, pdfFile, formData, file);
    return out.ok()
            ? ResponseEntity.ok(out)
            : ResponseEntity.badRequest().body(out);
  }

  @GetMapping("/contract/verify")
  public ResponseEntity<?> verifyContract(@RequestParam @Email String email,
                                          @RequestParam @NotBlank String code) {
    VerificationService.ContractCompletionResult out = svc.completeContractSigning(email, code);
    return out.ok()
            ? ResponseEntity.ok(out)
            : ResponseEntity.badRequest().body(out);
  }
}
