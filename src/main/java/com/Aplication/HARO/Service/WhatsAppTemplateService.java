package com.Aplication.HARO.Service;

import com.Aplication.HARO.Config.WhatsAppProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class WhatsAppTemplateService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE =
            new ParameterizedTypeReference<>() {};

    private final WhatsAppProperties props;
    private final RestTemplate restTemplate;

    public WhatsAppTemplateService(WhatsAppProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(1000, props.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Math.max(1000, props.getReadTimeoutMs()));
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public record SendResult(
            boolean ok,
            String to,
            String templateName,
            String providerMessageId,
            int statusCode,
            Map<String, Object> providerResponse
    ) {}

    public record ConfigStatus(
            boolean ready,
            boolean enabled,
            boolean restrictToDefault,
            String defaultToMasked,
            String message
    ) {}

    public ConfigStatus getConfigStatus() {
        if (!props.isEnabled()) {
            return new ConfigStatus(false, false, props.isRestrictToDefault(), maskPhone(props.getDefaultTo()),
                    "whatsapp.enabled=false");
        }

        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(props.getPhoneNumberId())) missing.add("whatsapp.phone-number-id");
        if (!StringUtils.hasText(props.getAccessToken())) missing.add("whatsapp.access-token");
        if (props.isRestrictToDefault() && !StringUtils.hasText(props.getDefaultTo())) {
            missing.add("whatsapp.default-to");
        }

        if (!missing.isEmpty()) {
            return new ConfigStatus(false, true, props.isRestrictToDefault(), maskPhone(props.getDefaultTo()),
                    "Faltan propiedades: " + String.join(", ", missing));
        }

        if (StringUtils.hasText(props.getDefaultTo())) {
            try {
                normalizePhone(props.getDefaultTo());
            } catch (Exception e) {
                return new ConfigStatus(false, true, props.isRestrictToDefault(), maskPhone(props.getDefaultTo()),
                        "whatsapp.default-to invalido");
            }
        }

        return new ConfigStatus(true, true, props.isRestrictToDefault(), maskPhone(props.getDefaultTo()), "OK");
    }

    public SendResult sendTemplate(String templateName,
                                   String toRaw,
                                   String languageCodeRaw,
                                   List<String> paramsRaw) {
        validateEnabledAndConfigured();

        String template = normalizeTemplateName(templateName);
        String to = resolveRecipient(toRaw);
        String languageCode = resolveLanguageCode(languageCodeRaw);
        List<String> params = sanitizeParams(paramsRaw);

        String url = buildMessagesUrl();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                buildPayload(template, to, languageCode, params),
                buildHeaders()
        );

        try {
            ResponseEntity<Map<String, Object>> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, MAP_RESPONSE);
            Map<String, Object> body = response.getBody() == null ? Map.of() : response.getBody();
            return new SendResult(
                    response.getStatusCode().is2xxSuccessful(),
                    to,
                    template,
                    extractMessageId(body),
                    response.getStatusCode().value(),
                    body
            );
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("Error WhatsApp API (" + e.getStatusCode().value() + "): "
                    + safeTrim(e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("No se pudo conectar con WhatsApp API: " + e.getMessage(), e);
        }
    }

    public SendResult sendTextMessage(String toRaw, String textRaw) {
        validateEnabledAndConfigured();

        String to = resolveRecipient(toRaw);
        String text = safeTrim(textRaw);
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("text es requerido");
        }
        if (text.length() > 4096) {
            throw new IllegalArgumentException("text supera 4096 caracteres");
        }

        String url = buildMessagesUrl();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "text");
        payload.put("text", Map.of("preview_url", false, "body", text));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, buildHeaders());
        try {
            ResponseEntity<Map<String, Object>> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, MAP_RESPONSE);
            Map<String, Object> body = response.getBody() == null ? Map.of() : response.getBody();
            return new SendResult(
                    response.getStatusCode().is2xxSuccessful(),
                    to,
                    "text",
                    extractMessageId(body),
                    response.getStatusCode().value(),
                    body
            );
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("Error WhatsApp API (" + e.getStatusCode().value() + "): "
                    + safeTrim(e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("No se pudo conectar con WhatsApp API: " + e.getMessage(), e);
        }
    }

    public SendResult sendImageMessage(String toRaw, String imageUrlRaw) {
        validateEnabledAndConfigured();

        String to = resolveRecipient(toRaw);
        String imageUrl = safeTrim(imageUrlRaw);
        if (!StringUtils.hasText(imageUrl)) {
            throw new IllegalArgumentException("imageUrl es requerido");
        }

        String url = normalizeUrlOrThrow(imageUrl);
        String previewUrl = StringUtils.hasText(url) ? url : imageUrl;

        String apiUrl = buildMessagesUrl();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "image");
        payload.put("image", Map.of("link", previewUrl));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, buildHeaders());
        try {
            ResponseEntity<Map<String, Object>> response =
                    restTemplate.exchange(apiUrl, HttpMethod.POST, entity, MAP_RESPONSE);
            Map<String, Object> body = response.getBody() == null ? Map.of() : response.getBody();
            return new SendResult(
                    response.getStatusCode().is2xxSuccessful(),
                    to,
                    "image",
                    extractMessageId(body),
                    response.getStatusCode().value(),
                    body
            );
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("Error WhatsApp API (" + e.getStatusCode().value() + "): "
                    + safeTrim(e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("No se pudo conectar con WhatsApp API: " + e.getMessage(), e);
        }
    }

    private void validateEnabledAndConfigured() {
        ConfigStatus status = getConfigStatus();
        if (!status.ready()) {
            throw new IllegalStateException("WhatsApp no esta listo: " + status.message());
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getAccessToken().trim());
        return headers;
    }

    private Map<String, Object> buildPayload(String templateName,
                                             String to,
                                             String languageCode,
                                             List<String> params) {
        Map<String, Object> language = Map.of("code", languageCode);

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", language);

        if (!params.isEmpty()) {
            List<Map<String, Object>> parameters = params.stream()
                    .map(v -> Map.<String, Object>of("type", "text", "text", v))
                    .toList();
            Map<String, Object> bodyComponent = new LinkedHashMap<>();
            bodyComponent.put("type", "body");
            bodyComponent.put("parameters", parameters);
            template.put("components", List.of(bodyComponent));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "template");
        payload.put("template", template);
        return payload;
    }

    private String buildMessagesUrl() {
        String base = safeTrim(props.getApiBaseUrl());
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String version = safeTrim(props.getApiVersion());
        if (version.startsWith("/")) version = version.substring(1);
        String phoneNumberId = safeTrim(props.getPhoneNumberId());
        return base + "/" + version + "/" + phoneNumberId + "/messages";
    }

    private String normalizeTemplateName(String templateNameRaw) {
        String name = safeTrim(templateNameRaw).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("templateName es requerido");
        }
        if (!name.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("templateName invalido. Usa minusculas, numeros y guion_bajo");
        }
        return name;
    }

    private String resolveLanguageCode(String languageCodeRaw) {
        String languageCode = safeTrim(languageCodeRaw);
        if (!StringUtils.hasText(languageCode)) {
            languageCode = safeTrim(props.getDefaultLanguageCode());
        }
        if (!StringUtils.hasText(languageCode)) {
            languageCode = "es_CO";
        }
        if (!languageCode.matches("[a-z]{2}_[A-Z]{2}")) {
            throw new IllegalArgumentException("languageCode invalido. Ejemplo: es_CO");
        }
        return languageCode;
    }

    private List<String> sanitizeParams(List<String> paramsRaw) {
        if (paramsRaw == null || paramsRaw.isEmpty()) return List.of();
        if (paramsRaw.size() > 20) {
            throw new IllegalArgumentException("params excede el maximo de 20 elementos");
        }
        List<String> out = new ArrayList<>(paramsRaw.size());
        for (String p : paramsRaw) {
            String v = safeTrim(p);
            if (v.length() > 1024) {
                throw new IllegalArgumentException("Cada parametro debe tener maximo 1024 caracteres");
            }
            out.add(v);
        }
        return out;
    }

    private String resolveRecipient(String toRaw) {
        String target = safeTrim(toRaw);
        if (!StringUtils.hasText(target)) {
            target = safeTrim(props.getDefaultTo());
        }
        if (!StringUtils.hasText(target)) {
            throw new IllegalArgumentException("Debe enviar 'to' o configurar whatsapp.default-to");
        }

        String normalizedTarget = normalizePhone(target);

        if (props.isRestrictToDefault() && StringUtils.hasText(props.getDefaultTo())) {
            String allowed = normalizePhone(props.getDefaultTo());
            if (!allowed.equals(normalizedTarget)) {
                throw new IllegalArgumentException("El numero destino no esta autorizado");
            }
        }

        return normalizedTarget;
    }

    private String normalizePhone(String rawPhone) {
        String cleaned = safeTrim(rawPhone).replaceAll("[\\s\\-()]", "");
        if (cleaned.startsWith("+")) cleaned = cleaned.substring(1);
        if (!cleaned.matches("\\d{8,15}")) {
            throw new IllegalArgumentException("Numero WhatsApp invalido. Usa formato internacional, por ejemplo 573001112233");
        }
        return cleaned;
    }

    private String extractMessageId(Map<String, Object> body) {
        Object messages = body.get("messages");
        if (!(messages instanceof List<?> list) || list.isEmpty()) return null;
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> map)) return null;
        Object id = map.get("id");
        return id == null ? null : id.toString();
    }

    private String normalizeUrlOrThrow(String url) {
        String trimmed = safeTrim(url);
        if (!StringUtils.hasText(trimmed)) {
            throw new IllegalArgumentException("imageUrl es requerido");
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IllegalArgumentException("imageUrl debe ser una URL http(s)");
        }
        return trimmed;
    }

    private String maskPhone(String rawPhone) {
        if (!StringUtils.hasText(rawPhone)) return null;
        try {
            String normalized = normalizePhone(rawPhone);
            if (normalized.length() <= 4) return "****";
            return "*".repeat(normalized.length() - 4) + normalized.substring(normalized.length() - 4);
        } catch (Exception ignored) {
            return "****";
        }
    }

    private String safeTrim(String v) {
        return v == null ? "" : v.trim();
    }
}
