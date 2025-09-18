package com.Aplication.HARO.Repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Aplication.HARO.Model.EstadoCuenta;

import java.util.Optional;
import java.util.List;

@Repository
public interface EstadoCuentaRepository extends JpaRepository<EstadoCuenta, Long> {
    Optional<EstadoCuenta> findByEstudianteId(Long idEstudiante);
    List<EstadoCuenta> findByEstado(EstadoCuenta estado);
}
