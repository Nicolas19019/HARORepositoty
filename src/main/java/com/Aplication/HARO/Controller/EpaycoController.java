package com.Aplication.HARO.Controller;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Aplication.HARO.Model.ChatbotMatriculaProceso;
import com.Aplication.HARO.Service.EpaycoCheckoutContextService;
import com.Aplication.HARO.Service.ChatbotProcesoService;
import com.Aplication.HARO.Service.EpaycoService;
import com.Aplication.HARO.Service.PaymentApprovalService;
import com.Aplication.HARO.Service.PaymentSyncContextService;
import com.Aplication.HARO.Service.VerificationService;
import com.Aplication.HARO.Service.WhatsAppTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@CrossOrigin(originPatterns = {
        "https://ceaharo.com",
        "https://www.ceaharo.com",
        "https://*.ceaharo.com"
})
public class EpaycoController {

    private static final Logger log = LoggerFactory.getLogger(EpaycoController.class);
    private record SyncDecision(boolean synced, String reason, String resolvedBy) {}
    private record ContractAccessParams(String email, String code) {}

    private static final String ADVISOR_PROMPT = "\n\n🤖 ASESOR: Si necesitas ayuda, escribe ASESOR.";

    private final EpaycoService epaycoService;
    private final EpaycoCheckoutContextService checkoutContextService;
    private final ChatbotProcesoService chatbotProcesoService;
    private final PaymentApprovalService paymentApprovalService;
    private final PaymentSyncContextService paymentSyncContextService;
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
                            EpaycoCheckoutContextService checkoutContextService,
                            ChatbotProcesoService chatbotProcesoService,
                            PaymentApprovalService paymentApprovalService,
                            PaymentSyncContextService paymentSyncContextService,
                            VerificationService verificationService,
                            WhatsAppTemplateService waService,
                            ObjectMapper objectMapper) {
        this.epaycoService = epaycoService;
        this.checkoutContextService = checkoutContextService;
        this.chatbotProcesoService = chatbotProcesoService;
        this.paymentApprovalService = paymentApprovalService;
        this.paymentSyncContextService = paymentSyncContextService;
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
                                      @RequestParam(name = "flow_id", required = false) String flowId,
                                      @RequestParam(name = "flowId", required = false) String flowIdCamel,
                                      @RequestParam(name = "process_id", required = false) String processId,
                                      @RequestParam(name = "processId", required = false) String processIdCamel,
                                      @RequestParam(name = "matricula_id", required = false) String matriculaId,
                                      @RequestParam(name = "matriculaId", required = false) String matriculaIdCamel,
                                      @RequestParam(name = "x_extra2", required = false) String xExtra2,
                                      @RequestParam(name = "format", required = false) String format,
                                      @RequestHeader(name = "Accept", required = false) String acceptHeader) {
        return userFacingPaymentStatus(
                refPayco,
                format,
                acceptHeader,
                firstNotBlank(documentHint, xExtra1),
                firstNotBlank(flowId, flowIdCamel, processId, processIdCamel, matriculaId, matriculaIdCamel, xExtra2)
        );
    }

    // Compatibilidad: si por configuracion el retorno llega a /confirmation por GET
    @GetMapping({"/confirmation", "/epayco/confirmation"})
    public ResponseEntity<?> confirmationView(@RequestParam(name = "ref_payco", required = false) String refPayco,
                                              @RequestParam(name = "document", required = false) String documentHint,
                                              @RequestParam(name = "x_extra1", required = false) String xExtra1,
                                              @RequestParam(name = "flow_id", required = false) String flowId,
                                              @RequestParam(name = "flowId", required = false) String flowIdCamel,
                                              @RequestParam(name = "process_id", required = false) String processId,
                                              @RequestParam(name = "processId", required = false) String processIdCamel,
                                              @RequestParam(name = "matricula_id", required = false) String matriculaId,
                                              @RequestParam(name = "matriculaId", required = false) String matriculaIdCamel,
                                              @RequestParam(name = "x_extra2", required = false) String xExtra2,
                                              @RequestParam(name = "format", required = false) String format,
                                              @RequestHeader(name = "Accept", required = false) String acceptHeader) {
        return userFacingPaymentStatus(
                refPayco,
                format,
                acceptHeader,
                firstNotBlank(documentHint, xExtra1),
                firstNotBlank(flowId, flowIdCamel, processId, processIdCamel, matriculaId, matriculaIdCamel, xExtra2)
        );
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

    String refPayco = resolveLookupRefPayco(
            safeTrim(asString(safePayload.get("ref_payco"))),
            safeTrim(asString(safePayload.get("refPayco"))),
            safeTrim(asString(safePayload.get("lookupReference"))),
            safeTrim(asString(safePayload.get("lookup_ref_payco"))),
            safeTrim(asString(safePayload.get("reference")))
    );
    String gatewayRefPayco = firstNotBlank(
            safeTrim(asString(safePayload.get("x_ref_payco"))),
            safeTrim(asString(safePayload.get("gatewayReference")))
    );
    Long flowIdHint = resolveFlowId(
            safePayload.get("flow_id"),
            safePayload.get("flowId"),
            safePayload.get("process_id"),
            safePayload.get("processId"),
            safePayload.get("matricula_id"),
            safePayload.get("matriculaId"),
            safePayload.get("x_extra2"),
            safePayload.get("extra2")
    );
    String rawDocumentHint = firstNotBlank(
            safeTrim(asString(safePayload.get("x_extra1"))),
            safeTrim(asString(safePayload.get("document"))),
            safeTrim(asString(safePayload.get("customer_document"))),
            safeTrim(asString(safePayload.get("x_customer_document")))
    );
    String rawEmailHint = firstNotBlank(
            safeTrim(asString(safePayload.get("email"))),
            safeTrim(asString(safePayload.get("customer_email"))),
            safeTrim(asString(safePayload.get("x_customer_email")))
    );
    String rawPhoneHint = firstNotBlank(
            safeTrim(asString(safePayload.get("phone"))),
            safeTrim(asString(safePayload.get("customer_phone"))),
            safeTrim(asString(safePayload.get("x_customer_phone"))),
            safeTrim(asString(safePayload.get("x_customer_mobile"))),
            safeTrim(asString(safePayload.get("x_customer_movil")))
    );
    Optional<ChatbotMatriculaProceso> procesoOpt = resolveProcesoFromHints(flowIdHint, rawDocumentHint, rawEmailHint);

    String documentHint = firstResolvedDocument(
            rawDocumentHint,
            procesoDocumento(procesoOpt)
    );
    String emailHint = firstResolvedEmail(
            rawEmailHint,
            procesoEmail(procesoOpt)
    );
    String invoiceHint = firstNotBlank(
            safeTrim(asString(safePayload.get("x_id_invoice"))),
            safeTrim(asString(safePayload.get("x_id_factura"))),
            safeTrim(asString(safePayload.get("invoice")))
    );
    String transactionIdHint = firstNotBlank(
            safeTrim(asString(safePayload.get("x_transaction_id"))),
            safeTrim(asString(safePayload.get("transactionId"))),
            safeTrim(asString(safePayload.get("transaction_id")))
    );
    String phoneHint = firstResolvedPhone(
            rawPhoneHint,
            procesoTelefono(procesoOpt)
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
    String payloadPaymentMethod = normalizePaymentMethodHint(firstNotBlank(
            safeTrim(asString(safePayload.get("paymentMethod"))),
            safeTrim(asString(safePayload.get("payment_method"))),
            safeTrim(asString(safePayload.get("method"))),
            safeTrim(asString(safePayload.get("x_payment_method"))),
            safeTrim(asString(safePayload.get("x_franchise"))),
            safeTrim(asString(safePayload.get("franchise"))),
            safeTrim(asString(safePayload.get("x_bank_name"))),
            safeTrim(asString(safePayload.get("bank_name")))
    ));
    String payloadPaymentNote = buildPaymentNoteHint(
            safeTrim(asString(safePayload.get("paymentNote"))),
            safeTrim(asString(safePayload.get("payment_note"))),
            safeTrim(asString(safePayload.get("x_bank_name"))),
            safeTrim(asString(safePayload.get("bank_name"))),
            safeTrim(asString(safePayload.get("x_franchise"))),
            safeTrim(asString(safePayload.get("franchise")))
    );

    captureTemporaryContext(
            refPayco,
            gatewayRefPayco,
            invoiceHint,
            transactionIdHint,
            documentHint,
            emailHint,
            phoneHint,
            payloadStatus,
            "response_sync_payload",
            flowIdHint
    );

    boolean approvedByPayload = asBoolean(safePayload.get("paymentApproved"), false)
            || asBoolean(safePayload.get("approved"), false)
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
            summary = new LinkedHashMap<>(buildUserPaymentSummary(refPayco, rawData, documentHint, asString(flowIdHint)));
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

    if (flowIdHint == null) {
        flowIdHint = resolveFlowId(
                summary.get("flow_id"),
                summary.get("flowId"),
                summary.get("x_extra2"),
                summary.get("extra2")
        );
    }
    if (procesoOpt.isEmpty()) {
        procesoOpt = resolveProcesoFromHints(
                flowIdHint,
                firstNotBlank(safeTrim(asString(summary.get("document"))), documentHint),
                firstNotBlank(safeTrim(asString(summary.get("email"))), emailHint)
        );
    }
    if (procesoOpt.isPresent()) {
        ChatbotMatriculaProceso proceso = procesoOpt.get();
        documentHint = firstResolvedDocument(documentHint, procesoDocumento(procesoOpt));
        emailHint = firstResolvedEmail(emailHint, procesoEmail(procesoOpt));
        phoneHint = firstResolvedPhone(phoneHint, procesoTelefono(procesoOpt));
        summary.putIfAbsent("flowId", proceso.getId());
        summary.putIfAbsent("flow_id", proceso.getId());
    }

    String summaryGatewayReference = firstNotBlank(
            safeTrim(asString(summary.get("gatewayReference"))),
            gatewayRefPayco
    );
    String summaryInvoice = firstNotBlank(
            safeTrim(asString(summary.get("invoice"))),
            invoiceHint
    );
    String summaryTransactionId = firstNotBlank(
            safeTrim(asString(summary.get("transactionId"))),
            safeTrim(asString(summary.get("trx"))),
            transactionIdHint
    );
    String summaryDocument = firstResolvedDocument(
            safeTrim(asString(summary.get("document"))),
            documentHint
    );
    String summaryEmail = firstResolvedEmail(
            safeTrim(asString(summary.get("email"))),
            emailHint
    );
    String summaryPhone = firstResolvedPhone(
            safeTrim(asString(summary.get("phone"))),
            phoneHint
    );
    Long summaryFlowId = resolveFlowId(
            summary.get("flow_id"),
            summary.get("flowId"),
            summary.get("x_extra2"),
            summary.get("extra2"),
            flowIdHint
    );

    captureTemporaryContext(
            refPayco,
            summaryGatewayReference,
            summaryInvoice,
            summaryTransactionId,
            summaryDocument,
            summaryEmail,
            summaryPhone,
            safeTrim(asString(summary.get("status"))),
            "response_sync_summary",
            summaryFlowId
    );

    Optional<PaymentSyncContextService.ResolvedContext> stagedContext = paymentSyncContextService.resolve(
            refPayco,
            summaryGatewayReference,
            summaryInvoice,
            summaryTransactionId
    );
    if (stagedContext.isPresent()) {
        PaymentSyncContextService.ResolvedContext ctx = stagedContext.get();
        if (!StringUtils.hasText(normalizeDocumentoCandidate(safeTrim(asString(summary.get("document")))))
                && StringUtils.hasText(ctx.document())) {
            summary.put("document", ctx.document());
        }
        if (!StringUtils.hasText(sanitizeEmailCandidate(safeTrim(asString(summary.get("email")))))
                && StringUtils.hasText(ctx.email())) {
            summary.put("email", ctx.email());
        }
        if (!StringUtils.hasText(sanitizePhoneCandidate(safeTrim(asString(summary.get("phone")))))
                && StringUtils.hasText(ctx.phone())) {
            summary.put("phone", ctx.phone());
        }
        if (flowIdHint == null && ctx.flowId() != null && ctx.flowId() > 0) {
            flowIdHint = ctx.flowId();
            summary.putIfAbsent("flowId", flowIdHint);
            summary.putIfAbsent("flow_id", flowIdHint);
        }
        documentHint = firstResolvedDocument(documentHint, ctx.document());
        emailHint = firstResolvedEmail(emailHint, ctx.email());
        phoneHint = firstResolvedPhone(phoneHint, ctx.phone());
    }

    String status = safeTrim(asString(summary.get("status")));
    if (!"APPROVED".equalsIgnoreCase(status) && approvedByPayload) {
        status = PaymentUserStatus.APPROVED.code;
        summary.put("status", status);
        summary.put("title", PaymentUserStatus.APPROVED.title);
        summary.put("message", PaymentUserStatus.APPROVED.defaultMessage);
        summary.put("nextStep", PaymentUserStatus.APPROVED.nextStep);
    }

    Map<String, Object> out = new LinkedHashMap<>(summary);
    boolean chatbotSynced = false;
    Map<String, Object> syncPayload = new LinkedHashMap<>(safePayload);
    if (flowIdHint != null) {
        syncPayload.putIfAbsent("flow_id", flowIdHint);
        syncPayload.putIfAbsent("flowId", flowIdHint);
        syncPayload.putIfAbsent("x_extra2", String.valueOf(flowIdHint));
    }
    if (!StringUtils.hasText(normalizeDocumentoCandidate(safeTrim(asString(syncPayload.get("document")))))
            && StringUtils.hasText(documentHint)) {
        syncPayload.put("document", documentHint);
        syncPayload.put("x_extra1", documentHint);
    }
    if (!StringUtils.hasText(sanitizeEmailCandidate(safeTrim(asString(syncPayload.get("email")))))
            && StringUtils.hasText(emailHint)) {
        syncPayload.put("email", emailHint);
        syncPayload.put("customer_email", emailHint);
    }
    if (!StringUtils.hasText(sanitizePhoneCandidate(safeTrim(asString(syncPayload.get("phone")))))
            && StringUtils.hasText(phoneHint)) {
        syncPayload.put("phone", phoneHint);
        syncPayload.put("customer_phone", phoneHint);
        syncPayload.put("x_customer_phone", phoneHint);
        syncPayload.put("x_customer_mobile", phoneHint);
    }

    if ("APPROVED".equalsIgnoreCase(status)) {
        SyncDecision syncDecision = syncApprovedPaymentToFlow(summary, syncPayload, documentHint, emailHint);
        chatbotSynced = syncDecision.synced();
        if (StringUtils.hasText(syncDecision.reason())) {
            out.put("syncReason", syncDecision.reason());
        }
        if (StringUtils.hasText(syncDecision.resolvedBy())) {
            out.put("syncResolvedBy", syncDecision.resolvedBy());
        }
        if (summary.containsKey("whatsappSent")) {
            out.put("whatsappSent", summary.get("whatsappSent"));
        }
        if (summary.containsKey("duplicatePaymentProcessing")) {
            out.put("duplicatePaymentProcessing", summary.get("duplicatePaymentProcessing"));
        }

        String finalDocument = firstResolvedDocument(
                safeTrim(asString(safePayload.get("x_extra1"))),
                safeTrim(documentHint),
                safeTrim(asString(safePayload.get("document"))),
                safeTrim(asString(summary.get("document"))),
                safeTrim(asString(summary.get("customerDocument")))
        );
        String finalDocumentNormalized = normalizeDocumentoCandidate(finalDocument);
        String finalEmail = firstResolvedEmail(
                safeTrim(asString(out.get("email"))),
                safeTrim(asString(summary.get("email"))),
                safeTrim(asString(safePayload.get("email"))),
                safeTrim(asString(safePayload.get("customer_email"))),
                safeTrim(emailHint)
        );

        if (StringUtils.hasText(finalDocumentNormalized)) {
            chatbotProcesoService.findProcesoByDocumento(finalDocumentNormalized).ifPresent(p -> {
                out.put("flowStatus", safeTrim(p.getFlowStatus()));
                out.put("paymentStatus", safeTrim(p.getPaymentStatus()));
                out.put("document", safeTrim(p.getNumeroDocumento()));
                out.put("email", firstNotBlank(safeTrim(p.getEmail()), safeTrim(asString(out.get("email")))));
                String contractLink = resolveContractLinkForProceso(p);
                if (StringUtils.hasText(contractLink)) {
                    out.put("contractLink", contractLink);
                    out.put("nextStep",
                            "Tu pago fue aprobado. Contin\u00faa con la contrataci\u00f3n en este enlace: " + contractLink);
                }
            });
        } else if (StringUtils.hasText(finalEmail)) {
            chatbotProcesoService.findLatestProcesoByEmail(finalEmail).ifPresent(p -> {
                out.put("flowStatus", safeTrim(p.getFlowStatus()));
                out.put("paymentStatus", safeTrim(p.getPaymentStatus()));
                out.put("document", firstNotBlank(safeTrim(p.getNumeroDocumento()), safeTrim(asString(out.get("document")))));
                out.put("email", firstNotBlank(safeTrim(p.getEmail()), safeTrim(asString(out.get("email")))));
                String contractLink = resolveContractLinkForProceso(p);
                if (StringUtils.hasText(contractLink)) {
                    out.put("contractLink", contractLink);
                    out.put("nextStep",
                            "Tu pago fue aprobado. Contin\u00faa con la contrataci\u00f3n en este enlace: " + contractLink);
                }
            });
        }
    }
    out.put("status", status);
    if (StringUtils.hasText(refPayco)) {
        out.put("reference", refPayco);
    }
    if (flowIdHint != null) {
        out.put("flowId", flowIdHint);
        out.put("flow_id", flowIdHint);
    }
    if (StringUtils.hasText(gatewayRefPayco)) {
        out.put("gatewayReference", gatewayRefPayco);
    }
    out.put("chatbotSynced", chatbotSynced);
    out.put("approvedByPayload", approvedByPayload);
    out.put("syncSource", "response_page");
    out.put("statusSource", statusSource);
    out.put("syncAt", ZonedDateTime.now(ZoneId.of("America/Bogota"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")));
    if (StringUtils.hasText(payloadPaymentMethod)) {
        out.put("paymentMethod", payloadPaymentMethod);
    }
    if (StringUtils.hasText(payloadPaymentNote)) {
        out.put("paymentNote", payloadPaymentNote);
    }
    out.putIfAbsent("paymentStatus", safeTrim(asString(out.get("paymentStatus"))));
    out.putIfAbsent("flowStatus", safeTrim(asString(out.get("flowStatus"))));
    out.putIfAbsent("contractLink", safeTrim(asString(out.get("contractLink"))));
    out.remove("_maskedDocumentRaw");
    out.remove("_maskedEmailRaw");
    out.remove("_maskedPhoneRaw");

    String metadataDocument = firstResolvedDocument(
            safeTrim(asString(out.get("document"))),
            summaryDocument,
            documentHint
    );
    capturePaymentMetadataIfPossible(metadataDocument, payloadPaymentMethod, payloadPaymentNote);

    String contextRef = firstNotBlank(refPayco, safeTrim(asString(out.get("reference"))));
    String contextGatewayRef = firstNotBlank(summaryGatewayReference, safeTrim(asString(out.get("gatewayReference"))), gatewayRefPayco);
    String contextInvoice = firstNotBlank(summaryInvoice, safeTrim(asString(out.get("invoice"))), invoiceHint);
    String contextTransaction = firstNotBlank(summaryTransactionId, safeTrim(asString(out.get("transactionId"))), transactionIdHint);
    if ("APPROVED".equalsIgnoreCase(status) && chatbotSynced) {
        paymentSyncContextService.clearByReferences(contextRef, contextGatewayRef, contextInvoice, contextTransaction);
    } else if ("REJECTED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
        paymentSyncContextService.clearByReferences(contextRef, contextGatewayRef, contextInvoice, contextTransaction);
    }

    return ResponseEntity.ok(out);
}

    private SyncDecision syncApprovedPaymentToFlow(Map<String, Object> summary,
                                                  Map<String, Object> payload,
                                                  String documentHint,
                                                  String emailHint) {
        if (summary == null) {
            return new SyncDecision(false, "summary_empty", "");
        }

        Map<String, Object> safePayload = payload == null ? Map.of() : payload;

        BigDecimal amount = parseAmountOrNull(firstNotBlank(
                safeTrim(asString(summary.get("amount"))),
                safeTrim(asString(safePayload.get("x_amount")))
        ));

        Long flowId = resolveFlowId(
                safePayload.get("flow_id"),
                safePayload.get("flowId"),
                safePayload.get("x_extra2"),
                summary.get("flow_id"),
                summary.get("flowId"),
                summary.get("x_extra2")
        );
        if (flowId != null) {
            Optional<ChatbotMatriculaProceso> procesoById = chatbotProcesoService.findProcesoById(flowId);
            if (procesoById.isPresent()) {
                String docFromFlow = firstResolvedDocument(safeTrim(procesoById.get().getNumeroDocumento()));
                if (StringUtils.hasText(docFromFlow)) {
                    try {
                        PaymentApprovalService.ApprovalResult approval =
                                paymentApprovalService.handleApprovedPayment(docFromFlow, amount);
                        applyApprovalResultToSummary(summary, approval, docFromFlow, safeTrim(procesoById.get().getEmail()));
                        summary.put("phone", firstResolvedPhone(
                                safeTrim(procesoById.get().getPhone()),
                                safeTrim(procesoById.get().getTelefono())
                        ));
                        boolean synced = approval.whatsappSent() || approval.duplicate();
                        return new SyncDecision(
                                synced,
                                synced ? "processed_by_flow_id" : "processed_by_flow_id_without_whatsapp_confirmation",
                                "flow_id"
                        );
                    } catch (Exception ex) {
                        log.error("No fue posible sincronizar pago aprobado por flow_id={} : {}", flowId, ex.getMessage(), ex);
                    }
                }
            }
        }

        String documento = firstResolvedDocument(
                safeTrim(asString(safePayload.get("x_extra1"))),
                safeTrim(documentHint),
                safeTrim(asString(safePayload.get("document"))),
                safeTrim(asString(safePayload.get("customer_document"))),
                safeTrim(asString(safePayload.get("x_customer_document"))),
                safeTrim(asString(summary.get("document"))),
                safeTrim(asString(summary.get("customerDocument")))
        );
        String customerPhoneRaw = firstNotBlank(
                safeTrim(asString(summary.get("phone"))),
                safeTrim(asString(summary.get("customerPhone"))),
                safeTrim(asString(safePayload.get("phone"))),
                safeTrim(asString(safePayload.get("customer_phone"))),
                safeTrim(asString(safePayload.get("x_customer_phone"))),
                safeTrim(asString(safePayload.get("x_customer_mobile"))),
                safeTrim(asString(safePayload.get("x_customer_movil")))
        );

        if (StringUtils.hasText(documento)) {
            try {
                PaymentApprovalService.ApprovalResult approval = paymentApprovalService.handleApprovedPayment(documento, amount);
                applyApprovalResultToSummary(summary, approval, documento, "");
                boolean synced = approval.whatsappSent() || approval.duplicate();
                return new SyncDecision(synced, synced ? "processed_by_document" : "processed_by_document_without_whatsapp_confirmation", "document");
            } catch (NoSuchElementException ex) {
                // Si el documento enviado no corresponde a un proceso vigente, intentamos fallback automático.
                log.warn("No se encontro proceso por documento doc={}. Se intentara fallback por email/phone/contexto.", documento);
            } catch (Exception ex) {
                log.error("Error procesando por documento doc={}. Se intentara fallback: {}", documento, ex.getMessage(), ex);
            }
        }

        String email = firstResolvedEmail(
                safeTrim(asString(summary.get("email"))),
                safeTrim(asString(summary.get("customerEmail"))),
                safeTrim(asString(safePayload.get("email"))),
                safeTrim(asString(safePayload.get("customer_email"))),
                safeTrim(asString(safePayload.get("x_customer_email"))),
                safeTrim(emailHint)
        );
        if (!StringUtils.hasText(email)) {
            Optional<SyncDecision> byPhone = trySyncByPhone(summary, safePayload, amount, customerPhoneRaw);
            if (byPhone.isPresent()) {
                return byPhone.get();
            }
            Optional<SyncDecision> byMasked = trySyncByMaskedHints(summary, safePayload, amount);
            if (byMasked.isPresent()) {
                return byMasked.get();
            }
            Optional<SyncDecision> byInvoice = trySyncByInvoice(summary, safePayload, amount);
            if (byInvoice.isPresent()) {
                return byInvoice.get();
            }

            log.warn("Sync pago aprobado sin identificadores resolubles del chat. reference={} gatewayRef={} invoice={}",
                    safeTrim(asString(summary.get("reference"))),
                    firstNotBlank(safeTrim(asString(summary.get("gatewayReference"))), safeTrim(asString(safePayload.get("x_ref_payco")))),
                    safeTrim(asString(summary.get("invoice"))));
            return new SyncDecision(false, "missing_identifiers_from_chat_context", "");
        }

        try {
            Optional<ChatbotMatriculaProceso> proceso = chatbotProcesoService.findLatestProcesoByEmail(email);
            if (proceso.isEmpty()) {
                Optional<SyncDecision> byPhone = trySyncByPhone(summary, safePayload, amount, customerPhoneRaw);
                if (byPhone.isPresent()) {
                    return byPhone.get();
                }
                Optional<SyncDecision> byMasked = trySyncByMaskedHints(summary, safePayload, amount);
                if (byMasked.isPresent()) {
                    return byMasked.get();
                }
                Optional<SyncDecision> byInvoice = trySyncByInvoice(summary, safePayload, amount);
                if (byInvoice.isPresent()) {
                    return byInvoice.get();
                }
                log.info("No se pudo resolver proceso por email para sync de pago. email={}", email);
                return new SyncDecision(false, "process_not_found_by_email", "email");
            }

            String fromEmailDoc = safeTrim(proceso.get().getNumeroDocumento());
            if (!StringUtils.hasText(fromEmailDoc)) {
                return new SyncDecision(false, "process_without_document_by_email", "email");
            }

            PaymentApprovalService.ApprovalResult approval =
                    paymentApprovalService.handleApprovedPayment(fromEmailDoc, amount);
            applyApprovalResultToSummary(summary, approval, fromEmailDoc, email);
            boolean synced = approval.whatsappSent() || approval.duplicate();
            return new SyncDecision(synced, synced ? "processed_by_email" : "processed_by_email_without_whatsapp_confirmation", "email");
        } catch (Exception ex) {
            log.error("No fue posible sincronizar pago aprobado por email. email={}: {}", email, ex.getMessage(), ex);
            Optional<SyncDecision> byMasked = trySyncByMaskedHints(summary, safePayload, amount);
            if (byMasked.isPresent()) {
                return byMasked.get();
            }
            Optional<SyncDecision> byInvoice = trySyncByInvoice(summary, safePayload, amount);
            if (byInvoice.isPresent()) {
                return byInvoice.get();
            }
            return new SyncDecision(false, "error_processing_by_email", "email");
        }
    }

    private Optional<SyncDecision> trySyncByInvoice(Map<String, Object> summary,
                                                    Map<String, Object> safePayload,
                                                    BigDecimal amount) {
        String invoice = firstNotBlank(
                safeTrim(asString(summary.get("invoice"))),
                safeTrim(asString(safePayload.get("x_id_invoice"))),
                safeTrim(asString(safePayload.get("x_id_factura"))),
                safeTrim(asString(safePayload.get("invoice")))
        );
        if (!StringUtils.hasText(invoice)) {
            return Optional.empty();
        }

        try {
            Long flowIdFromInvoice = parseFlowIdFromInvoiceHint(invoice);
            if (flowIdFromInvoice != null) {
                Optional<ChatbotMatriculaProceso> procesoByFlow = chatbotProcesoService.findProcesoById(flowIdFromInvoice);
                if (procesoByFlow.isPresent()) {
                    ChatbotMatriculaProceso resolved = procesoByFlow.get();
                    String document = safeTrim(resolved.getNumeroDocumento());
                    if (!StringUtils.hasText(document)) {
                        return Optional.of(new SyncDecision(false, "process_without_document_by_invoice_flow", "invoice_flow"));
                    }
                    PaymentApprovalService.ApprovalResult approval =
                            paymentApprovalService.handleApprovedPayment(document, amount);
                    applyApprovalResultToSummary(summary, approval, document, safeTrim(resolved.getEmail()));
                    summary.put("phone", firstResolvedPhone(safeTrim(resolved.getPhone()), safeTrim(resolved.getTelefono())));
                    summary.put("flowId", flowIdFromInvoice);
                    summary.put("flow_id", flowIdFromInvoice);
                    boolean synced = approval.whatsappSent() || approval.duplicate();
                    return Optional.of(new SyncDecision(
                            synced,
                            synced ? "processed_by_invoice_flow_id" : "processed_by_invoice_flow_id_without_whatsapp_confirmation",
                            "invoice_flow"
                    ));
                }
            }

            Optional<ChatbotMatriculaProceso> proceso = chatbotProcesoService.findLatestProcesoByInvoiceHint(invoice);
            if (proceso.isEmpty()) {
                log.info("No se pudo resolver proceso por invoice para sync de pago. invoice={}", invoice);
                return Optional.of(new SyncDecision(false, "process_not_found_by_invoice", "invoice"));
            }

            ChatbotMatriculaProceso resolved = proceso.get();
            String document = safeTrim(resolved.getNumeroDocumento());
            if (!StringUtils.hasText(document)) {
                return Optional.of(new SyncDecision(false, "process_without_document_by_invoice", "invoice"));
            }

            PaymentApprovalService.ApprovalResult approval =
                    paymentApprovalService.handleApprovedPayment(document, amount);
            applyApprovalResultToSummary(summary, approval, document, safeTrim(resolved.getEmail()));
            summary.put("phone", firstResolvedPhone(safeTrim(resolved.getPhone()), safeTrim(resolved.getTelefono())));
            if (resolved.getId() != null) {
                summary.put("flowId", resolved.getId());
                summary.put("flow_id", resolved.getId());
            }
            boolean synced = approval.whatsappSent() || approval.duplicate();
            return Optional.of(new SyncDecision(
                    synced,
                    synced ? "processed_by_invoice" : "processed_by_invoice_without_whatsapp_confirmation",
                    "invoice"
            ));
        } catch (Exception ex) {
            log.error("No fue posible sincronizar pago por invoice={} : {}", invoice, ex.getMessage(), ex);
            return Optional.of(new SyncDecision(false, "error_processing_by_invoice", "invoice"));
        }
    }

    private Optional<SyncDecision> trySyncByMaskedHints(Map<String, Object> summary,
                                                        Map<String, Object> safePayload,
                                                        BigDecimal amount) {
        String maskedDocument = firstMaskedValue(
                safeTrim(asString(summary.get("_maskedDocumentRaw"))),
                safeTrim(asString(safePayload.get("masked_document"))),
                safeTrim(asString(safePayload.get("customer_document"))),
                safeTrim(asString(safePayload.get("x_customer_document"))),
                safeTrim(asString(safePayload.get("document"))),
                safeTrim(asString(safePayload.get("x_extra1")))
        );
        String maskedEmail = firstMaskedValue(
                safeTrim(asString(summary.get("_maskedEmailRaw"))),
                safeTrim(asString(safePayload.get("masked_email"))),
                safeTrim(asString(safePayload.get("customer_email"))),
                safeTrim(asString(safePayload.get("x_customer_email"))),
                safeTrim(asString(safePayload.get("email")))
        );
        String maskedPhone = firstMaskedValue(
                safeTrim(asString(summary.get("_maskedPhoneRaw"))),
                safeTrim(asString(safePayload.get("masked_phone"))),
                safeTrim(asString(safePayload.get("customer_phone"))),
                safeTrim(asString(safePayload.get("x_customer_phone"))),
                safeTrim(asString(safePayload.get("x_customer_mobile"))),
                safeTrim(asString(safePayload.get("phone")))
        );

        if (!StringUtils.hasText(maskedDocument) && !StringUtils.hasText(maskedEmail) && !StringUtils.hasText(maskedPhone)) {
            return Optional.empty();
        }

        Optional<ChatbotMatriculaProceso> proceso = chatbotProcesoService.findLatestProcesoByMaskedHints(
                maskedDocument,
                maskedEmail,
                maskedPhone
        );
        if (proceso.isEmpty()) {
            return Optional.of(new SyncDecision(false, "masked_identifiers_not_resolved", "masked"));
        }

        ChatbotMatriculaProceso resolved = proceso.get();
        String document = safeTrim(resolved.getNumeroDocumento());
        if (!StringUtils.hasText(document)) {
            return Optional.of(new SyncDecision(false, "masked_process_without_document", "masked"));
        }

        try {
            PaymentApprovalService.ApprovalResult approval = paymentApprovalService.handleApprovedPayment(document, amount);
            applyApprovalResultToSummary(summary, approval, document, safeTrim(resolved.getEmail()));
            summary.put("phone", firstResolvedPhone(safeTrim(resolved.getPhone()), safeTrim(resolved.getTelefono())));
            if (resolved.getId() != null) {
                summary.put("flowId", resolved.getId());
                summary.put("flow_id", resolved.getId());
            }
            boolean synced = approval.whatsappSent() || approval.duplicate();
            return Optional.of(new SyncDecision(
                    synced,
                    synced ? "processed_by_masked_hints" : "processed_by_masked_hints_without_whatsapp_confirmation",
                    "masked"
            ));
        } catch (Exception ex) {
            log.error("No fue posible sincronizar pago por mascaras docMask={} emailMask={} phoneMask={}: {}",
                    maskedDocument, maskedEmail, maskedPhone, ex.getMessage(), ex);
            return Optional.of(new SyncDecision(false, "error_processing_by_masked_hints", "masked"));
        }
    }

    private Optional<SyncDecision> trySyncByPhone(Map<String, Object> summary,
                                                  Map<String, Object> safePayload,
                                                  BigDecimal amount,
                                                  String customerPhoneRaw) {
        String phone = firstResolvedPhone(
                safeTrim(asString(summary.get("phone"))),
                safeTrim(asString(summary.get("customerPhone"))),
                safeTrim(asString(safePayload.get("phone"))),
                safeTrim(asString(safePayload.get("customer_phone"))),
                safeTrim(asString(safePayload.get("x_customer_phone"))),
                safeTrim(asString(safePayload.get("x_customer_mobile"))),
                safeTrim(asString(safePayload.get("x_customer_movil"))),
                safeTrim(customerPhoneRaw)
        );
        if (!StringUtils.hasText(phone)) {
            return Optional.empty();
        }
        try {
            Optional<ChatbotMatriculaProceso> proceso = chatbotProcesoService.findLatestProcesoByPhone(phone);
            if (proceso.isEmpty()) {
                log.info("No se pudo resolver proceso por phone para sync de pago. phone={}", maskPhone(phone));
                return Optional.of(new SyncDecision(false, "process_not_found_by_phone", "phone"));
            }

            String fromPhoneDoc = safeTrim(proceso.get().getNumeroDocumento());
            if (!StringUtils.hasText(fromPhoneDoc)) {
                return Optional.of(new SyncDecision(false, "process_without_document_by_phone", "phone"));
            }

            PaymentApprovalService.ApprovalResult approval =
                    paymentApprovalService.handleApprovedPayment(fromPhoneDoc, amount);
            applyApprovalResultToSummary(summary, approval, fromPhoneDoc, safeTrim(proceso.get().getEmail()));
            summary.put("phone", phone);
            boolean synced = approval.whatsappSent() || approval.duplicate();
            return Optional.of(new SyncDecision(synced, synced ? "processed_by_phone" : "processed_by_phone_without_whatsapp_confirmation", "phone"));
        } catch (Exception ex) {
            log.error("No fue posible sincronizar pago aprobado por phone. phone={}: {}", maskPhone(phone), ex.getMessage(), ex);
            return Optional.of(new SyncDecision(false, "error_processing_by_phone", "phone"));
        }
    }

    private void applyApprovalResultToSummary(Map<String, Object> summary,
                                              PaymentApprovalService.ApprovalResult approval,
                                              String document,
                                              String email) {
        if (summary == null || approval == null) {
            return;
        }
        if (StringUtils.hasText(document)) {
            summary.put("document", document);
        }
        if (StringUtils.hasText(email)) {
            summary.put("email", email);
        }
        summary.put("paymentStatus", approval.paymentStatus());
        summary.put("flowStatus", approval.flowStatus());
        if (StringUtils.hasText(approval.contractLink())) {
            summary.put("contractLink", approval.contractLink());
        }
        summary.put("whatsappSent", approval.whatsappSent());
        summary.put("duplicatePaymentProcessing", approval.duplicate());
    }

    private ResponseEntity<?> userFacingPaymentStatus(String refPaycoRaw,
                                                      String formatRaw,
                                                      String acceptHeaderRaw,
                                                      String documentHintRaw,
                                                      String flowIdHintRaw) {
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

        Map<String, Object> payload = buildUserPaymentSummary(refPayco, rawData, documentHint, flowIdHintRaw);
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

    private Map<String, Object> buildUserPaymentSummary(String refPayco,
                                                        String rawData,
                                                        String documentHintRaw,
                                                        String flowIdHintRaw) {
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
        String xCodResponse = readFirstField(tx, root,
                "x_cod_response", "x_cod_respuesta", "cod_response", "codResponse");
        String xResponse = readFirstField(tx, root,
                "x_response", "x_respuesta", "x_transaction_state", "transaction_state");
        String xReason = firstNotBlank(
                readFirstField(tx, root, "x_response_reason_text", "response_reason_text", "text_response"),
                readField(root, "text_response")
        );
        String xAmount = readFirstField(tx, root, "x_amount", "amount", "x_amount_ok", "x_amount_country", "x_amount_base");
        String xCurrency = readFirstField(tx, root, "x_currency_code", "currency", "currency_code");
        String xTransactionId = readFirstField(tx, root, "x_transaction_id", "transaction_id");
        String xInvoice = readFirstField(tx, root, "x_id_invoice", "x_id_factura", "id_invoice", "invoice");
        String xRefPayco = readFirstField(tx, root, "x_ref_payco", "ref_payco", "refPayco");
        Long flowIdFromPayload = resolveFlowId(
                flowIdHintRaw,
                readFirstField(tx, root, "flow_id", "flowId", "process_id", "processId", "matricula_id", "matriculaId", "x_extra2", "extra2")
        );

        String customerDocumentRaw = readFirstField(tx, root,
                "x_extra1", "document", "x_customer_document", "customer_document", "x_customer_docnumber", "x_doc_number");
        String xDocumento = firstResolvedDocument(customerDocumentRaw, documentHintRaw);
        String maskedDocumentRaw = customerDocumentRaw != null && customerDocumentRaw.contains("*")
                ? safeTrim(customerDocumentRaw)
                : "";

        String customerEmailRaw = readFirstField(tx, root,
                "x_customer_email", "customer_email", "x_email", "email");
        String xEmail = firstResolvedEmail(customerEmailRaw);
        String maskedEmailRaw = customerEmailRaw != null && customerEmailRaw.contains("*")
                ? safeTrim(customerEmailRaw)
                : "";
        String customerPhoneRaw = readFirstField(tx, root,
                "x_customer_phone", "x_customer_mobile", "x_customer_movil", "customer_phone", "customer_mobile", "phone", "telefono", "x_phone");
        String xPhone = firstResolvedPhone(customerPhoneRaw);
        String maskedPhoneRaw = customerPhoneRaw != null && customerPhoneRaw.contains("*")
                ? safeTrim(customerPhoneRaw)
                : "";

        String lookupRefPayco = resolveLookupRefPayco(refPayco, readFirstField(root, tx, "ref_payco", "reference"));

        PaymentUserStatus status = resolvePaymentUserStatus(xResponse, xCodResponse, xReason);

        Optional<ChatbotMatriculaProceso> procesoOpt = Optional.empty();
        if (flowIdFromPayload != null) {
            procesoOpt = chatbotProcesoService.findProcesoById(flowIdFromPayload);
        }
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
        if (procesoOpt.isEmpty() && StringUtils.hasText(xEmail)) {
            procesoOpt = chatbotProcesoService.findLatestProcesoByEmail(xEmail);
        }

        if (status == PaymentUserStatus.APPROVED && StringUtils.hasText(xDocumento)) {
            procesoOpt = chatbotProcesoService.findProcesoByDocumento(xDocumento);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", status.code);
        out.put("title", status.title);
        out.put("message", status.message(xReason));
        out.put("nextStep", status.nextStep);
        out.put("reference", firstNotBlank(lookupRefPayco, xRefPayco));
        if (flowIdFromPayload != null) {
            out.put("flowId", flowIdFromPayload);
            out.put("flow_id", flowIdFromPayload);
        }
        if (StringUtils.hasText(xRefPayco) && !xRefPayco.equals(out.get("reference"))) {
            out.put("gatewayReference", xRefPayco);
        }

        String localDocument = xDocumento;
        String localEmail = xEmail;

        if (procesoOpt.isPresent()) {
            ChatbotMatriculaProceso proceso = procesoOpt.get();
            if (!StringUtils.hasText(localDocument)) {
                localDocument = safeTrim(proceso.getNumeroDocumento());
            }
            if (!StringUtils.hasText(localEmail)) {
                localEmail = safeTrim(proceso.getEmail());
            }
            if (!StringUtils.hasText(xPhone)) {
                xPhone = firstResolvedPhone(safeTrim(proceso.getPhone()), safeTrim(proceso.getTelefono()));
            }
        }

        if (StringUtils.hasText(localDocument)) out.put("document", localDocument);
        if (StringUtils.hasText(localEmail)) out.put("email", localEmail);
        if (StringUtils.hasText(xPhone)) out.put("phone", xPhone);
        if (StringUtils.hasText(maskedDocumentRaw)) out.put("_maskedDocumentRaw", maskedDocumentRaw);
        if (StringUtils.hasText(maskedEmailRaw)) out.put("_maskedEmailRaw", maskedEmailRaw);
        if (StringUtils.hasText(maskedPhoneRaw)) out.put("_maskedPhoneRaw", maskedPhoneRaw);

        if (StringUtils.hasText(xTransactionId)) out.put("transactionId", xTransactionId);
        if (StringUtils.hasText(xInvoice)) out.put("invoice", xInvoice);
        if (StringUtils.hasText(xAmount)) out.put("amount", xAmount);
        if (StringUtils.hasText(xCurrency)) out.put("currency", xCurrency);

        Map<String, String> gateway = new LinkedHashMap<>();
        if (StringUtils.hasText(xCodResponse)) gateway.put("codResponse", xCodResponse);
        if (StringUtils.hasText(xResponse)) gateway.put("response", xResponse);
        if (StringUtils.hasText(xReason)) gateway.put("reason", xReason);
        if (StringUtils.hasText(xRefPayco)) gateway.put("xRefPayco", xRefPayco);
        if (!gateway.isEmpty()) out.put("gateway", gateway);

        if (procesoOpt.isPresent()) {
            ChatbotMatriculaProceso proceso = procesoOpt.get();
            if (proceso.getId() != null) {
                out.put("flowId", proceso.getId());
                out.put("flow_id", proceso.getId());
            }
            String flowStatus = safeTrim(proceso.getFlowStatus());
            String paymentStatus = safeTrim(proceso.getPaymentStatus());
            if (StringUtils.hasText(flowStatus)) out.put("flowStatus", flowStatus);
            if (StringUtils.hasText(paymentStatus)) out.put("paymentStatus", paymentStatus);

            if (status == PaymentUserStatus.APPROVED) {
                String contractLink = resolveContractLinkForProceso(proceso);
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

        String refPayco = resolveLookupRefPayco(
                safeTrim(refPaycoRaw),
                safeTrim(asString(safePayload.get("ref_payco"))),
                safeTrim(asString(safePayload.get("refPayco"))),
                safeTrim(asString(safePayload.get("lookupReference"))),
                safeTrim(asString(safePayload.get("lookup_ref_payco"))),
                safeTrim(asString(safePayload.get("reference")))
        );
        String gatewayRefPayco = firstNotBlank(
                safeTrim(asString(safePayload.get("x_ref_payco"))),
                safeTrim(asString(safePayload.get("gatewayReference")))
        );
        String xCodResponse = firstNotBlank(
                safeTrim(asString(safePayload.get("x_cod_response"))),
                safeTrim(asString(safePayload.get("x_cod_respuesta"))),
                safeTrim(asString(safePayload.get("codResponse")))
        );
        String xResponse = firstNotBlank(
                safeTrim(asString(safePayload.get("x_response"))),
                safeTrim(asString(safePayload.get("x_respuesta"))),
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
                safeTrim(asString(safePayload.get("x_id_factura"))),
                safeTrim(asString(safePayload.get("invoice")))
        );
        String rawDocument = firstNotBlank(
                safeTrim(asString(safePayload.get("x_extra1"))),
                safeTrim(asString(safePayload.get("document"))),
                safeTrim(asString(safePayload.get("customer_document"))),
                safeTrim(asString(safePayload.get("x_customer_document"))),
                safeTrim(documentHintRaw)
        );
        String xDocumento = firstResolvedDocument(rawDocument);
        String maskedDocumentRaw = rawDocument != null && rawDocument.contains("*") ? safeTrim(rawDocument) : "";
        String rawEmail = firstNotBlank(
                safeTrim(asString(safePayload.get("customer_email"))),
                safeTrim(asString(safePayload.get("x_customer_email"))),
                safeTrim(asString(safePayload.get("email"))),
                safeTrim(emailHintRaw)
        );
        String xEmail = firstResolvedEmail(rawEmail);
        String maskedEmailRaw = rawEmail != null && rawEmail.contains("*") ? safeTrim(rawEmail) : "";
        String rawPhone = firstNotBlank(
                safeTrim(asString(safePayload.get("phone"))),
                safeTrim(asString(safePayload.get("customer_phone"))),
                safeTrim(asString(safePayload.get("x_customer_phone"))),
                safeTrim(asString(safePayload.get("x_customer_mobile"))),
                safeTrim(asString(safePayload.get("x_customer_movil"))),
                safeTrim(asString(safePayload.get("customer_mobile"))),
                safeTrim(asString(safePayload.get("mobile"))),
                safeTrim(asString(safePayload.get("telefono")))
        );
        String xPhone = firstResolvedPhone(rawPhone);
        String maskedPhoneRaw = rawPhone != null && rawPhone.contains("*") ? safeTrim(rawPhone) : "";
        String paymentMethod = normalizePaymentMethodHint(firstNotBlank(
                safeTrim(asString(safePayload.get("paymentMethod"))),
                safeTrim(asString(safePayload.get("payment_method"))),
                safeTrim(asString(safePayload.get("method"))),
                safeTrim(asString(safePayload.get("x_payment_method"))),
                safeTrim(asString(safePayload.get("x_franchise"))),
                safeTrim(asString(safePayload.get("franchise"))),
                safeTrim(asString(safePayload.get("x_bank_name"))),
                safeTrim(asString(safePayload.get("bank_name")))
        ));
        String paymentNote = buildPaymentNoteHint(
                safeTrim(asString(safePayload.get("paymentNote"))),
                safeTrim(asString(safePayload.get("payment_note"))),
                safeTrim(asString(safePayload.get("x_bank_name"))),
                safeTrim(asString(safePayload.get("bank_name"))),
                safeTrim(asString(safePayload.get("x_franchise"))),
                safeTrim(asString(safePayload.get("franchise")))
        );

        if (flowId != null && (!StringUtils.hasText(xDocumento) || !StringUtils.hasText(xEmail) || !StringUtils.hasText(xPhone))) {
            Optional<ChatbotMatriculaProceso> procesoById = chatbotProcesoService.findProcesoById(flowId);
            if (procesoById.isPresent()) {
                ChatbotMatriculaProceso proceso = procesoById.get();
                xDocumento = firstResolvedDocument(xDocumento, safeTrim(proceso.getNumeroDocumento()));
                xEmail = firstResolvedEmail(xEmail, safeTrim(proceso.getEmail()));
                xPhone = firstResolvedPhone(xPhone, safeTrim(proceso.getPhone()), safeTrim(proceso.getTelefono()));
            }
        }

        PaymentUserStatus status = resolvePaymentUserStatus(xResponse, xCodResponse, xReason);
        if (asBoolean(safePayload.get("paymentApproved"), false)) {
            status = PaymentUserStatus.APPROVED;
        }

        out.put("status", status.code);
        out.put("title", status.title);
        out.put("message", status.message(xReason));
        out.put("nextStep", status.nextStep);

        if (StringUtils.hasText(refPayco)) {
            out.put("reference", refPayco);
        } else if (StringUtils.hasText(gatewayRefPayco)) {
            out.put("reference", gatewayRefPayco);
        }
        if (StringUtils.hasText(gatewayRefPayco)) out.put("gatewayReference", gatewayRefPayco);
        if (flowId != null) {
            out.put("flowId", flowId);
            out.put("flow_id", flowId);
        }
        if (StringUtils.hasText(xTransactionId)) out.put("transactionId", xTransactionId);
        if (StringUtils.hasText(xInvoice)) out.put("invoice", xInvoice);
        if (StringUtils.hasText(xAmount)) out.put("amount", xAmount);
        if (StringUtils.hasText(xCurrency)) out.put("currency", xCurrency);
        if (StringUtils.hasText(xDocumento)) out.put("document", xDocumento);
        if (StringUtils.hasText(xEmail)) out.put("email", xEmail);
        if (StringUtils.hasText(xPhone)) out.put("phone", xPhone);
        if (StringUtils.hasText(paymentMethod)) out.put("paymentMethod", paymentMethod);
        if (StringUtils.hasText(paymentNote)) out.put("paymentNote", paymentNote);
        if (StringUtils.hasText(maskedDocumentRaw)) out.put("_maskedDocumentRaw", maskedDocumentRaw);
        if (StringUtils.hasText(maskedEmailRaw)) out.put("_maskedEmailRaw", maskedEmailRaw);
        if (StringUtils.hasText(maskedPhoneRaw)) out.put("_maskedPhoneRaw", maskedPhoneRaw);

        Map<String, String> gateway = new LinkedHashMap<>();
        if (StringUtils.hasText(xCodResponse)) gateway.put("codResponse", xCodResponse);
        if (StringUtils.hasText(xResponse)) gateway.put("response", xResponse);
        if (StringUtils.hasText(xReason)) gateway.put("reason", xReason);
        if (StringUtils.hasText(gatewayRefPayco)) gateway.put("xRefPayco", gatewayRefPayco);
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
        if (data.isObject()) {
            JsonNode nestedData = data.path("data");
            if (nestedData.isArray() && nestedData.size() > 0) {
                JsonNode firstNested = nestedData.get(0);
                if (firstNested != null && firstNested.isObject()) {
                    return firstNested;
                }
            }
            if (nestedData.isObject()) {
                return nestedData;
            }
        }
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

    private String readFirstField(JsonNode first, JsonNode second, String... fieldNames) {
        if (fieldNames == null) return "";
        for (String fieldName : fieldNames) {
            String fromFirst = readField(first, fieldName);
            if (StringUtils.hasText(fromFirst)) return fromFirst;
            String fromSecond = readField(second, fieldName);
            if (StringUtils.hasText(fromSecond)) return fromSecond;
        }
        return "";
    }

    private String resolveLookupRefPayco(String... candidates) {
        String fallback = "";
        if (candidates == null) return fallback;
        for (String candidate : candidates) {
            String value = safeTrim(candidate);
            if (!StringUtils.hasText(value)) continue;
            if (!StringUtils.hasText(fallback)) {
                fallback = value;
            }
            if (!looksLikeGatewayReference(value)) {
                return value;
            }
        }
        return fallback;
    }

    private boolean looksLikeGatewayReference(String valueRaw) {
        String value = safeTrim(valueRaw);
        return StringUtils.hasText(value) && value.matches("^\\d{6,}$");
    }

    private String normalizeDocumentoCandidate(String raw) {
        String value = safeTrim(raw);
        if (!StringUtils.hasText(value) || value.contains("*")) {
            return "";
        }
        String digits = value.replaceAll("\\D+", "");
        if (digits.length() < 5) {
            return "";
        }
        return digits;
    }

    private String sanitizeEmailCandidate(String raw) {
        String value = safeTrim(raw).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(value) || value.contains("*")) {
            return "";
        }
        if (!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return "";
        }
        return value;
    }

    private String sanitizePhoneCandidate(String raw) {
        String value = safeTrim(raw);
        if (!StringUtils.hasText(value) || value.contains("*")) {
            return "";
        }
        String normalized = value.replaceAll("[^0-9+]", "");
        if (normalized.startsWith("00")) {
            normalized = normalized.substring(2);
        }
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        String digits = normalized.replaceAll("\\D+", "");
        if (digits.length() < 8) {
            return "";
        }
        if (normalized.startsWith("+")) {
            return "+" + digits;
        }
        return digits;
    }

    private String firstResolvedDocument(String... candidates) {
        if (candidates == null) return "";
        for (String candidate : candidates) {
            String normalized = normalizeDocumentoCandidate(candidate);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return "";
    }

    private String firstResolvedEmail(String... candidates) {
        if (candidates == null) return "";
        for (String candidate : candidates) {
            String normalized = sanitizeEmailCandidate(candidate);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return "";
    }

    private String firstResolvedPhone(String... candidates) {
        if (candidates == null) return "";
        for (String candidate : candidates) {
            String normalized = sanitizePhoneCandidate(candidate);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return "";
    }

    private Optional<ChatbotMatriculaProceso> resolveProcesoFromHints(Long flowIdHint,
                                                                      String documentHintRaw,
                                                                      String emailHintRaw) {
        if (flowIdHint != null) {
            Optional<ChatbotMatriculaProceso> procesoById = chatbotProcesoService.findProcesoById(flowIdHint);
            if (procesoById.isPresent()) {
                return procesoById;
            }
        }

        String documentHint = normalizeDocumentoCandidate(documentHintRaw);
        if (StringUtils.hasText(documentHint)) {
            Optional<ChatbotMatriculaProceso> procesoByDocumento =
                    chatbotProcesoService.findProcesoByDocumento(documentHint);
            if (procesoByDocumento.isPresent()) {
                return procesoByDocumento;
            }
        }

        String emailHint = sanitizeEmailCandidate(emailHintRaw);
        if (StringUtils.hasText(emailHint)) {
            return chatbotProcesoService.findLatestProcesoByEmail(emailHint);
        }

        return Optional.empty();
    }

    private String procesoDocumento(Optional<ChatbotMatriculaProceso> procesoOpt) {
        return procesoOpt
                .map(ChatbotMatriculaProceso::getNumeroDocumento)
                .map(this::safeTrim)
                .orElse("");
    }

    private String procesoEmail(Optional<ChatbotMatriculaProceso> procesoOpt) {
        return procesoOpt
                .map(ChatbotMatriculaProceso::getEmail)
                .map(this::safeTrim)
                .orElse("");
    }

    private String procesoTelefono(Optional<ChatbotMatriculaProceso> procesoOpt) {
        if (procesoOpt.isEmpty()) {
            return "";
        }
        ChatbotMatriculaProceso proceso = procesoOpt.get();
        return firstResolvedPhone(safeTrim(proceso.getPhone()), safeTrim(proceso.getTelefono()));
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

    private String firstMaskedValue(String... values) {
        if (values == null) return "";
        for (String value : values) {
            String out = safeTrim(value);
            if (StringUtils.hasText(out) && out.contains("*")) {
                return out;
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

    // URL de confirmacion (Webhook ePayco)
   @PostMapping(value = {"/confirmation", "/epayco/confirmation"}, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> confirmation(@RequestParam MultiValueMap<String, String> form) {

        String xRefPayco = form.getFirst("x_ref_payco");
        String xTransactionId = form.getFirst("x_transaction_id");
        String xAmount = form.getFirst("x_amount");
        String xCurrencyCode = form.getFirst("x_currency_code");
        String xCodResponse = form.getFirst("x_cod_response");
        String xSignature = form.getFirst("x_signature");
        Long flowId = resolveFlowId(
                form.getFirst("flow_id"),
                form.getFirst("flowId"),
                form.getFirst("process_id"),
                form.getFirst("processId"),
                form.getFirst("matricula_id"),
                form.getFirst("matriculaId"),
                form.getFirst("x_extra2"),
                form.getFirst("extra2")
        );
        String xDocumento = firstResolvedDocument(
                form.getFirst("x_extra1"),
                form.getFirst("document"),
                form.getFirst("customer_document"),
                form.getFirst("x_customer_document")
        );
        String estado = form.getFirst("x_response");
        String xReason = form.getFirst("x_response_reason_text");
        String xInvoice = firstNotBlank(form.getFirst("x_id_invoice"), form.getFirst("x_id_factura"), form.getFirst("invoice"));
        String xEmail = firstResolvedEmail(
                form.getFirst("email"),
                form.getFirst("customer_email"),
                form.getFirst("x_customer_email")
        );
        String xPhone = firstResolvedPhone(
                form.getFirst("phone"),
                form.getFirst("customer_phone"),
                form.getFirst("x_customer_phone"),
                form.getFirst("x_customer_mobile"),
                form.getFirst("x_customer_movil"),
                form.getFirst("customer_mobile"),
                form.getFirst("mobile")
        );
        String paymentMethodHint = normalizePaymentMethodHint(firstNotBlank(
                form.getFirst("payment_method"),
                form.getFirst("paymentMethod"),
                form.getFirst("method"),
                form.getFirst("x_payment_method"),
                form.getFirst("x_franchise"),
                form.getFirst("franchise"),
                form.getFirst("x_bank_name"),
                form.getFirst("bank_name")
        ));
        String paymentNoteHint = buildPaymentNoteHint(
                form.getFirst("payment_note"),
                form.getFirst("paymentNote"),
                form.getFirst("x_bank_name"),
                form.getFirst("bank_name"),
                form.getFirst("x_franchise"),
                form.getFirst("franchise")
        );
        Optional<ChatbotMatriculaProceso> procesoByFlowId = flowId == null
                ? Optional.empty()
                : chatbotProcesoService.findProcesoById(flowId);
        if (procesoByFlowId.isPresent()) {
            ChatbotMatriculaProceso proceso = procesoByFlowId.get();
            xDocumento = firstResolvedDocument(xDocumento, safeTrim(proceso.getNumeroDocumento()));
            xEmail = firstResolvedEmail(xEmail, safeTrim(proceso.getEmail()));
            xPhone = firstResolvedPhone(xPhone, safeTrim(proceso.getPhone()), safeTrim(proceso.getTelefono()));
        }

        log.info("CONFIRM webhook ref={} trx={} flowId={} doc={} cod={} estado={} amount={} signaturePresent={}",
                xRefPayco, xTransactionId, flowId, xDocumento, xCodResponse, estado, xAmount, xSignature != null && !xSignature.isBlank());

            boolean signatureOk =
            epaycoService.isValidSignature(xRefPayco, xTransactionId, xAmount, xCurrencyCode, xSignature);

            if (!signatureOk) {
                log.warn("Firma inválida en confirmación ePayco ref={} trx={} doc={} cod={} estado={} amount={}",
                        xRefPayco, xTransactionId, xDocumento, xCodResponse, estado, xAmount);
                return ResponseEntity.badRequest().body(Map.of("error", "Firma invalida"));
            }

            captureTemporaryContext(
                    "",
                    xRefPayco,
                    xInvoice,
                    xTransactionId,
                    xDocumento,
                    xEmail,
                    xPhone,
                    estado,
                    "confirmation_webhook",
                    flowId
            );

            if (!StringUtils.hasText(normalizeDocumentoCandidate(xDocumento))) {
                Optional<PaymentSyncContextService.ResolvedContext> staged = paymentSyncContextService.resolve(
                        "",
                        xRefPayco,
                        xInvoice,
                        xTransactionId
                );
                if (staged.isPresent()) {
                    PaymentSyncContextService.ResolvedContext ctx = staged.get();
                    xDocumento = firstResolvedDocument(xDocumento, ctx.document());
                    xEmail = firstResolvedEmail(xEmail, ctx.email());
                    xPhone = firstResolvedPhone(xPhone, ctx.phone());
                    if (flowId == null && ctx.flowId() != null && ctx.flowId() > 0) {
                        flowId = ctx.flowId();
                    }
                }
            }
            if (!StringUtils.hasText(normalizeDocumentoCandidate(xDocumento)) && flowId != null) {
                Optional<ChatbotMatriculaProceso> procesoFromFlow = chatbotProcesoService.findProcesoById(flowId);
                if (procesoFromFlow.isPresent()) {
                    ChatbotMatriculaProceso proceso = procesoFromFlow.get();
                    xDocumento = firstResolvedDocument(xDocumento, safeTrim(proceso.getNumeroDocumento()));
                    xEmail = firstResolvedEmail(xEmail, safeTrim(proceso.getEmail()));
                    xPhone = firstResolvedPhone(xPhone, safeTrim(proceso.getPhone()), safeTrim(proceso.getTelefono()));
                }
            }

            capturePaymentMetadataIfPossible(xDocumento, paymentMethodHint, paymentNoteHint);

            if (isApproved(estado, xCodResponse)) {
            log.info("💰 Pago aprobado doc={} ref={} amount={}", xDocumento, xRefPayco, xAmount);
            try {
                PaymentApprovalService.ApprovalResult approval =
                         paymentApprovalService.handleApprovedPayment(xDocumento, parseAmountOrNull(xAmount), true);
                log.info("✅ Pago aprobado procesado doc={} paymentStatus={} flowStatus={} contractLinkPresent={}",
                        xDocumento,
                        approval.paymentStatus(),
                        approval.flowStatus(),
                        StringUtils.hasText(approval.contractLink()));
            } catch (Exception ex) {
                log.error("❌ Error procesando pago aprobado doc={} ref={}: {}", xDocumento, xRefPayco, ex.getMessage(), ex);
            }

        } else if (isCancelled(estado, xCodResponse)) {
            log.info("❌ Pago cancelado doc={} ref={} cod={} estado={}", xDocumento, xRefPayco, xCodResponse, estado);
            processNonApprovedPayment(xDocumento, xRefPayco, xCodResponse, estado, xReason, PaymentUserStatus.CANCELLED);

        } else if (isRejected(estado, xCodResponse)) {
            log.info("🚫 Pago rechazado doc={} ref={} cod={} estado={}", xDocumento, xRefPayco, xCodResponse, estado);
            processNonApprovedPayment(xDocumento, xRefPayco, xCodResponse, estado, xReason, PaymentUserStatus.REJECTED);

        } else if (isPending(estado, xCodResponse, xReason)) {
            log.info("⏳ Pago pendiente doc={} ref={} cod={} estado={}", xDocumento, xRefPayco, xCodResponse, estado);
            processNonApprovedPayment(xDocumento, xRefPayco, xCodResponse, estado, xReason, PaymentUserStatus.PENDING);

        } else {
            log.info("ℹ️ Estado no reconocido en confirmación doc={} ref={} cod={} estado={}",
                    xDocumento, xRefPayco, xCodResponse, estado);
        }

        if (StringUtils.hasText(normalizeDocumentoCandidate(xDocumento))
                && (isApproved(estado, xCodResponse) || isCancelled(estado, xCodResponse) || isRejected(estado, xCodResponse))) {
            paymentSyncContextService.clearByReferences("", xRefPayco, xInvoice, xTransactionId);
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private void processApprovedPayment(String documentoRaw, String amountRaw) {
        processApprovedPayment(documentoRaw, parseAmountOrNull(amountRaw), true, false, "", "");
    }

    private void capturePaymentMetadataIfPossible(String documentoRaw, String paymentMethodRaw, String paymentNoteRaw) {
        String documento = normalizeDocumentoCandidate(documentoRaw);
        String paymentMethod = normalizePaymentMethodHint(paymentMethodRaw);
        String paymentNote = buildPaymentNoteHint(paymentNoteRaw);
        if (!StringUtils.hasText(documento) || (!StringUtils.hasText(paymentMethod) && !StringUtils.hasText(paymentNote))) {
            return;
        }
        try {
            chatbotProcesoService.capturePaymentMetadataByDocument(documento, paymentMethod, paymentNote);
        } catch (Exception ex) {
            log.debug("No se pudo guardar metadata de pago para doc={}: {}", documento, ex.getMessage());
        }
    }

    private String normalizePaymentMethodHint(String raw) {
        String normalized = safeTrim(raw).toUpperCase(Locale.ROOT)
                .replace('Á', 'A')
                .replace('É', 'E')
                .replace('Í', 'I')
                .replace('Ó', 'O')
                .replace('Ú', 'U')
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (normalized.contains("PSE") || normalized.contains("BANK") || normalized.contains("BANCO")) return "PSE";
        if (normalized.contains("CREDITO")) return "TARJETA_CREDITO";
        if (normalized.contains("DEBITO")) return "TARJETA_DEBITO";
        if (normalized.contains("TARJETA") || normalized.contains("VISA") || normalized.contains("MASTERCARD") || normalized.contains("AMEX")) return "TARJETA";
        if (normalized.contains("TRANSFER")) return "TRANSFERENCIA";
        if (normalized.contains("NEQUI") || normalized.contains("DAVIPLATA") || normalized.contains("BILLETERA")) return "BILLETERA_DIGITAL";
        if (normalized.contains("EFECTIVO")) return "EFECTIVO";
        return normalized;
    }

    private String buildPaymentNoteHint(String... values) {
        LinkedHashMap<String, Boolean> unique = new LinkedHashMap<>();
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String current = safeTrim(value);
            if (StringUtils.hasText(current)) {
                unique.put(current, Boolean.TRUE);
            }
        }
        if (unique.isEmpty()) {
            return "";
        }
        return String.join(" | ", unique.keySet());
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
        try {
            paymentApprovalService.handleApprovedPayment(documento, amount, notifyContractLinkByChatbot);
        } catch (Exception ex) {
            log.error("No se pudo procesar pago aprobado doc={}: {}", documento, ex.getMessage(), ex);
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

        Optional<ChatbotMatriculaProceso> currentOpt = chatbotProcesoService.findProcesoByDocumento(documento);
        if (currentOpt.isEmpty()) {
            log.warn("No existe proceso de matricula para notificar pago no aprobado doc={} ref={}", documento, xRefPayco);
            return;
        }

        ChatbotMatriculaProceso proceso = currentOpt.get();
        String phone = safeTrim(proceso.getPhone());
        if (!StringUtils.hasText(phone)) {
            phone = safeTrim(proceso.getTelefono());
        }
        if (!StringUtils.hasText(phone)) {
            log.warn("Pago no aprobado doc={} sin telefono para notificar. ref={}", documento, xRefPayco);
            return;
        }

        if (!waService.getConfigStatus().ready()) {
            log.warn("Pago no aprobado doc={} pero WhatsApp no esta listo: {}", documento, waService.getConfigStatus().message());
            return;
        }

        if (shouldSkipPaymentStatusNotification(proceso, status)) {
            log.info("Notificacion de pago omitida (ya enviada recientemente) doc={} status={} ref={}",
                    documento, status.code, xRefPayco);
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

        String msg = buildPaymentStatusNotificationMessage(proceso, status, detail, paymentLink);

        try {
            waService.sendTextMessage(phone, msg);
            try {
                chatbotProcesoService.markPaymentStatusNotified(documento, status.code);
            } catch (Exception ignored) {
                // best-effort: no bloquea el flujo por falla guardando marca anti-spam.
            }
            log.info("Notificacion de pago no aprobado enviada doc={} to={} status={} ref={}",
                    documento, maskPhone(phone), status.code, xRefPayco);
        } catch (Exception ex) {
            log.error("No se pudo enviar notificacion de pago no aprobado doc={} to={} status={} ref={}: {}",
                    documento, maskPhone(phone), status.code, xRefPayco, ex.getMessage(), ex);
        }
    }

    private String buildPaymentStatusNotificationMessage(ChatbotMatriculaProceso proceso,
                                                         PaymentUserStatus status,
                                                         String detailRaw,
                                                         String paymentLinkRaw) {
        String detail = safeTrim(detailRaw);
        String paymentLink = safeTrim(paymentLinkRaw);
        String studentName = resolveStudentDisplayName(proceso);

        StringBuilder msg = new StringBuilder();
        if (StringUtils.hasText(studentName)) {
            msg.append("Hola ").append(studentName).append(".\n\n");
        }

        if (status == PaymentUserStatus.PENDING) {
            msg.append("⏳ Tu pago aparece como *PENDIENTE*.");
            if (StringUtils.hasText(detail)) {
                msg.append("\n🧾 Detalle: ").append(detail);
            }
            msg.append("\n\nEsto puede tardar unos minutos dependiendo del banco.");
            msg.append("\nCuando sea aprobado, te enviaremos automáticamente el enlace para firmar los contratos.");
            if (StringUtils.hasText(paymentLink)) {
                msg.append("\n\n🔁 Si necesitas el enlace nuevamente, aquí lo tienes:\n").append(paymentLink);
            }
            msg.append("\n\nℹ️ No necesitas hacer nada por ahora. Si el estado no cambia luego de unos minutos, escribe ASESOR.");
        } else {
            String headline = status == PaymentUserStatus.CANCELLED
                    ? "❌ Tu pago fue cancelado o no finalizado."
                    : "🚫 Tu pago no fue aprobado.";

            msg.append(headline);
            if (StringUtils.hasText(detail)) {
                msg.append("\n🧾 Detalle: ").append(detail);
            }
            if (StringUtils.hasText(paymentLink)) {
                msg.append("\n\n🔁 Puedes reintentar aquí:\n").append(paymentLink);
            }
        }

        msg.append(ADVISOR_PROMPT);
        return msg.toString();
    }

    private String resolveStudentDisplayName(ChatbotMatriculaProceso proceso) {
        if (proceso == null) {
            return "";
        }
        String nombre = safeTrim(proceso.getNombreCompleto());
        if (!StringUtils.hasText(nombre)) {
            return "";
        }
        return nombre.replaceAll("\\s+", " ");
    }

    private boolean shouldSkipPaymentStatusNotification(ChatbotMatriculaProceso proceso, PaymentUserStatus status) {
        if (proceso == null || status == null) {
            return false;
        }
        String last = safeTrim(proceso.getPaymentStatusNotified()).toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(last) || !last.equalsIgnoreCase(status.code)) {
            return false;
        }
        Instant at = proceso.getPaymentStatusNotifiedAt();
        if (at == null) {
            return false;
        }
        long cooldownMinutes = (status == PaymentUserStatus.PENDING) ? 15L : 60L;
        return Instant.now().isBefore(at.plus(cooldownMinutes, ChronoUnit.MINUTES));
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
        String contractLink = normalizeStoredContractLink(contractLinkRaw);
        if (!StringUtils.hasText(contractLink)) {
            return "✅ Pago recibido.";
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

        return "✅ Pago aprobado.\n\n📄 Continúa con la contratación en este enlace:\n"
                + contractLink
                + "\n\n🤝 Si necesitas ayuda, escribe ASESOR.";
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

        String ui = resolveContractUiUrl(out);
        if (!StringUtils.hasText(ui)) return safeTrim(out.url());

        String link = appendQueryParam(ui, "email", out.email());
        link = appendQueryParam(link, "code", out.code());
        if (looksLikeBackendBaseUrl(contractBaseUrl)) link = appendQueryParam(link, "apiBase", safeTrim(contractBaseUrl));
        return link;
    }

    private String resolveContractUiUrl(VerificationService.ContractLinkResult out) {
        String ui = normalizeContractUiUrl(contractUiUrl);
        if (StringUtils.hasText(ui)) return ui;

        String base = safeTrim(contractBaseUrl);
        if (StringUtils.hasText(base) && !looksLikeBackendBaseUrl(base)) {
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            return base + "/Contratos/contrato.html";
        }

        String verificationUrl = out == null ? "" : safeTrim(out.url());
        if (!StringUtils.hasText(verificationUrl)) return "";
        try {
            java.net.URL u = new java.net.URL(verificationUrl);
            return u.getProtocol() + "://" + u.getAuthority() + "/Contratos/contrato.html";
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean looksLikeBackendBaseUrl(String raw) {
        String v = safeTrim(raw).toLowerCase(java.util.Locale.ROOT);
        if (!StringUtils.hasText(v)) return false;
        return v.contains("run.app")
                || v.contains("localhost")
                || v.matches(".*:\\d{2,5}$");
    }

    private VerificationService.ContractAccessResult peekContractLinkAccess(String contractLinkRaw) {
        ContractAccessParams params = parseContractAccessParams(contractLinkRaw);
        if (!StringUtils.hasText(params.email()) || !StringUtils.hasText(params.code())) {
            return new VerificationService.ContractAccessResult(false, "Codigo requerido", null);
        }
        return verificationService.peekContractAccessCode(params.email(), params.code());
    }

    private ContractAccessParams parseContractAccessParams(String contractLinkRaw) {
        String link = safeTrim(contractLinkRaw);
        if (!StringUtils.hasText(link)) {
            return new ContractAccessParams("", "");
        }
        return new ContractAccessParams(
                readQueryParam(link, "email"),
                readQueryParam(link, "code")
        );
    }

    private String readQueryParam(String urlRaw, String keyRaw) {
        String url = safeTrim(urlRaw);
        String key = safeTrim(keyRaw);
        if (!StringUtils.hasText(url) || !StringUtils.hasText(key)) {
            return "";
        }
        try {
            URI uri = URI.create(url);
            String rawQuery = uri.getRawQuery();
            if (!StringUtils.hasText(rawQuery)) {
                return "";
            }
            for (String part : rawQuery.split("&")) {
                if (!StringUtils.hasText(part)) continue;
                int idx = part.indexOf('=');
                String k = idx >= 0 ? part.substring(0, idx) : part;
                String v = idx >= 0 ? part.substring(idx + 1) : "";
                String dk = URLDecoder.decode(k, StandardCharsets.UTF_8);
                if (!dk.equalsIgnoreCase(key)) continue;
                return URLDecoder.decode(v, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private boolean shouldRefreshContractLink(String contractLinkRaw) {
        String contractLink = normalizeStoredContractLink(contractLinkRaw);
        if (!StringUtils.hasText(contractLink)) {
            return true;
        }

        // Regenerar si el codigo del link ya no es vigente (expirado/consumido/no encontrado).
        try {
            VerificationService.ContractAccessResult peek = peekContractLinkAccess(contractLink);
            if (peek == null || !peek.ok()) {
                return true;
            }
        } catch (Exception ex) {
            // Si falla el "peek", no forzamos refresh para evitar loops.
        }

        String expectedUi = normalizeContractUiUrl(contractUiUrl);
        if (!StringUtils.hasText(expectedUi)) {
            return false;
        }
        return !contractLink.startsWith(expectedUi);
    }

    private String normalizeContractUiUrl(String rawUiUrl) {
        String ui = safeTrim(rawUiUrl);
        if (!StringUtils.hasText(ui)) {
            return ui;
        }
        return ui.replaceFirst("(?i)(?:/Contratos)*/contrato\\.html(?=($|[?#]))", "/Contratos/contrato.html");
    }

    private String resolveContractLinkForProceso(ChatbotMatriculaProceso proceso) {
        if (proceso == null) {
            return "";
        }

        String currentLink = normalizeStoredContractLink(proceso.getContractLink());
        String document = normalizeDocumentoCandidate(proceso.getNumeroDocumento());
        if (StringUtils.hasText(document)
                && !currentLink.equals(safeTrim(proceso.getContractLink()))) {
            try {
                chatbotProcesoService.updateContractLink(document, currentLink);
            } catch (Exception ex) {
                log.warn("No se pudo normalizar contractLink almacenado doc={}: {}", document, ex.getMessage());
            }
        }
        if (!shouldRefreshContractLink(currentLink)) {
            return currentLink;
        }

        String email = safeTrim(proceso.getEmail());
        if (!StringUtils.hasText(email) || !StringUtils.hasText(document)) {
            return currentLink;
        }

        try {
            VerificationService.ContractLinkResult out =
                    verificationService.createContractVerificationLink(email, contractBaseUrl);
            String refreshedLink = buildContractUserLink(out);
            if (StringUtils.hasText(refreshedLink)) {
                chatbotProcesoService.markContractLinkSent(document, refreshedLink);
                return refreshedLink;
            }
        } catch (Exception ex) {
            log.warn("No se pudo refrescar link de contrato doc={}: {}", document, ex.getMessage());
        }

        return currentLink;
    }

    private String normalizeStoredContractLink(String rawContractLink) {
        return normalizeContractUiUrl(safeTrim(rawContractLink));
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

    private void captureTemporaryContext(String lookupReferenceRaw,
                                         String gatewayReferenceRaw,
                                         String invoiceRaw,
                                         String transactionIdRaw,
                                         String documentRaw,
                                         String emailRaw,
                                         String phoneRaw,
                                         String statusRaw,
                                         String sourceRaw,
                                         Long flowIdRaw) {
        String lookupReference = resolveLookupRefPayco(
                safeTrim(lookupReferenceRaw),
                safeTrim(gatewayReferenceRaw)
        );
        String gatewayReference = safeTrim(gatewayReferenceRaw);
        String invoice = safeTrim(invoiceRaw);
        String transactionId = safeTrim(transactionIdRaw);
        String document = normalizeDocumentoCandidate(documentRaw);
        String email = sanitizeEmailCandidate(emailRaw);
        String phone = sanitizePhoneCandidate(phoneRaw);
        String status = safeTrim(statusRaw);
        String source = safeTrim(sourceRaw);
        Long flowId = flowIdRaw != null && flowIdRaw > 0 ? flowIdRaw : null;

        try {
            paymentSyncContextService.capture(
                    lookupReference,
                    gatewayReference,
                    invoice,
                    transactionId,
                    document,
                    email,
                    phone,
                    status,
                    source,
                    flowId
            );
        } catch (Exception ex) {
            log.warn("No se pudo guardar contexto temporal de sync. ref={} gatewayRef={} invoice={} trx={}: {}",
                    lookupReference,
                    gatewayReference,
                    invoice,
                    transactionId,
                    ex.getMessage());
        }
    }

    private Map<String, Object> mutableMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
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
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (!value.matches("^\\d{1,18}$")) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseFlowIdFromInvoiceHint(String invoiceRaw) {
        String invoice = safeTrim(invoiceRaw).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(invoice)) {
            return null;
        }
        String value = invoice;
        if (value.startsWith("flow-")) {
            value = value.substring("flow-".length());
        } else if (value.startsWith("flow_")) {
            value = value.substring("flow_".length());
        } else if (value.startsWith("flow:")) {
            value = value.substring("flow:".length());
        } else {
            return null;
        }
        return parseFlowIdCandidate(value);
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

