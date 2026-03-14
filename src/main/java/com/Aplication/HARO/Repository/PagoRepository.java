package com.Aplication.HARO.Repository;


import com.Aplication.HARO.Model.Pagos;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pagos, Long> {
    List<Pagos> findByEstadoCuenta(Long estadoId);
    Optional<Pagos> findTopByEstadoCuentaOrderByIdDesc(Long estadoId);
}



