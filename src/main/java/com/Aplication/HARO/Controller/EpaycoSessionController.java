package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Service.EpaycoCheckoutContextService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/payments/epayco")
@CrossOrigin(originPatterns = {
        "https://ceaharo.com",
        "https://www.ceaharo.com",
        "https://*.ceaharo.com"
})
public class EpaycoSessionController {

    private final EpaycoCheckoutContextService checkoutContextService;

    public EpaycoSessionController(EpaycoCheckoutContextService checkoutContextService) {
        this.checkoutContextService = checkoutContextService;
    }

    @PostMapping({"/session", "/session/from-flow"})
    public ResponseEntity<?> createSession(@RequestBody Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        Long flowId = resolveFlowId(
                safePayload.get("flow_id"),
                safePayload.get("flowId"),
                safePayload.get("process_id"),
                safePayload.get("processId"),
                safePayload.get("matricula_id"),
                safePayload.get("matriculaId"),
                safePayload.get("x_extra2"),
                safePayload.get("extra2")
        );

        if (flowId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "FLOW_ID_REQUIRED",
                    "message", "Debes enviar flowId, flow_id o x_extra2 para crear la sesion de pago."
            ));
        }

        try {
            EpaycoCheckoutContextService.CheckoutContext ctx = checkoutContextService.resolveFromFlowId(flowId);
            return ResponseEntity.ok(buildSessionResponse(ctx));
        } catch (EpaycoCheckoutContextService.IncompleteProcessException ex) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                    "error", "PROCESS_INCOMPLETE",
                    "message", ex.getMessage(),
                    "flowId", ex.getFlowId(),
                    "missingFields", ex.getMissingFields()
            ));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "PROCESS_NOT_FOUND",
                    "message", ex.getMessage(),
                    "flowId", flowId
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_FLOW_ID",
                    "message", ex.getMessage()
            ));
        }
    }

    private Map<String, Object> buildSessionResponse(EpaycoCheckoutContextService.CheckoutContext ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("message", "Sesion de pago preparada desde el proceso del chatbot.");
        out.put("source", "chatbot_process");
        out.put("sessionId", "DUMMY_SESSION_ID");
        out.put("flowId", ctx.flowId());
        out.put("processId", ctx.flowId());
        out.put("buyerName", ctx.buyerName());
        out.put("buyerEmail", ctx.email());
        out.put("document", ctx.document());
        out.put("phone", ctx.phone());
        out.put("category", ctx.category());
        out.put("amount", ctx.amount() == null ? "0" : ctx.amount().toPlainString());
        out.put("currency", "COP");
        out.put("invoice", ctx.invoice());
        out.put("paymentLink", ctx.paymentLink());
        return out;
    }

    private Long resolveFlowId(Object... candidates) {
        if (candidates == null) {
            return null;
        }
        for (Object candidate : candidates) {
            Long parsed = parseFlowIdCandidate(asString(candidate));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private Long parseFlowIdCandidate(String raw) {
        String value = safeTrim(raw);
        if (!StringUtils.hasText(value) || !value.matches("^\\d{1,18}$")) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
