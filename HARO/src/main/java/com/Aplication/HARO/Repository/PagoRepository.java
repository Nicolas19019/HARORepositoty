package com.Aplication.HARO.Repository;


import com.Aplication.HARO.Model.Pagos;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PagoRepository extends JpaRepository<Pagos, Long> {
    List<Pagos> findByEstadoCuentaId(Long idEstadoCuenta);
    Page<Pagos> findByEstadoCuentaId(Long idEstadoCuenta, Pageable pageable);

    List<Pagos> findByFechaPagoBetween(LocalDate desde, LocalDate hasta);
    List<Pagos> findByMetodo(String metodo);
}
