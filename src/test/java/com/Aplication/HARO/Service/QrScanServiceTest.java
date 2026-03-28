package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.QrScan;
import com.Aplication.HARO.Repository.QrScanRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QrScanServiceTest {

    @Test
    void registerDebeAplicarValoresPorDefecto() {
        QrScanRepository repository = mock(QrScanRepository.class);
        when(repository.save(any(QrScan.class))).thenAnswer(invocation -> {
            QrScan row = invocation.getArgument(0);
            row.setId(1L);
            row.setCreatedAt(Instant.parse("2026-03-27T15:00:00Z"));
            return row;
        });

        QrScanService service = new QrScanService(repository);

        QrScanService.RegisterResponse out = service.register(
                new QrScanService.RegisterRequest(null, null, null, "redirigirQR",
                        null, null, null, null, "src=qr", "https://ceaharo.com/wa/redirigirQR.html", "1.2.3.4", "UA")
        );

        assertTrue(out.ok());
        assertEquals(1L, out.id());
        assertEquals(Instant.parse("2026-03-27T15:00:00Z"), out.createdAt());
    }

    @Test
    void reportDebeAgruparPorDiaYPorCampos() {
        QrScanRepository repository = mock(QrScanRepository.class);
        when(repository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        build("qr", "whatsapp", "haro_chatbot", "redirigirQR", "QR General", "Campana Marzo", "Kennedy",
                                Instant.parse("2026-03-27T15:00:00Z")),
                        build("qr", "whatsapp", "haro_chatbot", "redirigirQR", "QR General", "Campana Marzo", "Kennedy",
                                Instant.parse("2026-03-27T16:00:00Z")),
                        build("poster", "whatsapp", "haro_chatbot", "landingQR", "QR Eden", "Campana Abril", "Eden",
                                Instant.parse("2026-03-28T16:00:00Z"))
                ));

        QrScanService service = new QrScanService(repository);
        QrScanService.ReportResponse out = service.report(LocalDate.of(2026, 3, 27), LocalDate.of(2026, 3, 28));

        assertEquals(3L, out.total());
        assertEquals(2L, out.byDay().get("2026-03-27"));
        assertEquals(1L, out.byDay().get("2026-03-28"));
        assertEquals(2L, out.bySource().get("qr"));
        assertEquals(2L, out.byPage().get("redirigirQR"));
        assertEquals(2L, out.bySede().get("Kennedy"));
    }

    private QrScan build(String source,
                         String channel,
                         String target,
                         String page,
                         String qrName,
                         String campaign,
                         String sede,
                         Instant createdAt) {
        QrScan scan = new QrScan();
        scan.setSource(source);
        scan.setChannel(channel);
        scan.setTarget(target);
        scan.setPage(page);
        scan.setQrName(qrName);
        scan.setCampaign(campaign);
        scan.setSede(sede);
        scan.setCreatedAt(createdAt);
        return scan;
    }
}
