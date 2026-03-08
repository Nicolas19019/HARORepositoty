package com.Aplication.HARO.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/epayco")
@CrossOrigin(origins = {"https://ceaharo.com",
                                "https://www.ceaharo.com",
                                "https://*.ceaharo.com"})
public class EpaycoSessionController {

    public record Item(Long serviceId, String name, double unitPrice, int qty) {}
    public record CreateSessionRequest(String buyerEmail, String buyerName, List<Item> items) {}

    @PostMapping("/session")
    public ResponseEntity<?> createSession(@RequestBody CreateSessionRequest req) {

        // ✅ Validación mínima
        if (req == null || req.items() == null || req.items().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "items vacío"));
        }

        // 🔴 TEMPORAL (para probar que el front ya pega al backend):
        // Devuelve un "sessionId" dummy. Luego lo reemplazamos por el sessionId real de ePayco.
        return ResponseEntity.ok(Map.of("sessionId", "DUMMY_SESSION_ID"));
    }
}
