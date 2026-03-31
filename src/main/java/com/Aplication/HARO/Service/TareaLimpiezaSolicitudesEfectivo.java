package com.Aplication.HARO.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TareaLimpiezaSolicitudesEfectivo {

    private static final Logger log = LoggerFactory.getLogger(TareaLimpiezaSolicitudesEfectivo.class);

    private final ServicioLimpiezaSolicitudesEfectivo cleanupService;

    public TareaLimpiezaSolicitudesEfectivo(ServicioLimpiezaSolicitudesEfectivo cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(
            fixedDelayString = "${chatbot.cash-request.cleanup.fixed-delay-ms:300000}",
            initialDelayString = "${chatbot.cash-request.cleanup.initial-delay-ms:60000}"
    )
    public void run() {
        try {
            int removed = cleanupService.cleanupExpiredCashRequests();
            if (removed > 0) {
                log.info("Cash cleanup removed={} requests", removed);
            }
        } catch (Exception ex) {
            log.warn("Cash cleanup job failed: {}", ex.getMessage());
        }
    }
}
