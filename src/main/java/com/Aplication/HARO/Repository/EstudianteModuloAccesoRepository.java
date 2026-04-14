package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.EstudianteModuloAcceso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso al registro de uso del modulo de aprendizaje por estudiante.
 *
 * Consulta altas individuales y metricas basicas de ingreso al modulo.
 */
public interface EstudianteModuloAccesoRepository extends JpaRepository<EstudianteModuloAcceso, Long> {
    Optional<EstudianteModuloAcceso> findByIdEstudiante(Long idEstudiante);
    long countByPrimerIngresoEnIsNotNull();
}
