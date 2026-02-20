package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Service.EpaycoService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/epayco")
public class EpaycoController {

    private final EpaycoService epaycoService;

    public EpaycoController(EpaycoService epaycoService) {
        this.epaycoService = epaycoService;
    }

    // ---------------------------
    // Página/endpoint de respuesta (visible al usuario)
    // ---------------------------
    @GetMapping("/response")
    public ResponseEntity<?> response(@RequestParam(name = "ref_payco", required = false) String refPayco) {
        if (refPayco == null || refPayco.isBlank()) {
            return ResponseEntity.badRequest().body(
                    java.util.Map.of("error", "Falta ref_payco")
            );
        }
        String data = epaycoService.fetchTransactionByRefPayco(refPayco);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(data);
    }

    // ---------------------------
    // Crear sesión (para Smart Checkout)
    // El front hace POST y espera { "sessionId": "..." }
    // ---------------------------
    @CrossOrigin(origins = {"http://127.0.0.1:8081", "http://localhost:8081"})
    @PostMapping(value = "/session", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSession(@RequestBody java.util.Map<String, Object> payload) {

        // 🔴 TEMPORAL para probar el flujo (luego lo conectamos a ePayco real)
        // Solo validación mínima
        Object items = payload.get("items");
        if (items == null) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "items vacío"));
        }

        return ResponseEntity.ok(java.util.Map.of("sessionId", "DUMMY_SESSION_ID"));
    }


    // ---------------------------
    // URL de confirmación (Webhook)
    // ePayco normalmente envía x-www-form-urlencoded
    // ---------------------------
    @PostMapping(value = "/confirmation", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> confirmation(@RequestBody MultiValueMap<String, String> form) {

        String xRefPayco = form.getFirst("x_ref_payco");
        String xTransactionId = form.getFirst("x_transaction_id");
        String xAmount = form.getFirst("x_amount");
        String xCurrencyCode = form.getFirst("x_currency_code");
        String xSignature = form.getFirst("x_signature");

        boolean ok = epaycoService.isValidSignature(xRefPayco, xTransactionId, xAmount, xCurrencyCode, xSignature);
        if (!ok) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Firma inválida"));
        }

        String estado = form.getFirst("x_response"); // "Aceptada", "Rechazada", "Pendiente" (según tu lógica)
        // Recomendación práctica: guarda SIEMPRE el payload completo en BD/log
        // y marca la orden según x_ref_payco o x_transaction_id

        if ("Aceptada".equalsIgnoreCase(estado)) {
            System.out.println("Pago aprobado: " + form);
            // TODO: actualizar orden en BD, habilitar servicio/matrícula, emitir factura, etc.
        } else if ("Rechazada".equalsIgnoreCase(estado)) {
            System.out.println("Pago rechazado: " + form);
        } else if ("Pendiente".equalsIgnoreCase(estado)) {
            System.out.println("Pago pendiente: " + form);
        } else {
            System.out.println("Pago estado desconocido: " + estado + " - " + form);
        }

        // ePayco necesita 200 OK
        return ResponseEntity.ok(java.util.Map.of("status", "ok"));
    }
}