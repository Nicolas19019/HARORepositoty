package com.carritos.academiacarros.repository;

import com.carritos.academiacarros.model.Pago;
import com.carritos.academiacarros.model.MetodoPago;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    List<Pago> findByEstadoCuentaId(Long idEstadoCuenta);
    Page<Pago> findByEstadoCuentaId(Long idEstadoCuenta, Pageable pageable);

    List<Pago> findByFechaPagoBetween(LocalDate desde, LocalDate hasta);
    List<Pago> findByMetodo(MetodoPago metodo);
}
