package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class EpaycoCheckoutContextService {

    public record CheckoutContext(Long flowId,
                                  String buyerName,
                                  String document,
                                  String email,
                                  String phone,
                                  String category,
                                  BigDecimal amount,
                                  String invoice,
                                  String paymentLink) {}

    public static class IncompleteProcessException extends RuntimeException {
        private final Long flowId;
        private final List<String> missingFields;

        public IncompleteProcessException(Long flowId, List<String> missingFields) {
            super("El proceso de matricula esta incompleto y no puede iniciar el pago.");
            this.flowId = flowId;
            this.missingFields = List.copyOf(missingFields);
        }

        public Long getFlowId() {
            return flowId;
        }

        public List<String> getMissingFields() {
            return missingFields;
        }
    }

    private final ChatbotProcesoService chatbotProcesoService;

    public EpaycoCheckoutContextService(ChatbotProcesoService chatbotProcesoService) {
        this.chatbotProcesoService = chatbotProcesoService;
    }

    public CheckoutContext resolveFromFlowId(Long flowId) {
        if (flowId == null || flowId <= 0) {
            throw new IllegalArgumentException("Debes enviar flowId, flow_id o x_extra2.");
        }

        ChatbotMatriculaProceso proceso = chatbotProcesoService.findProcesoById(flowId)
                .orElseThrow(() -> new NoSuchElementException("No existe un proceso de matricula para flowId " + flowId));

        String buyerName = safeTrim(proceso.getNombreCompleto());
        String document = normalizeDocument(proceso.getNumeroDocumento());
        String email = normalizeEmail(proceso.getEmail());
        String phone = normalizePhone(firstNotBlank(proceso.getTelefono(), proceso.getPhone()));
        String category = safeTrim(proceso.getCategoria());
        BigDecimal amount = proceso.getExpectedAmount() == null ? BigDecimal.ZERO : proceso.getExpectedAmount();

        List<String> missingFields = new ArrayList<>();
        if (!StringUtils.hasText(buyerName)) missingFields.add("nombreCompleto");
        if (!StringUtils.hasText(document)) missingFields.add("numeroDocumento");
        if (!StringUtils.hasText(email)) missingFields.add("email");
        if (!StringUtils.hasText(phone)) missingFields.add("telefono");
        if (!missingFields.isEmpty()) {
            throw new IncompleteProcessException(flowId, missingFields);
        }

        String paymentLink = resolvePaymentLink(document);
        String invoice = firstNotBlank(
                queryParam(paymentLink, "x_id_invoice"),
                queryParam(paymentLink, "x_id_factura"),
                queryParam(paymentLink, "invoice"),
                "FLOW-" + flowId
        );

        return new CheckoutContext(
                flowId,
                buyerName,
                document,
                email,
                phone,
                category,
                amount,
                invoice,
                paymentLink
        );
    }

    private String resolvePaymentLink(String document) {
        try {
            ChatbotMatriculaProceso updated = chatbotProcesoService.setPaymentLinkIfMissing(document);
            String paymentLink = safeTrim(updated.getPaymentLink());
            if (StringUtils.hasText(paymentLink)) {
                return paymentLink;
            }
        } catch (Exception ignored) {
            // Fallback below keeps the endpoint usable even if the draft does not persist the link.
        }
        return safeTrim(chatbotProcesoService.getPaymentLink(document));
    }

    private String queryParam(String rawUrl, String key) {
        String url = safeTrim(rawUrl);
        String searchKey = safeTrim(key);
        if (!StringUtils.hasText(url) || !StringUtils.hasText(searchKey)) {
            return "";
        }
        try {
            URI uri = URI.create(url);
            String query = uri.getRawQuery();
            if (!StringUtils.hasText(query)) {
                return "";
            }
            for (String chunk : query.split("&")) {
                if (!StringUtils.hasText(chunk)) {
                    continue;
                }
                String[] parts = chunk.split("=", 2);
                String parsedKey = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                if (!searchKey.equalsIgnoreCase(parsedKey)) {
                    continue;
                }
                String parsedValue = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
                return safeTrim(parsedValue);
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private String normalizeDocument(String raw) {
        String digits = safeTrim(raw).replaceAll("\\D+", "");
        return digits.length() >= 5 ? digits : "";
    }

    private String normalizeEmail(String raw) {
        String email = safeTrim(raw).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(email)) {
            return "";
        }
        return email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ? email : "";
    }

    private String normalizePhone(String raw) {
        String phone = safeTrim(raw).replaceAll("[^0-9+]", "");
        if (!StringUtils.hasText(phone)) {
            return "";
        }
        String digits = phone.replaceAll("\\D+", "");
        if (digits.length() < 8) {
            return "";
        }
        return phone.startsWith("+") ? "+" + digits : digits;
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return safeTrim(value);
            }
        }
        return "";
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
