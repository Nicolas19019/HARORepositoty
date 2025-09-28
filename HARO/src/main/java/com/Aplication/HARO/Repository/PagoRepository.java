package com.Aplication.HARO.Repository;


import com.Aplication.HARO.Model.Pagos;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PagoRepository extends JpaRepository<Pagos, Long> {
    List<Pagos> findByEstadoCuenta(Long estadoId);
}



