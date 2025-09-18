package com.carritos.academiacarros.repository;

import com.carritos.academiacarros.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ClaseRepository extends JpaRepository<Clase, Long> {

    // consultas comunes para agenda
    List<Clase> findByFecha(LocalDate fecha);
    Page<Clase> findByFecha(LocalDate fecha, Pageable pageable);

    List<Clase> findByEstudianteId(Long idEstudiante);
    Page<Clase> findByProfesorId(Long idProfesor, Pageable pageable);

    List<Clase> findByVehiculoPlaca(String placa);

    List<Clase> findByFechaAndHoraInicioBetween(LocalDate fecha,
                                                LocalTime horaInicio,
                                                LocalTime horaFin);

    List<Clase> findByEstado(EstadoClase estado);
}
