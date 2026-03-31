package com.Aplication.HARO.Service;

import com.Aplication.HARO.Repository.ChatbotContractCategoryProgressRepository;
import com.Aplication.HARO.Repository.ChatbotMatriculaProcesoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ServicioLimpiezaSolicitudesEfectivo {

    private static final Logger log = LoggerFactory.getLogger(ServicioLimpiezaSolicitudesEfectivo.class);

    private final ChatbotMatriculaProcesoRepository procesoRepository;
    private final ChatbotContractCategoryProgressRepository contractCategoryProgressRepository;

    @Value("${chatbot.cash-request.cleanup.enabled:true}")
    private boolean enabled;

    @Value("${chatbot.cash-request.ttl-minutes:60}")
    private long ttlMinutes;

    public ServicioLimpiezaSolicitudesEfectivo(ChatbotMatriculaProcesoRepository procesoRepository,
                                               ChatbotContractCategoryProgressRepository contractCategoryProgressRepository) {
        this.procesoRepository = procesoRepository;
        this.contractCategoryProgressRepository = contractCategoryProgressRepository;
    }

    /**
     * Removes stale cash enrollment requests:
     * - flowStatus=PENDING_CASH_VALIDATION
     * - metodoPago=EFECTIVO
     * - paymentStatus=PENDING
     * - updatedAt older than ttlMinutes
     *
     * Note: We delete contract category progress rows first to avoid FK issues.
     */
    @Transactional
    public int cleanupExpiredCashRequests() {
        if (!enabled) {
            return 0;
        }

        long ttl = Math.max(1L, ttlMinutes);
        Instant threshold = Instant.now().minus(ttl, ChronoUnit.MINUTES);

        List<Long> ids = procesoRepository.findIdsForCashPendingCleanup(
                "PENDING_CASH_VALIDATION",
                "EFECTIVO",
                "PENDING",
                threshold
        );
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        try {
            contractCategoryProgressRepository.deleteByProcesoIdIn(ids);
        } catch (Exception ex) {
            // Best-effort: do not crash the app. If FK exists, the process delete below may fail and will be logged.
            log.warn("Cash cleanup: could not delete contract progress rows idsCount={} err={}", ids.size(), ex.getMessage());
        }

        try {
            procesoRepository.deleteAllByIdInBatch(ids);
        } catch (Exception ex) {
            log.warn("Cash cleanup: could not delete procesos idsCount={} err={}", ids.size(), ex.getMessage());
            return 0;
        }

        return ids.size();
    }
}
