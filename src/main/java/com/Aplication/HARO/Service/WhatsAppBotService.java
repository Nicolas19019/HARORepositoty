package com.Aplication.HARO.Service;

import com.Aplication.HARO.Config.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.text.Normalizer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;

@Service
public class WhatsAppBotService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppBotService.class);
    private static final int MAX_PROCESSED_IDS = 500;
    private static final int MAX_GREETED_SENDERS = 2_000;
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final Pattern MOJIBAKE_PATTERN =
            Pattern.compile("[\\u00C3\\u00C2\\u00E2\\u00F0\\u00EF]");
    private final WhatsAppProperties props;
    private final WhatsAppTemplateService waService;
    private final Deque<String> processedOrder = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, Boolean> processedSet = new ConcurrentHashMap<>();
    private final Deque<String> greetedOrder = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, Boolean> greetedSet = new ConcurrentHashMap<>();
    private volatile Instant lastImageAttemptAt;
    private volatile String lastImageAttemptTo;
    private volatile String lastImageAttemptStatus;
    private volatile String lastImageAttemptError;
    private volatile String lastImageAttemptUrl;
    @Value("${chatbot.enrollment.welcome-image-url:}")
    private String enrollmentWelcomeImageUrl;

    public WhatsAppBotService(WhatsAppProperties props, WhatsAppTemplateService waService) {
        this.props = props;
        this.waService = waService;
    }

    public record BotStatus(
            boolean enabled,
            int processedIds,
            int greetedSenders,
            String welcomeReply,
            String fallbackReply,
            Instant lastImageAttemptAt,
            String lastImageAttemptTo,
            String lastImageAttemptStatus,
            String lastImageAttemptError,
            String lastImageAttemptUrl
    ) {}

    public BotStatus status() {
        return new BotStatus(
                props.isBotEnabled(),
                processedSet.size(),
                greetedSet.size(),
                props.getBotWelcomeReply(),
                props.getBotFallbackReply(),
                lastImageAttemptAt,
                lastImageAttemptTo,
                lastImageAttemptStatus,
                lastImageAttemptError,
                lastImageAttemptUrl
        );
    }

    public void handleInboundMessages(List<WhatsAppWebhookService.InboundMessage> messages) {
        if (!props.isBotEnabled() || messages == null || messages.isEmpty()) {
            return;
        }

        for (WhatsAppWebhookService.InboundMessage message : messages) {
            try {
                handleInboundMessage(message);
            } catch (Exception e) {
                log.error("Error procesando mensaje entrante from={} id={}: {}", 
                        message == null ? null : message.from(),
                        message == null ? null : message.messageId(),
                        e.getMessage(), e);
            }
        }
    }

    private void handleInboundMessage(WhatsAppWebhookService.InboundMessage msg) {
        if (msg == null || !StringUtils.hasText(msg.from())) {
            return;
        }

        String messageId = trim(msg.messageId());
        boolean syntheticTestId = isSyntheticTestMessageId(messageId);
        if (StringUtils.hasText(messageId) && !syntheticTestId && isAlreadyProcessed(messageId)) {
            return;
        }

        String fromKey = normalizePhone(msg.from());
        String normalized = normalize(msg.text());
        log.info("WhatsApp inbound from={} text={} enrollmentStart={}",
                maskPhone(fromKey), normalized, isEnrollmentStart(normalized));

        if (isEnrollmentStart(normalized)) {
            log.info("Enviando bienvenida de matrícula con imagen a {} (text={})", maskPhone(fromKey), normalized);
            sendEnrollmentWelcomeFlow(msg.from());
            if (!syntheticTestId) {
                markProcessed(messageId);
            }
            return;
        }

        if (isFirstContact(fromKey)) {
            waService.sendTextMessage(msg.from(), normalizeOutboundText(welcomeText()));
            if (!syntheticTestId) {
                markProcessed(messageId);
            }
            return;
        }

        String reply = buildReply(msg);
        if (!StringUtils.hasText(reply)) {
            if (!syntheticTestId) {
                markProcessed(messageId);
            }
            return;
        }

        waService.sendTextMessage(msg.from(), normalizeOutboundText(reply));
        if (!syntheticTestId) {
            markProcessed(messageId);
        }
    }

    private boolean isAlreadyProcessed(String messageId) {
        return processedSet.containsKey(messageId);
    }

    private void markProcessed(String messageId) {
        if (!StringUtils.hasText(messageId)) {
            return;
        }
        processedSet.put(messageId, Boolean.TRUE);
        processedOrder.addFirst(messageId);

        while (processedOrder.size() > MAX_PROCESSED_IDS) {
            String old = processedOrder.pollLast();
            if (old != null) processedSet.remove(old);
        }
    }

    private boolean isFirstContact(String from) {
        if (!StringUtils.hasText(from)) {
            return false;
        }
        if (greetedSet.containsKey(from)) {
            return false;
        }
        greetedSet.put(from, Boolean.TRUE);
        greetedOrder.addFirst(from);

        while (greetedOrder.size() > MAX_GREETED_SENDERS) {
            String old = greetedOrder.pollLast();
            if (old != null) greetedSet.remove(old);
        }
        return true;
    }

    private boolean isEnrollmentStart(String normalized) {
        return "2".equals(normalized)
                || normalized.contains("matricula")
                || normalized.contains("inscripcion");
    }

    private void sendEnrollmentWelcomeFlow(String to) {
        String maskedTo = maskPhone(to);
        lastImageAttemptAt = Instant.now();
        lastImageAttemptTo = maskedTo;
        lastImageAttemptStatus = "PENDING";
        lastImageAttemptError = null;
        lastImageAttemptUrl = hasTextOrNull(enrollmentWelcomeImageUrl);

        if (StringUtils.hasText(enrollmentWelcomeImageUrl)) {
            try {
                log.info("ALERTA_WHATSAPP_IMAGE: intentando enviar imagen de bienvenida a {}", maskedTo);
                var result = waService.sendImageMessage(to, enrollmentWelcomeImageUrl);
                if (result.ok()) {
                    lastImageAttemptStatus = "OK";
                    log.info("ALERTA_WHATSAPP_IMAGE: imagen enviada a {}. providerMessageId={}, statusCode={}, providerResponse={}",
                            maskedTo, result.providerMessageId(), result.statusCode(), result.providerResponse());
                } else {
                    lastImageAttemptStatus = "FAILED_PROVIDER";
                    log.warn("ALERTA_WHATSAPP_IMAGE: la imagen no se confirmó como enviada a {}. statusCode={} response={}",
                            maskedTo, result.statusCode(), result.providerResponse());
                }
            } catch (Exception e) {
                lastImageAttemptStatus = "FAILED_EXCEPTION";
                lastImageAttemptError = e.getMessage();
                log.error("ALERTA_WHATSAPP_IMAGE: no se pudo enviar imagen de bienvenida a {}: {}",
                        maskedTo, e.getMessage(), e);
            }
        } else {
            lastImageAttemptStatus = "SKIPPED_NO_URL";
            lastImageAttemptError = "chatbot.enrollment.welcome-image-url no configurado";
            log.warn("ALERTA_WHATSAPP_IMAGE: no se envió imagen porque CHATBOT_ENROLLMENT_WELCOME_IMAGE_URL está vacío para {}",
                    maskedTo);
        }

        waService.sendTextMessage(to, normalizeOutboundText(
                "👋 ¡Gracias por elegirnos! Para brindarte un servicio personalizado, necesitamos algunos datos personales. "
                        + "Puedes revisar nuestra política de tratamiento de datos en www.ceaharo.com"));
        waService.sendTextMessage(to, normalizeOutboundText("🛡️ ¿Autorizas el tratamiento de tus datos?"));
    }

    private String buildReply(WhatsAppWebhookService.InboundMessage msg) {
        String normalized = normalize(msg.text());
        if (!StringUtils.hasText(normalized)) {
            return "🤖 Recibimos tu mensaje. Escribe MENU para ver opciones.";
        }

        if (normalized.equals("menu") || normalized.equals("hola") || normalized.equals("buenas")) {
            return menuText();
        }
        if (normalized.equals("1") || normalized.contains("inscripcion")) {
            return "📝 Inscripciones: escribe tu nombre completo y categoría de licencia (A2, B1, C1).";
        }
        if (normalized.equals("2") || normalized.contains("horario")) {
            return "🕒 Horarios CEA HARO: Lun-Vie 7:00 a.m. a 6:00 p.m., Sábado 7:00 a.m. a 1:00 p.m.";
        }
        if (normalized.equals("3") || normalized.contains("asesor") || normalized.contains("humano")) {
            return "🙋 Perfecto. Un asesor te responderá en breve. Si deseas, comparte tu nombre y número de documento.";
        }
        if (normalized.equals("4") || normalized.contains("precio") || normalized.contains("costo")) {
            return "💰 Para cotizarte, indícanos categoría de licencia y ciudad. Te respondemos de inmediato.";
        }

        String fallback = trim(props.getBotFallbackReply());
        return StringUtils.hasText(fallback) ? fallback : menuText();
    }

    private String welcomeText() {
        String configured = trim(props.getBotWelcomeReply());
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        return "👋 ¡Bienvenido a CEA HARO! Soy tu asistente virtual. Escribe MENU para continuar.";
    }

    private String menuText() {
        return "🤖 Hola, soy el asistente de CEA HARO.\n"
                + "Responde con una opción:\n"
                + "1️⃣ Inscripciones\n"
                + "2️⃣ Horarios\n"
                + "3️⃣ Hablar con asesor\n"
                + "4️⃣ Precios";
    }

    private String normalize(String text) {
        String v = trim(text).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(v)) return "";
        String nfd = Normalizer.normalize(v, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}+", "");
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String hasTextOrNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String normalizeOutboundText(String textRaw) {
        String text = trim(textRaw);
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String fixed = fixMojibake(text);
        fixed = fixMojibake(fixed);
        return fixed;
    }

    private String fixMojibake(String text) {
        if (!MOJIBAKE_PATTERN.matcher(text).find()) {
            return text;
        }
        String decoded = new String(text.getBytes(WINDOWS_1252), StandardCharsets.UTF_8);
        if (decoded.indexOf('\uFFFD') >= 0) {
            return text;
        }
        return mojibakeScore(decoded) <= mojibakeScore(text) ? decoded : text;
    }

    private int mojibakeScore(String text) {
        int score = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\u00C3' || ch == '\u00C2' || ch == '\u00E2' || ch == '\u00F0' || ch == '\u00EF' || ch == '\uFFFD') {
                score++;
            }
        }
        return score;
    }

    private boolean isSyntheticTestMessageId(String messageId) {
        if (!StringUtils.hasText(messageId)) {
            return false;
        }
        String id = messageId.toLowerCase(Locale.ROOT);
        return id.startsWith("wamid.test") || id.startsWith("test_");
    }

    private String normalizePhone(String from) {
        String cleaned = trim(from).replaceAll("[\\s\\-()]", "");
        if (cleaned.startsWith("+")) cleaned = cleaned.substring(1);
        return cleaned;
    }

    private String maskPhone(String phone) {
        String normalized = normalizePhone(phone);
        if (!StringUtils.hasText(normalized) || normalized.length() <= 4) {
            return "*".repeat(Math.max(1, normalized.length()));
        }
        return "*".repeat(Math.max(1, normalized.length() - 4)) + normalized.substring(normalized.length() - 4);
    }
}

