package com.carritos.academiacarros.repository;

import com.carritos.academiacarros.model.EstadoCuenta;
import com.carritos.academiacarros.model.EstadoDeuda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface EstadoCuentaRepository extends JpaRepository<EstadoCuenta, Long> {
    Optional<EstadoCuenta> findByEstudianteId(Long idEstudiante);
    List<EstadoCuenta> findByEstado(EstadoDeuda estado);
}
