package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Service.WhatsAppBotService;
import com.Aplication.HARO.Service.WhatsAppWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para WhatsApp webhook.
 */
@RestController
@RequestMapping("/api/whatsapp/webhook")
@CrossOrigin(origins = "*")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private final WhatsAppWebhookService service;
    private final WhatsAppBotService botService;

/**
 * Inyecta las dependencias necesarias del controlador.
 */
    public WhatsAppWebhookController(WhatsAppWebhookService service, WhatsAppBotService botService) {
        this.service = service;
        this.botService = botService;
    }

    /**
     * Valida el webhook de WhatsApp durante la verificacion de Meta.
     */
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {
        try {
            String out = service.verifyChallenge(mode, verifyToken, challenge);
            return ResponseEntity.ok(out);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("forbidden");
        }
    }

    /**
     * Recibe y procesa eventos entrantes del webhook de WhatsApp.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> receiveWebhook(
            @RequestBody String rawBody,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signatureHeader
    ) {
        try {
            service.validateSignatureIfConfigured(signatureHeader, rawBody);
            List<WhatsAppWebhookService.InboundMessage> messages = service.processIncomingPayload(rawBody);
            botService.handleInboundMessages(messages);
            return ResponseEntity.ok("EVENT_RECEIVED");
        } catch (SecurityException e) {
            log.warn("Webhook rechazado por firma/seguridad: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("forbidden");
        } catch (Exception e) {
            // Nunca bloqueamos el webhook de Meta por errores internos de negocio.
            log.error("Error procesando webhook WhatsApp: {}", e.getMessage(), e);
            return ResponseEntity.ok("EVENT_RECEIVED");
        }
    }

    /**
     * Lista los mensajes recientes procesados por el webhook.
     */
    @GetMapping("/messages")
    public ResponseEntity<List<WhatsAppWebhookService.InboundMessage>> recentMessages(
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(service.lastMessages(limit));
    }

/**
 * Limpia los mensajes almacenados por el webhook.
 */
    @DeleteMapping("/messages")
    public ResponseEntity<Void> clearMessages() {
        service.clearMessages();
        return ResponseEntity.noContent().build();
    }

/**
 * Consulta el estado del controlador o servicio asociado.
 */
    @GetMapping("/status")
    public ResponseEntity<WhatsAppWebhookService.WebhookStatus> status() {
        return ResponseEntity.ok(service.status());
    }

/**
 * Consulta el estado del bot asociado al webhook.
 */
    @GetMapping("/bot/status")
    public ResponseEntity<WhatsAppBotService.BotStatus> botStatus() {
        return ResponseEntity.ok(botService.status());
    }
}
