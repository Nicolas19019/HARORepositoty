package com.Aplication.HARO.Service;

import com.Aplication.HARO.Model.QrScan;
import com.Aplication.HARO.Repository.QrScanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio de metricas de codigos QR.
 *
 * Registra escaneos, cuenta eventos por rango de fechas y genera reportes para
 * seguimiento de campanas.
 */
@Service
@Transactional
public class QrScanService {

    /**
     * DTO de entrada para register.
     */
    public record RegisterRequest(
            String source,
            String channel,
            String target,
            String page,
            String qrName,
            String campaign,
            String sede,
            String location,
            String queryParams,
            String referer,
            String ipAddress,
            String userAgent
    ) {}

    /**
     * DTO de salida para register.
     */
    public record RegisterResponse(
            boolean ok,
            Long id,
            Instant createdAt
    ) {}

    /**
     * DTO de salida para count.
     */
    public record CountResponse(
            long total
    ) {}

    /**
     * DTO de salida para report.
     */
    public record ReportResponse(
            String timezone,
            String from,
            String to,
            long total,
            Map<String, Long> byDay,
            Map<String, Long> bySource,
            Map<String, Long> byChannel,
            Map<String, Long> byTarget,
            Map<String, Long> byPage,
            Map<String, Long> byCampaign,
            Map<String, Long> bySede,
            Map<String, Long> byQrName
    ) {}

    private static final ZoneId REPORT_ZONE = ZoneId.of("America/Bogota");

    private final QrScanRepository repository;

    public QrScanService(QrScanRepository repository) {
        this.repository = repository;
    }

    /**
     * Crea o registra la informacion recibida aplicando las validaciones del servicio.
     */
    public RegisterResponse register(RegisterRequest request) {
        QrScan scan = new QrScan();
        scan.setSource(normalizeSmall(request == null ? null : request.source(), "qr"));
        scan.setChannel(normalizeSmall(request == null ? null : request.channel(), "whatsapp"));
        scan.setTarget(normalizeSmall(request == null ? null : request.target(), "haro_chatbot"));
        scan.setPage(normalizeSmall(request == null ? null : request.page(), "unknown"));
        scan.setQrName(normalizeSmall(request == null ? null : request.qrName(), null));
        scan.setCampaign(normalizeSmall(request == null ? null : request.campaign(), null));
        scan.setSede(normalizeSmall(request == null ? null : request.sede(), null));
        scan.setLocation(normalizeSmall(request == null ? null : request.location(), null));
        scan.setQueryParams(normalizeLarge(request == null ? null : request.queryParams()));
        scan.setReferer(normalizeLarge(request == null ? null : request.referer()));
        scan.setIpAddress(normalizeSmall(request == null ? null : request.ipAddress(), null));
        scan.setUserAgent(normalizeLarge(request == null ? null : request.userAgent()));

        QrScan saved = repository.save(scan);
        return new RegisterResponse(true, saved.getId(), saved.getCreatedAt());
    }

    /**
     * Cuenta registros para el indicador solicitado.
     */
    @Transactional(readOnly = true)
    public CountResponse countAll() {
        return new CountResponse(repository.count());
    }

    /**
     * Cuenta registros para el indicador solicitado.
     */
    @Transactional(readOnly = true)
    public CountResponse countToday() {
        Instant from = LocalDate.now(REPORT_ZONE).atStartOfDay(REPORT_ZONE).toInstant();
        Instant to = LocalDate.now(REPORT_ZONE).plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();
        return new CountResponse(repository.countByCreatedAtBetween(from, to));
    }

    /**
     * Genera el reporte solicitado para el rango de consulta.
     */
    @Transactional(readOnly = true)
    public ReportResponse report(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now(REPORT_ZONE).minusDays(29) : from;
        LocalDate end = to == null ? LocalDate.now(REPORT_ZONE) : to;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("to no puede ser menor que from");
        }

        Instant fromInstant = start.atStartOfDay(REPORT_ZONE).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();
        List<QrScan> rows = repository.findByCreatedAtBetweenOrderByCreatedAtAsc(fromInstant, toInstant);

        Map<String, Long> byDay = new LinkedHashMap<>();
        Map<String, Long> bySource = new LinkedHashMap<>();
        Map<String, Long> byChannel = new LinkedHashMap<>();
        Map<String, Long> byTarget = new LinkedHashMap<>();
        Map<String, Long> byPage = new LinkedHashMap<>();
        Map<String, Long> byCampaign = new LinkedHashMap<>();
        Map<String, Long> bySede = new LinkedHashMap<>();
        Map<String, Long> byQrName = new LinkedHashMap<>();

        for (QrScan row : rows) {
            increment(byDay, formatDay(row.getCreatedAt()));
            increment(bySource, fallback(row.getSource(), "Sin source"));
            increment(byChannel, fallback(row.getChannel(), "Sin channel"));
            increment(byTarget, fallback(row.getTarget(), "Sin target"));
            increment(byPage, fallback(row.getPage(), "Sin page"));
            increment(byCampaign, fallback(row.getCampaign(), "Sin campaign"));
            increment(bySede, fallback(row.getSede(), "Sin sede"));
            increment(byQrName, fallback(row.getQrName(), "Sin qrName"));
        }

        return new ReportResponse(
                REPORT_ZONE.toString(),
                start.toString(),
                end.toString(),
                rows.size(),
                byDay,
                bySource,
                byChannel,
                byTarget,
                byPage,
                byCampaign,
                bySede,
                byQrName
        );
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private void increment(Map<String, Long> map, String key) {
        map.merge(key, 1L, Long::sum);
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeSmall(String value, String fallback) {
        String out = trim(value);
        if (out.isBlank()) return fallback;
        out = out.replaceAll("\\s+", " ");
        return out.length() > 120 ? out.substring(0, 120) : out;
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String normalizeLarge(String value) {
        String out = trim(value);
        if (out.isBlank()) return null;
        return out.length() > 4000 ? out.substring(0, 4000) : out;
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String fallback(String value, String fallback) {
        String out = trim(value);
        return out.isBlank() ? fallback : out;
    }

    /**
     * Ejecuta una operacion auxiliar del servicio.
     */
    private String formatDay(Instant instant) {
        if (instant == null) return "Sin fecha";
        return instant.atZone(REPORT_ZONE).toLocalDate().toString();
    }

    /**
     * Normaliza el valor recibido para usarlo de forma consistente.
     */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
