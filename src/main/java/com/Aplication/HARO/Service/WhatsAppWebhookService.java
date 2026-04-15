package com.Aplication.HARO.Service;

import com.Aplication.HARO.Config.WhatsAppProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Servicio de webhook de WhatsApp.
 *
 * Verifica challenge, valida firma opcional, procesa payloads entrantes y
 * conserva mensajes recientes para diagnostico.
 */
@Service
public class WhatsAppWebhookService {

    private static final int MAX_STORED_MESSAGES = 200;

    private final WhatsAppProperties props;
    private final ObjectMapper objectMapper;
    private final Deque<InboundMessage> recentMessages = new ConcurrentLinkedDeque<>();

    public WhatsAppWebhookService(WhatsAppProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * DTO de entrada para mensajes entrantes.
     */
    public record InboundMessage(
            Instant receivedAt,
            String messageId,
            String from,
            String type,
            String text
    ) {}

    /**
     * Estado resumido del webhook de WhatsApp.
     */
    public record WebhookStatus(
            boolean verifyTokenConfigured,
            boolean signatureValidationEnabled,
            int storedMessages
    ) {}

    /**
     * Devuelve un resumen de estado para consumo administrativo o diagnostico.
     */
    public WebhookStatus status() {
        return new WebhookStatus(
                StringUtils.hasText(props.getWebhookVerifyToken()),
                StringUtils.hasText(props.getAppSecret()),
                recentMessages.size()
        );
    }

    /**
     * Verifica el codigo o token recibido y devuelve el resultado de validacion.
     */
    public String verifyChallenge(String mode, String verifyToken, String challenge) {
        if (!"subscribe".equals(mode)) {
            throw new IllegalArgumentException("hub.mode invalido");
        }
        if (!StringUtils.hasText(challenge)) {
            throw new IllegalArgumentException("hub.challenge requerido");
        }
        if (!StringUtils.hasText(props.getWebhookVerifyToken())) {
            throw new IllegalStateException("Configura WHATSAPP_WEBHOOK_VERIFY_TOKEN");
        }
        if (!props.getWebhookVerifyToken().trim().equals(trim(verifyToken))) {
            throw new SecurityException("hub.verify_token invalido");
        }
        return challenge;
    }

    /**
     * Valida la informacion recibida y devuelve el resultado normalizado.
     */
    public void validateSignatureIfConfigured(String signatureHeader, String rawBody) {
        if (!StringUtils.hasText(props.getAppSecret())) {
            return;
        }
        if (!StringUtils.hasText(signatureHeader)) {
            throw new SecurityException("Falta X-Hub-Signature-256");
        }

        String expected = "sha256=" + hmacSha256Hex(props.getAppSecret(), rawBody == null ? "" : rawBody);
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = trim(signatureHeader).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(left, right)) {
            throw new SecurityException("Firma webhook invalida");
        }
    }

    /**
     * Ejecuta la operacion publica del servicio.
     */
    public List<InboundMessage> processIncomingPayload(String rawBody) {
        if (!StringUtils.hasText(rawBody)) return List.of();
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rawBody, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON webhook invalido");
        }

        List<InboundMessage> parsedMessages = new ArrayList<>();
        Object entryObj = payload.get("entry");
        if (!(entryObj instanceof List<?> entries)) return parsedMessages;

        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> entryMap)) continue;
            Object changesObj = entryMap.get("changes");
            if (!(changesObj instanceof List<?> changes)) continue;

            for (Object change : changes) {
                if (!(change instanceof Map<?, ?> changeMap)) continue;
                Object valueObj = changeMap.get("value");
                if (!(valueObj instanceof Map<?, ?> value)) continue;

                Object messagesObj = value.get("messages");
                if (!(messagesObj instanceof List<?> messages)) continue;

                for (Object msgObj : messages) {
                    if (!(msgObj instanceof Map<?, ?> msg)) continue;
                    String messageId = asString(msg.get("id"));
                    String from = asString(msg.get("from"));
                    String type = asString(msg.get("type"));
                    String text = extractMessageText(msg, type);
                    InboundMessage inbound = new InboundMessage(Instant.now(), messageId, from, type, text);
                    storeMessage(inbound);
                    parsedMessages.add(inbound);
                }
            }
        }
        return parsedMessages;
    }

    /**
     * Ejecuta la operacion publica del servicio.
     */
    public List<InboundMessage> lastMessages(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<InboundMessage> out = new ArrayList<>(safeLimit);
        int count = 0;
        for (InboundMessage msg : recentMessages) {
            out.add(msg);
            count++;
            if (count >= safeLimit) break;
        }
        return out;
    }

    /**
     * Limpia registros temporales o vencidos asociados al flujo.
     */
    public void clearMessages() {
        recentMessages.clear();
    }

    /**
     * Persiste los cambios auxiliares generados por el servicio.
     */
    private void storeMessage(InboundMessage message) {
        recentMessages.addFirst(message);
        while (recentMessages.size() > MAX_STORED_MESSAGES) {
            recentMessages.pollLast();
        }
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String extractMessageText(Map<?, ?> msg, String type) {
        if ("text".equals(type)) {
            return nestedString(msg, "text", "body");
        }
        if ("button".equals(type)) {
            return nestedString(msg, "button", "text");
        }
        if ("image".equals(type)) {
            return nestedString(msg, "image", "caption");
        }
        if ("document".equals(type)) {
            return nestedString(msg, "document", "caption");
        }
        if ("interactive".equals(type)) {
            String replyTitle = nestedString(msg, "interactive", "button_reply", "title");
            if (StringUtils.hasText(replyTitle)) return replyTitle;
            return nestedString(msg, "interactive", "list_reply", "title");
        }
        return null;
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String nestedString(Map<?, ?> root, String... path) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(key);
            if (current == null) return null;
        }
        return asString(current);
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String asString(Object v) {
        return v == null ? null : v.toString();
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String hmacSha256Hex(String secret, String body) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = hmac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo validar firma webhook");
        }
    }
}
