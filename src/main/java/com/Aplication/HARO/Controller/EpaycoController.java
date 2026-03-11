package com.Aplication.HARO.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Service.ChatbotProcesoService;
import com.Aplication.HARO.Service.EpaycoService;
import com.Aplication.HARO.Service.VerificationService;
import com.Aplication.HARO.Service.WhatsAppTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@CrossOrigin(originPatterns = {
        "https://ceaharo.com",
        "https://www.ceaharo.com",
        "https://*.ceaharo.com"
})
public class EpaycoController {

    private static final Logger log = LoggerFactory.getLogger(EpaycoController.class);

    private final EpaycoService epaycoService;
    private final ChatbotProcesoService chatbotProcesoService;
    private final VerificationService verificationService;
    private final WhatsAppTemplateService waService;
    private final ObjectMapper objectMapper;

    @Value("${chatbot.contract.base-url:}")
    private String contractBaseUrl;

    @Value("${chatbot.contract.ui-url:}")
    private String contractUiUrl;

    @Value("${chatbot.auto-send-contract-on-payment:true}")
    private boolean autoSendContractOnPayment;

    public EpaycoController(EpaycoService epaycoService,
                            ChatbotProcesoService chatbotProcesoService,
                            VerificationService verificationService,
                            WhatsAppTemplateService waService,
                            ObjectMapper objectMapper) {
        this.epaycoService = epaycoService;
        this.chatbotProcesoService = chatbotProcesoService;
        this.verificationService = verificationService;
        this.waService = waService;
        this.objectMapper = objectMapper;
    }

    // Health check rapido para validar conectividad API desde frontend o curl.
    @GetMapping(value = {"/ping", "/epayco/ping"}, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("OK");
    }

    // Endpoint de respuesta (visible al usuario)
    @GetMapping({"/response", "/epayco/response"})
    public ResponseEntity<?> response(@RequestParam(name = "ref_payco", required = false) String refPayco,
                                      @RequestParam(name = "document", required = false) String documentHint,
                                      @RequestParam(name = "x_extra1", required = false) String xExtra1,
                                      @RequestParam(name = "format", required = false) String format,
                                      @RequestHeader(name = "Accept", required = false) String acceptHeader) {
        return userFacingPaymentStatus(refPayco, format, acceptHeader, firstNotBlank(documentHint, xExtra1));
    }

    // Compatibilidad: si por configuracion el retorno llega a /confirmation por GET
    @GetMapping({"/confirmation", "/epayco/confirmation"})
    public ResponseEntity<?> confirmationView(@RequestParam(name = "ref_payco", required = false) String refPayco,
                                              @RequestParam(name = "document", required = false) String documentHint,
                                              @RequestParam(name = "x_extra1", required = false) String xExtra1,
                                              @RequestParam(name = "format", required = false) String format,
                                              @RequestHeader(name = "Accept", required = false) String acceptHeader) {
        return userFacingPaymentStatus(refPayco, format, acceptHeader, firstNotBlank(documentHint, xExtra1));
    }

    /**
     * Endpoint para que respuesta.html confirme estado y dispare continuidad del chatbot.
     * Espera JSON con cualquiera de estos campos:
     * - ref_payco | refPayco
     * - document | x_extra1
     */
    @PostMapping(value = {"/response/sync", "/epayco/response/sync"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> responseSync(@RequestBody Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;

        String refPayco = firstNotBlank(
                safeTrim(asString(safePayload.get("ref_payco"))),
                safeTrim(asString(safePayload.get("refPayco"))),
                safeTrim(asString(safePayload.get("reference")))
        );
        String documentHint = firstNotBlank(
                safeTrim(asString(safePayload.get("document"))),
                safeTrim(asString(safePayload.get("x_extra1")))
        );
        String emailHint = firstNotBlank(
                safeTrim(asString(safePayload.get("email"))),
                safeTrim(asString(safePayload.get("customer_email")))
        );

        String payloadStatus = firstNotBlank(
                safeTrim(asString(safePayload.get("status"))),
                safeTrim(asString(safePayload.get("paymentStatus"))),
                safeTrim(asString(safePayload.get("x_response"))),
                safeTrim(asString(safePayload.get("response")))
        );
        String payloadCodResponse = firstNotBlank(
                safeTrim(asString(safePayload.get("x_cod_response"))),
                safeTrim(asString(safePayload.get("codResponse")))
        );
        String payloadReason = firstNotBlank(
                safeTrim(asString(safePayload.get("x_response_reason_text"))),
                safeTrim(asString(safePayload.get("reason"))),
                safeTrim(asString(safePayload.get("message")))
        );
        boolean approvedByPayload = asBoolean(safePayload.get("paymentApproved"), false)
                || resolvePaymentUserStatus(payloadStatus, payloadCodResponse, payloadReason) == PaymentUserStatus.APPROVED;

        if (!StringUtils.hasText(refPayco) && !approvedByPayload) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "ref_payco es requerido o debes enviar estado aprobado en payload"
            ));
        }

        Map<String, Object> summary;
        String statusSource = "epayco";
        if (StringUtils.hasText(refPayco)) {
            try {
                String rawData = epaycoService.fetchTransactionByRefPayco(refPayco);
                summary = new LinkedHashMap<>(buildUserPaymentSummary(refPayco, rawData, documentHint));
            } catch (Exception ex) {
                if (!approvedByPayload) {
                    log.error("responseSync no pudo consultar ePayco ref={}: {}", refPayco, ex.getMessage(), ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                            "status", "ERROR",
                            "reference", refPayco,
                            "message", "No fue posible consultar ePayco"
                    ));
                }
                log.warn("responseSync usara payload aprobado por falla temporal consultando ePayco. ref={} err={}",
                        refPayco, ex.getMessage());
                summary = buildSyncSummaryFromPayload(refPayco, safePayload, documentHint, emailHint);
                statusSource = "payload_fallback";
            }
        } else {
            summary = buildSyncSummaryFromPayload(refPayco, safePayload, documentHint, emailHint);
            statusSource = "payload_only";
        }

        String status = safeTrim(asString(summary.get("status")));
        if (!"APPROVED".equalsIgnoreCase(status) && approvedByPayload) {
            status = PaymentUserStatus.APPROVED.code;
            summary.put("status", status);
            summary.putIfAbsent("title", PaymentUserStatus.APPROVED.title);
            summary.putIfAbsent("nextStep", PaymentUserStatus.APPROVED.nextStep);
            if (!StringUtils.hasText(safeTrim(asString(summary.get("message"))))) {
                summary.put("message", PaymentUserStatus.APPROVED.defaultMessage);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>(summary);
        boolean chatbotSynced = false;
        if ("APPROVED".equalsIgnoreCase(status)) {
            chatbotSynced = syncApprovedPaymentToFlow(summary, safePayload, documentHint, emailHint);
        }
        out.put("chatbotSynced", chatbotSynced);
        out.put("approvedByPayload", approvedByPayload);
        out.put("syncSource", "response_page");
        out.put("statusSource", statusSource);
        out.put("syncAt", ZonedDateTime.now(ZoneId.of("America/Bogota"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")));
        return ResponseEntity.ok(out);
    }

    private boolean syncApprovedPaymentToFlow(Map<String, Object> summary,
                                             Map<String, Object> payload,
                                             String documentHint,
                                             String emailHint) {
        if (summary == null) {
            return false;
        }

        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        boolean notifyContractLinkByChatbot = asBoolean(safePayload.get("notifyContractLinkByChatbot"), true);
        boolean forceResendContractLink = asBoolean(safePayload.get("forceResendContractLink"), true);
        String chatbotMessageText = safeTrim(asString(safePayload.get("chatbotMessageText")));
        String chatbotMessageTemplate = safeTrim(asString(safePayload.get("chatbotMessageTemplate")));

        BigDecimal amount = parseAmountOrNull(firstNotBlank(
                safeTrim(asString(summary.get("amount"))),
                safeTrim(asString(safePayload.get("x_amount")))
        ));

        String documento = firstNotBlank(
                safeTrim(asString(summary.get("document"))),
                safeTrim(asString(safePayload.get("document"))),
                safeTrim(asString(safePayload.get("x_extra1"))),
                safeTrim(documentHint)
        );

        if (StringUtils.hasText(documento)) {
            try {
                processApprovedPayment(
                        documento,
                        amount,
                        notifyContractLinkByChatbot,
                        forceResendContractLink,
                        chatbotMessageText,
                        chatbotMessageTemplate
                );
                return true;
            } catch (Exception ex) {
                log.error("No fue posible sincronizar pago aprobado por documento doc={}: {}", documento, ex.getMessage(), ex);
                return false;
            }
        }

        String email = firstNotBlank(
                safeTrim(asString(summary.get("email"))),
                safeTrim(asString(safePayload.get("email"))),
                safeTrim(asString(safePayload.get("customer_email"))),
                safeTrim(emailHint)
        );
        if (!StringUtils.hasText(email)) {
            return false;
        }

        try {
            Optional<ChatbotMatriculaProceso> proceso = chatbotProcesoService.findLatestProcesoByEmail(email);
            if (proceso.isEmpty()) {
                log.info("No se pudo resolver proceso por email para sync de pago. email={}", email);
                return false;
            }

            String fromEmailDoc = safeTrim(proceso.get().getNumeroDocumento());
            if (!StringUtils.hasText(fromEmailDoc)) {
                return false;
            }

            processApprovedPayment(
                    fromEmailDoc,
                    amount,
                    notifyContractLinkByChatbot,
                    forceResendContractLink,
                    chatbotMessageText,
                    chatbotMessageTemplate
            );
            summary.put("document", fromEmailDoc);
            return true;
        } catch (Exception ex) {
            log.error("No fue posible sincronizar pago aprobado por email. email={}: {}", email, ex.getMessage(), ex);
            return false;
        }
    }

    private ResponseEntity<?> userFacingPaymentStatus(String refPaycoRaw,
                                                      String formatRaw,
                                                      String acceptHeaderRaw,
                                                      String documentHintRaw) {
        boolean wantsHtml = wantsHtml(formatRaw, acceptHeaderRaw);
        String refPayco = safeTrim(refPaycoRaw);
        String documentHint = safeTrim(documentHintRaw);
        if (!StringUtils.hasText(refPayco)) {
            Map<String, Object> payload = Map.of(
                    "status", "ERROR",
                    "title", "No se pudo validar el pago",
                    "message", "Falta el parametro ref_payco para consultar la transaccion.",
                    "nextStep", "Vuelve a intentar el pago o contacta al asesor para validar manualmente."
            );
            return userFacingResponse(HttpStatus.BAD_REQUEST, payload, wantsHtml);
        }

        String rawData;
        try {
            rawData = epaycoService.fetchTransactionByRefPayco(refPayco);
        } catch (Exception ex) {
            log.error("No se pudo consultar transaccion ePayco ref={}: {}", refPayco, ex.getMessage(), ex);
            Map<String, Object> payload = Map.of(
                    "status", "ERROR",
                    "title", "No pudimos consultar tu transaccion",
                    "message", "Ocurrio un problema temporal al validar el pago.",
                    "reference", refPayco,
                    "nextStep", "Espera unos minutos e intenta de nuevo con el mismo enlace de confirmacion."
            );
            return userFacingResponse(HttpStatus.INTERNAL_SERVER_ERROR, payload, wantsHtml);
        }

        Map<String, Object> payload = buildUserPaymentSummary(refPayco, rawData, documentHint);
        return userFacingResponse(HttpStatus.OK, payload, wantsHtml);
    }

    private ResponseEntity<?> userFacingResponse(HttpStatus status,
                                                 Map<String, Object> payload,
                                                 boolean wantsHtml) {
        if (!wantsHtml) {
            return ResponseEntity.status(status).body(payload);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_HTML)
                .body(renderPaymentReceiptHtml(payload));
    }

    private Map<String, Object> buildUserPaymentSummary(String refPayco, String rawData, String documentHintRaw) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawData == null ? "{}" : rawData);
        } catch (Exception ex) {
            log.warn("No se pudo parsear respuesta ePayco para ref={}: {}", refPayco, ex.getMessage());
            return Map.of(
                    "status", "UNKNOWN",
                    "title", "Pago en validacion",
                    "message", "Recibimos tu referencia, pero no fue posible leer el detalle de la pasarela.",
                    "reference", refPayco,
                    "nextStep", "Si en 10 minutos no ves actualizacion, contacta a soporte con esta referencia."
            );
        }

        JsonNode tx = resolveTransactionNode(root);
        String xCodResponse = firstNotBlank(readField(tx, "x_cod_response"), readField(root, "x_cod_response"));
        String xResponse = firstNotBlank(readField(tx, "x_response"), readField(root, "x_response"));
        String xReason = firstNotBlank(
                readField(tx, "x_response_reason_text"),
                readField(root, "x_response_reason_text"),
                readField(root, "text_response")
        );
        String xAmount = firstNotBlank(readField(tx, "x_amount"), readField(root, "x_amount"));
        String xCurrency = firstNotBlank(readField(tx, "x_currency_code"), readField(root, "x_currency_code"));
        String xTransactionId = firstNotBlank(readField(tx, "x_transaction_id"), readField(root, "x_transaction_id"));
        String xInvoice = firstNotBlank(readField(tx, "x_id_invoice"), readField(root, "x_id_invoice"));
        String xDocumento = firstNotBlank(readField(tx, "x_extra1"), readField(root, "x_extra1"), documentHintRaw);
        String xEmail = firstNotBlank(
                readField(tx, "x_customer_email"),
                readField(tx, "customer_email"),
                readField(root, "customer_email"),
                readField(root, "x_customer_email")
        );
        String xRefPayco = firstNotBlank(readField(tx, "x_ref_payco"), readField(root, "x_ref_payco"), refPayco);

        PaymentUserStatus status = resolvePaymentUserStatus(xResponse, xCodResponse, xReason);

        Optional<ChatbotMatriculaProceso> procesoOpt = Optional.empty();
        if (StringUtils.hasText(xDocumento)) {
            procesoOpt = chatbotProcesoService.findProcesoByDocumento(xDocumento);
            if (status != PaymentUserStatus.APPROVED) {
                PaymentUserStatus localStatus = resolveLocalProcesoStatus(procesoOpt.orElse(null));
                if (localStatus == PaymentUserStatus.APPROVED
                        || localStatus == PaymentUserStatus.CANCELLED
                        || localStatus == PaymentUserStatus.REJECTED) {
                    status = localStatus;
                }
            }
        }

        if (status == PaymentUserStatus.APPROVED && StringUtils.hasText(xDocumento)) {
            // Refuerza continuidad del flujo aunque el webhook se retrase.
            processApprovedPayment(xDocumento, xAmount);
            procesoOpt = chatbotProcesoService.findProcesoByDocumento(xDocumento);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", status.code);
        out.put("title", status.title);
        out.put("message", status.message(xReason));
        out.put("nextStep", status.nextStep);
        out.put("reference", xRefPayco);

        if (StringUtils.hasText(xTransactionId)) out.put("transactionId", xTransactionId);
        if (StringUtils.hasText(xInvoice)) out.put("invoice", xInvoice);
        if (StringUtils.hasText(xAmount)) out.put("amount", xAmount);
        if (StringUtils.hasText(xCurrency)) out.put("currency", xCurrency);
        if (StringUtils.hasText(xDocumento)) out.put("document", xDocumento);
        if (StringUtils.hasText(xEmail)) out.put("email", xEmail);

        Map<String, String> gateway = new LinkedHashMap<>();
        if (StringUtils.hasText(xCodResponse)) gateway.put("codResponse", xCodResponse);
        if (StringUtils.hasText(xResponse)) gateway.put("response", xResponse);
        if (StringUtils.hasText(xReason)) gateway.put("reason", xReason);
        if (!gateway.isEmpty()) out.put("gateway", gateway);

        if (procesoOpt.isPresent()) {
            ChatbotMatriculaProceso proceso = procesoOpt.get();
            String flowStatus = safeTrim(proceso.getFlowStatus());
            String paymentStatus = safeTrim(proceso.getPaymentStatus());
            if (StringUtils.hasText(flowStatus)) out.put("flowStatus", flowStatus);
            if (StringUtils.hasText(paymentStatus)) out.put("paymentStatus", paymentStatus);

            if (status == PaymentUserStatus.APPROVED) {
                String contractLink = safeTrim(proceso.getContractLink());
                if (StringUtils.hasText(contractLink)) {
                    out.put("contractLink", contractLink);
                    out.put("nextStep",
                            "Tu pago fue aprobado. Continua con la contratacion en este enlace: " + contractLink);
                }
            }
        }

        return out;
    }

    private Map<String, Object> buildSyncSummaryFromPayload(String refPaycoRaw,
                                                            Map<String, Object> payload,
                                                            String documentHintRaw,
                                                            String emailHintRaw) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        Map<String, Object> out = new LinkedHashMap<>();

        String refPayco = firstNotBlank(
                safeTrim(refPaycoRaw),
                safeTrim(asString(safePayload.get("ref_payco"))),
                safeTrim(asString(safePayload.get("refPayco"))),
                safeTrim(asString(safePayload.get("reference")))
        );
        String xCodResponse = firstNotBlank(
                safeTrim(asString(safePayload.get("x_cod_response"))),
                safeTrim(asString(safePayload.get("codResponse")))
        );
        String xResponse = firstNotBlank(
                safeTrim(asString(safePayload.get("x_response"))),
                safeTrim(asString(safePayload.get("response"))),
                safeTrim(asString(safePayload.get("status"))),
                safeTrim(asString(safePayload.get("paymentStatus")))
        );
        String xReason = firstNotBlank(
                safeTrim(asString(safePayload.get("x_response_reason_text"))),
                safeTrim(asString(safePayload.get("reason"))),
                safeTrim(asString(safePayload.get("message")))
        );
        String xAmount = firstNotBlank(
                safeTrim(asString(safePayload.get("x_amount"))),
                safeTrim(asString(safePayload.get("amount")))
        );
        String xCurrency = firstNotBlank(
                safeTrim(asString(safePayload.get("x_currency_code"))),
                safeTrim(asString(safePayload.get("currency")))
        );
        String xTransactionId = firstNotBlank(
                safeTrim(asString(safePayload.get("x_transaction_id"))),
                safeTrim(asString(safePayload.get("transactionId"))),
                safeTrim(asString(safePayload.get("transaction_id")))
        );
        String xInvoice = firstNotBlank(
                safeTrim(asString(safePayload.get("x_id_invoice"))),
                safeTrim(asString(safePayload.get("invoice")))
        );
        String xDocumento = firstNotBlank(
                safeTrim(asString(safePayload.get("x_extra1"))),
                safeTrim(asString(safePayload.get("document"))),
                safeTrim(documentHintRaw)
        );
        String xEmail = firstNotBlank(
                safeTrim(asString(safePayload.get("customer_email"))),
                safeTrim(asString(safePayload.get("email"))),
                safeTrim(emailHintRaw)
        );

        PaymentUserStatus status = resolvePaymentUserStatus(xResponse, xCodResponse, xReason);
        if (asBoolean(safePayload.get("paymentApproved"), false)) {
            status = PaymentUserStatus.APPROVED;
        }

        out.put("status", status.code);
        out.put("title", status.title);
        out.put("message", status.message(xReason));
        out.put("nextStep", status.nextStep);

        if (StringUtils.hasText(refPayco)) out.put("reference", refPayco);
        if (StringUtils.hasText(xTransactionId)) out.put("transactionId", xTransactionId);
        if (StringUtils.hasText(xInvoice)) out.put("invoice", xInvoice);
        if (StringUtils.hasText(xAmount)) out.put("amount", xAmount);
        if (StringUtils.hasText(xCurrency)) out.put("currency", xCurrency);
        if (StringUtils.hasText(xDocumento)) out.put("document", xDocumento);
        if (StringUtils.hasText(xEmail)) out.put("email", xEmail);

        Map<String, String> gateway = new LinkedHashMap<>();
        if (StringUtils.hasText(xCodResponse)) gateway.put("codResponse", xCodResponse);
        if (StringUtils.hasText(xResponse)) gateway.put("response", xResponse);
        if (StringUtils.hasText(xReason)) gateway.put("reason", xReason);
        if (!gateway.isEmpty()) out.put("gateway", gateway);

        return out;
    }

    private PaymentUserStatus resolveLocalProcesoStatus(ChatbotMatriculaProceso proceso) {
        if (proceso == null) return PaymentUserStatus.UNKNOWN;
        String local = safeTrim(proceso.getPaymentStatus()).toUpperCase(Locale.ROOT);
        return switch (local) {
            case "APPROVED" -> PaymentUserStatus.APPROVED;
            case "CANCELLED" -> PaymentUserStatus.CANCELLED;
            case "REJECTED" -> PaymentUserStatus.REJECTED;
            case "PENDING" -> PaymentUserStatus.PENDING;
            default -> PaymentUserStatus.UNKNOWN;
        };
    }

    private JsonNode resolveTransactionNode(JsonNode root) {
        if (root == null || root.isNull()) return null;
        JsonNode data = root.path("data");
        if (data.isArray() && data.size() > 0) {
            JsonNode first = data.get(0);
            if (first != null && first.isObject()) {
                return first;
            }
        }
        if (data.isObject()) {
            return data;
        }
        return root;
    }

    private String readField(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) return "";
        return safeTrim(value.asText(""));
    }

    private String firstNotBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return safeTrim(value);
            }
        }
        return "";
    }

    private PaymentUserStatus resolvePaymentUserStatus(String estadoRaw, String codRaw) {
        return resolvePaymentUserStatus(estadoRaw, codRaw, "");
    }

    private PaymentUserStatus resolvePaymentUserStatus(String estadoRaw, String codRaw, String reasonRaw) {
        if (isApproved(estadoRaw, codRaw)) {
            return PaymentUserStatus.APPROVED;
        }
        if (isCancelled(estadoRaw, codRaw)) {
            return PaymentUserStatus.CANCELLED;
        }
        if (isRejected(estadoRaw, codRaw)) {
            return PaymentUserStatus.REJECTED;
        }
        if (isPending(estadoRaw, codRaw, reasonRaw)) {
            return PaymentUserStatus.PENDING;
        }
        return PaymentUserStatus.UNKNOWN;
    }

    private boolean wantsHtml(String formatRaw, String acceptHeaderRaw) {
        String format = safeTrim(formatRaw).toLowerCase(Locale.ROOT);
        if ("json".equals(format)) return false;
        if ("html".equals(format)) return true;
        String accept = safeTrim(acceptHeaderRaw).toLowerCase(Locale.ROOT);
        return accept.contains("text/html");
    }

    private String renderPaymentReceiptHtml(Map<String, Object> payload) {
        String status = value(payload, "status");
        String title = value(payload, "title");
        String message = value(payload, "message");
        String nextStep = value(payload, "nextStep");
        String contractLink = value(payload, "contractLink");
        String reference = value(payload, "reference");
        String transactionId = value(payload, "transactionId");
        String invoice = value(payload, "invoice");
        String amount = value(payload, "amount");
        String currency = value(payload, "currency");
        String document = value(payload, "document");
        String reason = gatewayValue(payload, "reason");
        String queryDate = ZonedDateTime.now(ZoneId.of("America/Bogota"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));

        String amountFriendly = formatAmount(amount, currency);
        String badgeClass = "badge-" + status.toLowerCase(Locale.ROOT);

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"es\"><head><meta charset=\"UTF-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        html.append("<title>Comprobante de pago</title>");
        html.append("<style>");
        html.append("body{margin:0;background:#f4f7fb;color:#18212f;font-family:Segoe UI,Arial,sans-serif;}");
        html.append(".wrap{max-width:760px;margin:24px auto;padding:0 16px;}");
        html.append(".card{background:#fff;border-radius:14px;box-shadow:0 10px 25px rgba(15,23,42,.08);overflow:hidden;}");
        html.append(".head{background:linear-gradient(120deg,#0d6efd,#0aa3a3);color:#fff;padding:24px;}");
        html.append(".head h1{margin:0;font-size:24px;} .head p{margin:8px 0 0;opacity:.95;}");
        html.append(".body{padding:24px;} .grid{display:grid;grid-template-columns:1fr 1fr;gap:12px 18px;}");
        html.append(".row{border-bottom:1px dashed #e7edf5;padding:8px 0;} .k{font-size:12px;color:#5b6b82;}");
        html.append(".v{font-size:15px;font-weight:600;word-break:break-word;}");
        html.append(".full{grid-column:1/-1;} .box{margin-top:16px;background:#f8fbff;border-radius:10px;padding:14px;}");
        html.append(".badge{display:inline-block;padding:7px 12px;border-radius:999px;font-weight:700;font-size:12px;}");
        html.append(".badge-approved{background:#d1fae5;color:#065f46;}");
        html.append(".badge-pending{background:#fef3c7;color:#92400e;}");
        html.append(".badge-cancelled{background:#fde68a;color:#78350f;}");
        html.append(".badge-rejected{background:#fee2e2;color:#991b1b;}");
        html.append(".badge-unknown,.badge-error{background:#e5e7eb;color:#1f2937;}");
        html.append(".foot{padding:16px 24px;background:#f8fafc;color:#475569;font-size:12px;}");
        html.append("@media(max-width:680px){.grid{grid-template-columns:1fr;}}");
        html.append("</style></head><body><div class=\"wrap\"><div class=\"card\">");
        html.append("<div class=\"head\"><h1>CEA HARO</h1><p>Comprobante de transaccion</p></div>");
        html.append("<div class=\"body\">");
        html.append("<div class=\"row\"><span class=\"badge ").append(escapeHtml(badgeClass)).append("\">")
                .append(escapeHtml(emptyTo(status, "UNKNOWN"))).append("</span></div>");
        html.append("<div class=\"row full\"><div class=\"k\">Estado</div><div class=\"v\">")
                .append(escapeHtml(title)).append("</div></div>");
        html.append("<div class=\"row full\"><div class=\"k\">Mensaje</div><div class=\"v\">")
                .append(escapeHtml(message)).append("</div></div>");
        html.append("<div class=\"grid\">");
        addHtmlRow(html, "Referencia ePayco", reference);
        addHtmlRow(html, "ID transaccion", transactionId);
        addHtmlRow(html, "Factura", invoice);
        addHtmlRow(html, "Documento", document);
        addHtmlRow(html, "Valor pagado", amountFriendly);
        addHtmlRow(html, "Fecha de consulta", queryDate);
        html.append("</div>");
        if (StringUtils.hasText(reason)) {
            html.append("<div class=\"box\"><div class=\"k\">Detalle pasarela</div><div class=\"v\">")
                    .append(escapeHtml(reason)).append("</div></div>");
        }
        html.append("<div class=\"box\"><div class=\"k\">Siguiente paso</div><div class=\"v\">")
                .append(escapeHtml(nextStep)).append("</div></div>");
        if (StringUtils.hasText(contractLink)) {
            html.append("<div class=\"box\"><div class=\"k\">Contratacion</div><div class=\"v\">")
                    .append("<a href=\"").append(escapeHtml(contractLink))
                    .append("\" target=\"_blank\" rel=\"noopener noreferrer\">Abrir enlace de contrato</a>")
                    .append("</div></div>");
        }
        html.append("</div><div class=\"foot\">Conserva esta informacion para soporte y seguimiento.</div>");
        html.append("</div></div></body></html>");
        return html.toString();
    }

    private void addHtmlRow(StringBuilder html, String label, String value) {
        if (!StringUtils.hasText(value)) return;
        html.append("<div class=\"row\"><div class=\"k\">").append(escapeHtml(label))
                .append("</div><div class=\"v\">").append(escapeHtml(value)).append("</div></div>");
    }

    private String formatAmount(String amountRaw, String currencyRaw) {
        String amount = safeTrim(amountRaw);
        if (amount.isBlank()) return "";
        String currencyCode = safeTrim(currencyRaw).toUpperCase(Locale.ROOT);
        try {
            BigDecimal value = new BigDecimal(amount);
            NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
            if (!currencyCode.isBlank()) {
                nf.setCurrency(Currency.getInstance(currencyCode));
            }
            return nf.format(value);
        } catch (Exception ignored) {
            if (currencyCode.isBlank()) return amount;
            return amount + " " + currencyCode;
        }
    }

    private String gatewayValue(Map<String, Object> payload, String key) {
        Object gatewayObj = payload == null ? null : payload.get("gateway");
        if (!(gatewayObj instanceof Map<?, ?> gateway)) return "";
        Object value = gateway.get(key);
        return value == null ? "" : safeTrim(String.valueOf(value));
    }

    private String value(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        return value == null ? "" : safeTrim(String.valueOf(value));
    }

    private String emptyTo(String value, String fallback) {
        String out = safeTrim(value);
        return out.isBlank() ? fallback : out;
    }

    private String escapeHtml(String input) {
        String value = input == null ? "" : input;
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private enum PaymentUserStatus {
        APPROVED(
                "APPROVED",
                "Pago aprobado",
                "Recibimos tu pago correctamente.",
                "Te enviaremos el enlace de contratos. Si no lo recibes en pocos minutos, escribe MENU en WhatsApp."
        ),
        PENDING(
                "PENDING",
                "Pago pendiente",
                "Tu transaccion esta en validacion por la entidad financiera.",
                "Espera unos minutos y vuelve a consultar con esta misma referencia."
        ),
        CANCELLED(
                "CANCELLED",
                "Pago cancelado",
                "La transaccion no se completo o fue cancelada.",
                "Puedes iniciar nuevamente el pago desde el enlace compartido por WhatsApp."
        ),
        REJECTED(
                "REJECTED",
                "Pago no aprobado",
                "La entidad rechazo la transaccion.",
                "Intenta nuevamente con otro medio de pago o contacta al asesor."
        ),
        UNKNOWN(
                "UNKNOWN",
                "Estado de pago no confirmado",
                "Aun no tenemos un estado final para esta transaccion.",
                "Conserva esta referencia y vuelve a validar en unos minutos."
        );

        final String code;
        final String title;
        final String defaultMessage;
        final String nextStep;

        PaymentUserStatus(String code, String title, String defaultMessage, String nextStep) {
            this.code = code;
            this.title = title;
            this.defaultMessage = defaultMessage;
            this.nextStep = nextStep;
        }

        String message(String reason) {
            String detail = reason == null ? "" : reason.trim();
            if (detail.isBlank()) {
                return defaultMessage;
            }
            return defaultMessage + " Detalle: " + detail;
        }
    }

    // Endpoint temporal para checkout session (si aplica)
    @CrossOrigin(origins = {"http://127.0.0.1:8081", "http://localhost:8081"})
    @PostMapping(value = {"/session", "/epayco/session"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSession(@RequestBody java.util.Map<String, Object> payload) {
        Object items = payload.get("items");
        if (items == null) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "items vacio"));
        }
        return ResponseEntity.ok(java.util.Map.of("sessionId", "DUMMY_SESSION_ID"));
    }

    // URL de confirmacion (Webhook ePayco)
    @PostMapping(value = {"/confirmation", "/epayco/confirmation"}, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
public ResponseEntity<?> confirmation(@RequestBody MultiValueMap<String, String> form) {

    String xRefPayco = form.getFirst("x_ref_payco");
    String xTransactionId = form.getFirst("x_transaction_id");
    String xAmount = form.getFirst("x_amount");
    String xCurrencyCode = form.getFirst("x_currency_code");
    String xCodResponse = form.getFirst("x_cod_response");
    String xSignature = form.getFirst("x_signature");
    String xDocumento = form.getFirst("x_extra1");
    String estado = form.getFirst("x_response");

    boolean signatureOk =
            epaycoService.isValidSignature(xRefPayco, xTransactionId, xAmount, xCurrencyCode, xSignature);

    if (!signatureOk) {

        log.warn("Firma inválida en confirmación ePayco ref={}", xRefPayco);
        return ResponseEntity.badRequest().build();
    }

    if (isApproved(estado, xCodResponse)) {

        log.info("💰 Pago aprobado doc={} ref={}", xDocumento, xRefPayco);

        processApprovedPayment(xDocumento, xAmount);
    }

    return ResponseEntity.ok(Map.of("status", "ok"));
}

    private void processApprovedPayment(String documentoRaw, String amountRaw) {
        processApprovedPayment(documentoRaw, parseAmountOrNull(amountRaw), true, false, "", "");
    }

    private void processApprovedPayment(String documentoRaw, BigDecimal amount) {
        processApprovedPayment(documentoRaw, amount, true, false, "", "");
    }

    private void processApprovedPayment(String documentoRaw,
                                        BigDecimal amount,
                                        boolean notifyContractLinkByChatbot,
                                        boolean forceResendContractLink,
                                        String customMessageTextRaw,
                                        String customMessageTemplateRaw) {
        String documento = safeTrim(documentoRaw);
        if (!StringUtils.hasText(documento)) {
            log.warn("Pago aprobado sin x_extra1 (documento). No se sincroniza chatbot.");
            return;
        }

        ChatbotMatriculaProceso proceso;
        try {
            proceso = chatbotProcesoService.markPaymentApproved(documento, amount);
        } catch (Exception ex) {
            log.error("No se pudo marcar pago aprobado doc={}: {}", documento, ex.getMessage(), ex);
            return;
        }

        if (!autoSendContractOnPayment || !notifyContractLinkByChatbot) {
            return;
        }

        Optional<ChatbotMatriculaProceso> currentOpt = chatbotProcesoService.findProcesoByDocumento(documento);
        ChatbotMatriculaProceso current = currentOpt.orElse(proceso);

        String flowStatus = safeTrim(current.getFlowStatus()).toUpperCase(Locale.ROOT);
        if ("CONTRACT_LINK_SENT".equals(flowStatus)
                && StringUtils.hasText(current.getContractLink())
                && !forceResendContractLink) {
            log.info("Contrato ya enviado para doc={}. Se evita reenvio.", documento);
            return;
        }

        String contractLink = safeTrim(current.getContractLink());
        if (!StringUtils.hasText(contractLink)) {
            try {
                VerificationService.ContractLinkResult out =
                        verificationService.createContractVerificationLink(current.getEmail(), contractBaseUrl);
                contractLink = buildContractUserLink(out);
                chatbotProcesoService.markContractLinkSent(documento, contractLink);
            } catch (Exception ex) {
                log.error("Pago aprobado doc={} pero no se pudo generar link de contrato: {}", documento, ex.getMessage(), ex);
                return;
            }
        }

        String phone = safeTrim(current.getPhone());
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(contractLink)) {
            log.warn("Pago aprobado doc={} sin telefono/link para notificar. phonePresent={} linkPresent={}",
                    documento, StringUtils.hasText(phone), StringUtils.hasText(contractLink));
            return;
        }

        if (!waService.getConfigStatus().ready()) {
            log.warn("Pago aprobado doc={} pero WhatsApp no esta listo: {}", documento, waService.getConfigStatus().message());
            return;
        }

        try {
            String msg = buildApprovedPaymentMessage(contractLink, customMessageTextRaw, customMessageTemplateRaw);
            waService.sendTextMessage(phone, msg);
            log.info("Enlace de contrato enviado por WhatsApp doc={} to={}", documento, maskPhone(phone));
        } catch (Exception ex) {
            log.error("No se pudo enviar enlace de contrato por WhatsApp doc={} to={}: {}",
                    documento, maskPhone(phone), ex.getMessage(), ex);
        }
    }

    private void processNonApprovedPayment(String documentoRaw,
                                           String xRefPayco,
                                           String xCodResponse,
                                           String estado,
                                           String reason,
                                           PaymentUserStatus status) {
        String documento = safeTrim(documentoRaw);
        if (!StringUtils.hasText(documento)) {
            log.warn("Pago no aprobado sin x_extra1 (documento). ref={} estado={} cod={}", xRefPayco, estado, xCodResponse);
            return;
        }

        try {
            if (status == PaymentUserStatus.CANCELLED) {
                chatbotProcesoService.markPaymentCancelled(documento);
            } else if (status == PaymentUserStatus.REJECTED) {
                chatbotProcesoService.markPaymentRejected(documento);
            } else if (status == PaymentUserStatus.PENDING) {
                chatbotProcesoService.markPaymentPending(documento);
            }
        } catch (Exception ex) {
            log.error("No se pudo actualizar estado de pago no aprobado doc={} status={} ref={}: {}",
                    documento, status.code, xRefPayco, ex.getMessage(), ex);
            return;
        }

        if (status == PaymentUserStatus.PENDING) {
            return;
        }

        Optional<ChatbotMatriculaProceso> currentOpt = chatbotProcesoService.findProcesoByDocumento(documento);
        if (currentOpt.isEmpty()) {
            log.warn("No existe proceso de matricula para notificar pago no aprobado doc={} ref={}", documento, xRefPayco);
            return;
        }

        ChatbotMatriculaProceso proceso = currentOpt.get();
        String phone = safeTrim(proceso.getPhone());
        if (!StringUtils.hasText(phone)) {
            log.warn("Pago no aprobado doc={} sin telefono para notificar. ref={}", documento, xRefPayco);
            return;
        }

        if (!waService.getConfigStatus().ready()) {
            log.warn("Pago no aprobado doc={} pero WhatsApp no esta listo: {}", documento, waService.getConfigStatus().message());
            return;
        }

        String detail = safeTrim(reason);
        String paymentLink = safeTrim(proceso.getPaymentLink());
        if (!StringUtils.hasText(paymentLink)) {
            try {
                paymentLink = safeTrim(chatbotProcesoService.getPaymentLink(documento));
            } catch (Exception ignored) {
                // link opcional para reintento
            }
        }

        String headline = status == PaymentUserStatus.CANCELLED
                ? "Tu pago fue cancelado o no finalizado."
                : "Tu pago no fue aprobado.";

        StringBuilder msg = new StringBuilder();
        msg.append(headline);
        if (StringUtils.hasText(detail)) {
            msg.append("\nDetalle: ").append(detail);
        }
        if (StringUtils.hasText(paymentLink)) {
            msg.append("\n\nPuedes reintentar aqui:\n").append(paymentLink);
        }
        msg.append("\n\nSi necesitas ayuda, escribe ASESOR.");

        try {
            waService.sendTextMessage(phone, msg.toString());
            log.info("Notificacion de pago no aprobado enviada doc={} to={} status={} ref={}",
                    documento, maskPhone(phone), status.code, xRefPayco);
        } catch (Exception ex) {
            log.error("No se pudo enviar notificacion de pago no aprobado doc={} to={} status={} ref={}: {}",
                    documento, maskPhone(phone), status.code, xRefPayco, ex.getMessage(), ex);
        }
    }

    private boolean isApproved(String estadoRaw, String codRaw) {
        String estado = safeTrim(estadoRaw).toLowerCase(Locale.ROOT);
        String cod = safeTrim(codRaw);
        return "1".equals(cod)
                || estado.contains("acept")
                || estado.contains("aprob")
                || estado.contains("pagad")
                || estado.contains("exito");
    }

    private boolean isCancelled(String estadoRaw, String codRaw) {
        String estado = safeTrim(estadoRaw).toLowerCase(Locale.ROOT);
        String cod = safeTrim(codRaw);
        return "4".equals(cod)
                || estado.contains("cancel")
                || estado.contains("fallid")
                || estado.contains("abandon")
                || estado.contains("anulad");
    }

    private boolean isRejected(String estadoRaw, String codRaw) {
        String estado = safeTrim(estadoRaw).toLowerCase(Locale.ROOT);
        String cod = safeTrim(codRaw);
        return "2".equals(cod) || estado.contains("rech") || estado.contains("declin");
    }

    private boolean isPending(String estadoRaw, String codRaw) {
        return isPending(estadoRaw, codRaw, "");
    }

    private boolean isPending(String estadoRaw, String codRaw, String reasonRaw) {
        String estado = safeTrim(estadoRaw).toLowerCase(Locale.ROOT);
        String cod = safeTrim(codRaw);
        String reason = safeTrim(reasonRaw).toLowerCase(Locale.ROOT);
        return "3".equals(cod)
                || estado.contains("pend")
                || estado.contains("validad")
                || reason.contains("validado con nuestro sistema")
                || reason.contains("en validacion");
    }

    private BigDecimal parseAmountOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildApprovedPaymentMessage(String contractLinkRaw,
                                               String customMessageTextRaw,
                                               String customMessageTemplateRaw) {
        String contractLink = safeTrim(contractLinkRaw);
        if (!StringUtils.hasText(contractLink)) {
            return "Pago recibido.";
        }

        String template = safeTrim(customMessageTemplateRaw);
        if (StringUtils.hasText(template)) {
            return injectContractUrl(template, contractLink);
        }

        String text = safeTrim(customMessageTextRaw);
        if (StringUtils.hasText(text)) {
            String withInlinePlaceholder = injectContractUrl(text, contractLink);
            if (!withInlinePlaceholder.equals(text)) {
                return withInlinePlaceholder;
            }
            return text + ": " + contractLink;
        }

        return "Pago recibido sigue con la contratacion con esta url: " + contractLink;
    }

    private String injectContractUrl(String templateRaw, String contractLink) {
        String template = safeTrim(templateRaw);
        if (!StringUtils.hasText(template)) {
            return template;
        }
        return template
                .replace("{{contract_url}}", contractLink)
                .replace("{contract_url}", contractLink)
                .replace("${contract_url}", contractLink);
    }

    private String buildContractUserLink(VerificationService.ContractLinkResult out) {
        if (out == null) return "";

        String ui = safeTrim(contractUiUrl);
        if (!StringUtils.hasText(ui)) {
            return safeTrim(out.url());
        }

        String link = appendQueryParam(ui, "email", out.email());
        link = appendQueryParam(link, "code", out.code());
        if (StringUtils.hasText(contractBaseUrl)) {
            link = appendQueryParam(link, "apiBase", safeTrim(contractBaseUrl));
        }
        return link;
    }

    private String appendQueryParam(String baseUrl, String key, String value) {
        String base = safeTrim(baseUrl);
        if (!StringUtils.hasText(base) || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return base;
        }
        String separator = base.contains("?") ? "&" : "?";
        return base + separator
                + URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        String raw = safeTrim(String.valueOf(value)).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(raw)) return defaultValue;
        return switch (raw) {
            case "1", "true", "t", "yes", "si", "y", "on" -> true;
            case "0", "false", "f", "no", "n", "off" -> false;
            default -> defaultValue;
        };
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String maskPhone(String phoneRaw) {
        String digits = safeTrim(phoneRaw).replaceAll("\\D+", "");
        if (digits.length() <= 4) return "****";
        return "*".repeat(Math.max(1, digits.length() - 4)) + digits.substring(digits.length() - 4);
    }
}
