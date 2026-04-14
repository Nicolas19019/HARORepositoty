package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Service.PasswordRecoveryService;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador REST para password recovery.
 */
@Validated
@RestController
@RequestMapping("/api/auth/password")
@CrossOrigin(origins = "*")
public class PasswordRecoveryController {

    private final PasswordRecoveryService service;

/**
 * Inyecta las dependencias necesarias del controlador.
 */
    public PasswordRecoveryController(PasswordRecoveryService service) {
        this.service = service;
    }

    /**
     * DTO de entrada con el correo para iniciar la recuperacion.
     */
    public record RecoveryRequest(
            @JsonAlias({"email", "correo"}) @NotBlank String email
    ) {}

/**
 * Solicita el inicio del proceso de recuperacion.
 */
    @PostMapping("/recovery/request")
    public ResponseEntity<Map<String, Object>> requestRecovery(@RequestBody RecoveryRequest req) {
        service.requestLearningModuleRecovery(req.email());
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "flow", "TEMP_PASSWORD_BY_EMAIL",
                "message", "Solicitud aceptada. Revisa tu correo."
        ));
    }
}

