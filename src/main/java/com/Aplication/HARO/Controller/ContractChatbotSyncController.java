package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Service.ChatbotProcesoService;
import com.Aplication.HARO.Service.VerificationService;
import jakarta.validation.constraints.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The contract UI (static JS) retries "sync" calls to a list of endpoints after the last PDF upload.
 * Historically some of those endpoints didn't exist and, with throw-exception-if-no-handler-found enabled,
 * they were surfaced as 500s.
 *
 * This controller provides compatible endpoints (best-effort) and retries the key operation:
 * create/update student + estado de cuenta + pago, then WhatsApp notification.
 */
@RestController
public class ContractChatbotSyncController {

  private static final Logger log = LoggerFactory.getLogger(ContractChatbotSyncController.class);

  private final ChatbotProcesoService chatbotProcesoService;
  private final VerificationService verificationService;

  public ContractChatbotSyncController(ChatbotProcesoService chatbotProcesoService,
                                       VerificationService verificationService) {
    this.chatbotProcesoService = chatbotProcesoService;
    this.verificationService = verificationService;
  }

  // One handler for all "sync" endpoints used by the contract UI.
  @PostMapping(
      path = {
          "/api/chatbot/enrollment/complete",
          "/api/chatbot/enrollment/completed",
          "/api/chatbot/contract/complete",
          "/api/chatbot/contract/completed",
          "/api/whatsapp/enrollment/complete",
          "/api/whatsapp/contract/complete",
          "/api/verification/contract/chatbot-sync"
      },
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  public ResponseEntity<?> sync(@RequestBody Map<String, Object> payload) {
    String email = normalizeEmail(payload.get("email"));
    String doc = firstNotBlank(payload, "document", "x_extra1", "numeroDocumento", "numero_documento");
    String phone = firstNotBlank(payload, "phone", "telefono", "celular");

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ok", true);
    out.put("chatbotSynced", true);
    out.put("enrollmentChatbotSynced", true);
    out.put("completionChatbotSynced", true);
    out.put("contractCompletedChatbotSynced", true);
    out.put("email", email);
    out.put("document", doc);
    out.put("phone", phone);

    // Mark contract signed if we can resolve the process by email (best effort).
    if (StringUtils.hasText(email)) {
      try {
        chatbotProcesoService.markContractSignedByEmail(email);
      } catch (Exception ex) {
        log.debug("sync: no se pudo marcar SIGNED por email={}: {}", email, ex.getMessage());
      }
    }

    // Retry finalization (student + estado de cuenta + pago).
    Long studentId = null;
    if (StringUtils.hasText(doc)) {
      try {
        studentId = chatbotProcesoService.forceFinalizeEnrollmentByDocumento(doc);
        out.put("studentId", studentId);
        out.put("finalized", true);
      } catch (Exception ex) {
        out.put("ok", false);
        out.put("chatbotSynced", false);
        out.put("finalized", false);
        out.put("message", "No se pudo finalizar matricula: " + safe(ex.getMessage()));
        log.error("sync: fallo finalizando matricula doc={} email={}: {}", doc, email, ex.getMessage(), ex);
        return ResponseEntity.status(500).body(out);
      }
    } else {
      out.put("finalized", false);
      out.put("message", "Falta documento para finalizar matricula.");
    }

    // WhatsApp notification (best effort).
    if (studentId != null && StringUtils.hasText(email)) {
      try {
        verificationService.notifyContractCompletionAfterCommit(email, studentId);
        out.put("whatsappNotified", true);
      } catch (Exception ex) {
        out.put("whatsappNotified", false);
      }
    }

    return ResponseEntity.ok(out);
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }

  private static String normalizeEmail(Object raw) {
    String v = raw == null ? "" : String.valueOf(raw).trim();
    return v.toLowerCase(Locale.ROOT);
  }

  private static String firstNotBlank(Map<String, Object> payload, String... keys) {
    for (String k : keys) {
      Object v = payload.get(k);
      if (v == null) continue;
      String s = String.valueOf(v).trim();
      if (!s.isEmpty()) return s;
    }
    return "";
  }
}

