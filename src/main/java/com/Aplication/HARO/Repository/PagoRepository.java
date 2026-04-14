package com.Aplication.HARO.Repository;


import com.Aplication.HARO.Model.Pagos;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a pagos registrados en estados de cuenta.
 *
 * Consulta el historial de pagos por estado de cuenta y el ultimo pago asociado.
 */
public interface PagoRepository extends JpaRepository<Pagos, Long> {
    List<Pagos> findByEstadoCuenta(Long estadoId);
    Optional<Pagos> findTopByEstadoCuentaOrderByIdDesc(Long estadoId);
}



