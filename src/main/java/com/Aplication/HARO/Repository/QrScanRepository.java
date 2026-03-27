package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.QrScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface QrScanRepository extends JpaRepository<QrScan, Long> {

    long countByCreatedAtBetween(Instant from, Instant to);

    List<QrScan> findByCreatedAtBetweenOrderByCreatedAtAsc(Instant from, Instant to);
}
