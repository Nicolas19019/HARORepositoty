package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.PaymentSyncContext;
import com.Aplication.HARO.Repository.PaymentSyncContextRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class PaymentSyncContextService {

    private static final Logger log = LoggerFactory.getLogger(PaymentSyncContextService.class);

    public record ResolvedContext(String document, String email, String phone, Long flowId) {}

    private final PaymentSyncContextRepository repository;

    @Value("${epayco.sync.context.ttl-minutes:1440}")
    private long ttlMinutes;

    public PaymentSyncContextService(PaymentSyncContextRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void capture(String lookupReferenceRaw,
                        String gatewayReferenceRaw,
                        String invoiceRaw,
                        String transactionIdRaw,
                        String documentRaw,
                        String emailRaw,
                        String phoneRaw,
                        String statusRaw,
                        String sourceRaw,
                        Long flowIdRaw) {
        String lookupReference = trim(lookupReferenceRaw);
        String gatewayReference = trim(gatewayReferenceRaw);
        String invoice = trim(invoiceRaw);
        String transactionId = trim(transactionIdRaw);
        String document = normalizeDocument(documentRaw);
        String email = normalizeEmail(emailRaw);
        String phone = normalizePhone(phoneRaw);
        String status = trim(statusRaw).toUpperCase(Locale.ROOT);
        String source = trim(sourceRaw);
        Long flowId = (flowIdRaw != null && flowIdRaw > 0) ? flowIdRaw : null;

        boolean hasReference = StringUtils.hasText(lookupReference)
                || StringUtils.hasText(gatewayReference)
                || StringUtils.hasText(invoice)
                || StringUtils.hasText(transactionId);
        boolean hasIdentity = StringUtils.hasText(document)
                || StringUtils.hasText(email)
                || StringUtils.hasText(phone);
        if (!hasReference || (!hasIdentity && flowId == null)) {
            return;
        }

        repository.deleteExpired(Instant.now());

        PaymentSyncContext ctx = new PaymentSyncContext();
        ctx.setLookupReference(lookupReference);
        ctx.setGatewayReference(gatewayReference);
        ctx.setInvoice(invoice);
        ctx.setTransactionId(transactionId);
        ctx.setFlowId(flowId);
        ctx.setDocument(document);
        ctx.setEmail(email);
        ctx.setPhone(phone);
        ctx.setStatus(status);
        ctx.setSource(source);
        ctx.setExpiresAt(Instant.now().plusSeconds(Math.max(60, ttlMinutes) * 60));
        repository.save(ctx);
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedContext> resolve(String lookupReferenceRaw,
                                             String gatewayReferenceRaw,
                                             String invoiceRaw,
                                             String transactionIdRaw) {
        String lookupReference = trim(lookupReferenceRaw);
        String gatewayReference = trim(gatewayReferenceRaw);
        String invoice = trim(invoiceRaw);
        String transactionId = trim(transactionIdRaw);

        if (!StringUtils.hasText(lookupReference)
                && !StringUtils.hasText(gatewayReference)
                && !StringUtils.hasText(invoice)
                && !StringUtils.hasText(transactionId)) {
            return Optional.empty();
        }

        List<PaymentSyncContext> candidates = repository.findActiveCandidates(
                lookupReference,
                gatewayReference,
                invoice,
                transactionId,
                Instant.now(),
                PageRequest.of(0, 50)
        );
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        String document = "";
        String email = "";
        String phone = "";
        Long flowId = null;
        for (PaymentSyncContext candidate : candidates) {
            if (!StringUtils.hasText(document)) document = trim(candidate.getDocument());
            if (!StringUtils.hasText(email)) email = trim(candidate.getEmail());
            if (!StringUtils.hasText(phone)) phone = trim(candidate.getPhone());
            if (flowId == null && candidate.getFlowId() != null && candidate.getFlowId() > 0) {
                flowId = candidate.getFlowId();
            }
            if (StringUtils.hasText(document) && StringUtils.hasText(email) && StringUtils.hasText(phone) && flowId != null) {
                break;
            }
        }

        if (!StringUtils.hasText(document) && !StringUtils.hasText(email) && !StringUtils.hasText(phone) && flowId == null) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedContext(document, email, phone, flowId));
    }

    @Transactional
    public int clearByReferences(String lookupReferenceRaw,
                                 String gatewayReferenceRaw,
                                 String invoiceRaw,
                                 String transactionIdRaw) {
        String lookupReference = trim(lookupReferenceRaw);
        String gatewayReference = trim(gatewayReferenceRaw);
        String invoice = trim(invoiceRaw);
        String transactionId = trim(transactionIdRaw);
        if (!StringUtils.hasText(lookupReference)
                && !StringUtils.hasText(gatewayReference)
                && !StringUtils.hasText(invoice)
                && !StringUtils.hasText(transactionId)) {
            return 0;
        }
        return repository.deleteByAnyReference(lookupReference, gatewayReference, invoice, transactionId);
    }

    @Transactional
    public int purgeExpired() {
        int removed = repository.deleteExpired(Instant.now());
        if (removed > 0) {
            log.info("Payment sync context: {} registros expirados eliminados", removed);
        }
        return removed;
    }

    private String normalizeDocument(String raw) {
        String value = trim(raw);
        if (!StringUtils.hasText(value) || value.contains("*")) return "";
        String digits = value.replaceAll("\\D+", "");
        return digits.length() >= 5 ? digits : "";
    }

    private String normalizeEmail(String raw) {
        String value = trim(raw).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(value) || value.contains("*")) return "";
        return value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ? value : "";
    }

    private String normalizePhone(String raw) {
        String value = trim(raw);
        if (!StringUtils.hasText(value) || value.contains("*")) return "";
        String normalized = value.replaceAll("[^0-9+]", "");
        if (normalized.startsWith("00")) normalized = normalized.substring(2);
        String digits = normalized.replaceAll("\\D+", "");
        if (digits.length() < 8) return "";
        return normalized.startsWith("+") ? "+" + digits : digits;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
