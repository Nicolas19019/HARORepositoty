package com.Aplication.HARO.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Aplication.HARO.Model.Clase;

import java.time.LocalTime;
import java.time.LocalDate;
import java.util.List;


@Repository
public interface ClaseRepository extends JpaRepository<Clase, Long> {

	 // Por fecha exacta
    List<Clase> findByFecha(LocalDate fecha);

    // Por rango (opcional)
    List<Clase> findByFechaBetween(LocalDate desde, LocalDate hasta);

    @Query("""
            SELECT c
            FROM Clase c
            WHERE c.id_estudiante = :idEstudiante
            ORDER BY c.fecha ASC, c.horaInicio ASC
            """)
    List<Clase> findAgendaByIdEstudiante(@Param("idEstudiante") Long idEstudiante);

    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM Clase c
            WHERE c.id_estudiante = :idEstudiante
              AND c.fecha = :fecha
              AND c.horaInicio < :horaFin
              AND c.horaFin > :horaInicio
            """)
    boolean existsStudentOverlap(@Param("idEstudiante") Long idEstudiante,
                                 @Param("fecha") LocalDate fecha,
                                 @Param("horaInicio") LocalTime horaInicio,
                                 @Param("horaFin") LocalTime horaFin);

    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM Clase c
            WHERE c.id_profesor = :idProfesor
              AND c.fecha = :fecha
              AND c.horaInicio < :horaFin
              AND c.horaFin > :horaInicio
            """)
    boolean existsProfesorOverlap(@Param("idProfesor") Long idProfesor,
                                  @Param("fecha") LocalDate fecha,
                                  @Param("horaInicio") LocalTime horaInicio,
                                  @Param("horaFin") LocalTime horaFin);

    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM Clase c
            WHERE c.placa_vehiculo = :placaVehiculo
              AND c.fecha = :fecha
              AND c.horaInicio < :horaFin
              AND c.horaFin > :horaInicio
            """)
    boolean existsVehiculoOverlap(@Param("placaVehiculo") String placaVehiculo,
                                  @Param("fecha") LocalDate fecha,
                                  @Param("horaInicio") LocalTime horaInicio,
                                  @Param("horaFin") LocalTime horaFin);
}
