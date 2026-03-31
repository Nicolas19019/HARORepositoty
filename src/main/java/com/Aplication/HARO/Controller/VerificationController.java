// src/main/java/com/Aplication/HARO/Controller/VerificationController.java
package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Service.VerificationService;
import com.Aplication.HARO.Service.EstudianteService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/verification")
public class VerificationController {

  private static final Logger log = LoggerFactory.getLogger(VerificationController.class);

  private final VerificationService svc;
  private final EstudianteService estudianteService;

  // Feature flags (can be overridden via env vars on Cloud Run):
  // - app.contract.complete.use-v2 -> APP_CONTRACT_COMPLETE_USE_V2
  // - app.contract.complete.enable-recovery -> APP_CONTRACT_COMPLETE_ENABLE_RECOVERY
  @Value("${app.contract.complete.use-v2:true}")
  private boolean contractCompleteUseV2;

  @Value("${app.contract.complete.enable-recovery:true}")
  private boolean contractCompleteEnableRecovery;

  public VerificationController(VerificationService svc, EstudianteService estudianteService) {
    this.svc = svc;
    this.estudianteService = estudianteService;
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
      Map<String, Object> payload = svc.buildContractAccessPayload(req.email());

      // Validacion temprana: si el estudiante ya existe por documento o correo,
      // avisar de inmediato para no hacerle perder tiempo firmando.
      String documento = payload.get("document") == null ? "" : String.valueOf(payload.get("document")).trim();
      String contactEmail = payload.get("shared_contact_email") == null ? "" : String.valueOf(payload.get("shared_contact_email")).trim();

      if (!documento.isBlank() && estudianteService.existePorNumeroDocumento(documento)) {
        body.put("ok", false);
        body.put("message", "La informacion ya existe: ya hay un estudiante registrado con ese numero de documento.");
        body.remove("data");
        return ResponseEntity.badRequest().body(body);
      }

      if (!contactEmail.isBlank() && estudianteService.existePorCorreo(contactEmail)) {
        body.put("ok", false);
        body.put("message", "La informacion ya existe: ya hay un estudiante registrado con ese correo.");
        body.remove("data");
        return ResponseEntity.badRequest().body(body);
      }

      body.put("data", payload);
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
    } catch (Exception ex) {
      boolean isRollback =
              (ex instanceof UnexpectedRollbackException) ||
              (ex.getCause() instanceof UnexpectedRollbackException) ||
              (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("rollback-only"));

      if (contractCompleteEnableRecovery && isRollback) {
        recovered = true;
        mode = "RECOVERY";
        log.warn("Rollback detectado en /contract/complete, activando RECOVERY. email={}", req.email(), ex);
        result = svc.completeContractSigningRecovery(req.email(), req.code());
      } else {
        log.error("Error en /contract/complete (mode={}) email={}: {}", mode, req.email(), ex.getMessage(), ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("message", "Error interno del servidor: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        body.put("email", req.email());
        body.put("mode", "EXCEPTION");
        body.put("recovered", false);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
      }
    }

     boolean whatsappNotified = false;
     if (result.ok() && result.studentId() != null) {
         whatsappNotified = svc.notifyContractCompletionAfterCommit(result.email(), result.studentId());
     }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("ok", result.ok());
    body.put("message", result.message());
    body.put("email", result.email());
    body.put("document", result.documento());
     body.put("studentId", result.studentId());
     body.put("flowStatus", result.flowStatus());
     body.put("paymentStatus", result.paymentStatus());
     body.put("contractFlow", svc.buildContractAccessPayload(req.email()).get("contractFlow"));
     body.put("whatsappNotified", whatsappNotified);
     body.put("mode", mode);
     body.put("recovered", recovered);

    return result.ok()
            ? ResponseEntity.ok(body)
            : ResponseEntity.badRequest().body(body);
}

  @PostMapping(value = "/contract/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> uploadContractSigned(@RequestParam @Email String email,
                                                @RequestParam @NotBlank String code,
                                                @RequestParam(name = "categoryCode", required = false) String categoryCode,
                                                @RequestParam(name = "signerName", required = false) String signerName,
                                                @RequestParam(name = "contractName", required = false) String contractName,
                                                @RequestParam(name = "pdfFile", required = false) String pdfFile,
                                                @RequestParam(name = "formData", required = false) String formData,
                                                @RequestPart("file") MultipartFile file) {
    try {
      VerificationService.ContractUploadResult out =
              svc.uploadSignedContractDocument(email, code, categoryCode, signerName, contractName, pdfFile, formData, file);
      return out.ok()
              ? ResponseEntity.ok(out)
              : ResponseEntity.badRequest().body(out);
    } catch (com.Aplication.HARO.Service.ChatbotProcesoService.ContractUploadValidationException ex) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("ok", false);
      body.put("message", ex.getMessage());
      body.put("email", email);
      body.put("categoryCode", categoryCode);
      return ResponseEntity.status(ex.getStatus()).body(body);
    }
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
