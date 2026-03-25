package com.Aplication.HARO.Repository;

import com.Aplication.HARO.Model.EstudianteModuloAcceso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstudianteModuloAccesoRepository extends JpaRepository<EstudianteModuloAcceso, Long> {
    Optional<EstudianteModuloAcceso> findByIdEstudiante(Long idEstudiante);
    long countByPrimerIngresoEnIsNotNull();
}
