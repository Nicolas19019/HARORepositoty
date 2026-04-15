package com.Aplication.HARO.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Aplication.HARO.Model.EstadoCuenta;

import java.util.Optional;

/**
 * Acceso al estado de cuenta financiero de estudiantes.
 *
 * Permite ubicar el estado de cuenta asociado a un estudiante para calcular
 * pagos, multas y saldo pendiente.
 */
@Repository
public interface EstadoCuentaRepository extends JpaRepository<EstadoCuenta, Long> {
    Optional<EstadoCuenta> findByIdEstudiante(Long idEstudiante);
}
