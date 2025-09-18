package com.Aplication.HARO.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Aplication.HARO.Model.Clase;

import java.awt.print.Pageable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ClaseRepository extends JpaRepository<Clase, Long> {

    // consultas comunes para agenda
    List<Clase> findByFecha(LocalDate fecha);
    Page<Clase> findByFecha(LocalDate fecha, Page pageable);

    List<Clase> findByEstudianteId(Long idEstudiante);
    Page<Clase> findByProfesorId(Long idProfesor, Pageable pageable);

    List<Clase> findByVehiculoPlaca(String placa);

    List<Clase> findByFechaAndHoraInicioBetween(LocalDate fecha,
                                                LocalTime horaInicio,
                                                LocalTime horaFin);

}
