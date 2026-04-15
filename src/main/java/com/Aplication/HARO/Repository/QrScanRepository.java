package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.QrScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * Acceso a metricas de escaneos QR.
 *
 * Consulta conteos y eventos por rango de fechas para reportes de campanas y
 * canales.
 */
public interface QrScanRepository extends JpaRepository<QrScan, Long> {

    long countByCreatedAtBetween(Instant from, Instant to);

    List<QrScan> findByCreatedAtBetweenOrderByCreatedAtAsc(Instant from, Instant to);
}
