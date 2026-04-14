package com.Aplication.HARO.Controller;

import com.Aplication.HARO.Service.QrScanService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/qr/scan")
@CrossOrigin(origins = {
        "https://ceaharo.com",
        "https://www.ceaharo.com"
})
/**
 * Controlador REST para QR scan.
 */
public class QrScanController {

    /**
     * DTO de entrada con los datos de lectura del codigo QR.
     */
    public record ScanRequest(
            String source,
            String channel,
            String target,
            String page,
            String qrName,
            String campaign,
            String sede,
            String location,
            String queryParams,
            String referer
    ) {}

    private final QrScanService service;

/**
 * Inyecta las dependencias necesarias del controlador.
 */
    public QrScanController(QrScanService service) {
        this.service = service;
    }

    /**
     * Registra un evento de escaneo QR.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> register(@RequestBody(required = false) ScanRequest req,
                                                        HttpServletRequest httpRequest) {
        QrScanService.RegisterResponse out = service.register(
                new QrScanService.RegisterRequest(
                        req == null ? null : req.source(),
                        req == null ? null : req.channel(),
                        req == null ? null : req.target(),
                        req == null ? null : req.page(),
                        req == null ? null : req.qrName(),
                        req == null ? null : req.campaign(),
                        req == null ? null : req.sede(),
                        req == null ? null : req.location(),
                        req == null ? null : req.queryParams(),
                        firstNotBlank(req == null ? null : req.referer(), httpRequest.getHeader("Referer")),
                        resolveClientIp(httpRequest),
                        trim(httpRequest.getHeader("User-Agent"))
                )
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", out.ok());
        body.put("id", out.id());
        body.put("createdAt", out.createdAt());
        return ResponseEntity.ok(body);
    }

/**
 * Cuenta todos los registros disponibles.
 */
    @GetMapping("/count")
    public QrScanService.CountResponse countAll() {
        return service.countAll();
    }

/**
 * Cuenta los registros generados en el dia actual.
 */
    @GetMapping("/count/today")
    public QrScanService.CountResponse countToday() {
        return service.countToday();
    }

    /**
     * Genera el reporte de lecturas QR en un rango de fechas.
     */
    @GetMapping("/report")
    public QrScanService.ReportResponse report(@RequestParam(required = false) LocalDate from,
                                               @RequestParam(required = false) LocalDate to) {
        return service.report(from, to);
    }

/**
 * Resuelve la IP del cliente a partir de la peticion.
 */
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = trim(request.getHeader("X-Forwarded-For"));
        if (!forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            return comma >= 0 ? forwardedFor.substring(0, comma).trim() : forwardedFor;
        }

        String realIp = trim(request.getHeader("X-Real-IP"));
        if (!realIp.isBlank()) {
            return realIp;
        }
        return trim(request.getRemoteAddr());
    }

/**
 * Retorna el primer valor no vacio.
 */
    private String firstNotBlank(String first, String second) {
        String a = trim(first);
        if (!a.isBlank()) return a;
        return trim(second);
    }

/**
 * Recorta texto evitando nulos.
 */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
