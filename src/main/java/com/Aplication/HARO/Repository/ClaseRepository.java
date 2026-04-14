package com.Aplication.HARO.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Aplication.HARO.Model.Clase;

import java.time.LocalTime;
import java.time.LocalDate;
import java.util.List;


/**
 * Acceso a clases practicas agendadas.
 *
 * Incluye consultas de agenda, rangos de fecha y deteccion de cruces para
 * estudiante, profesor y vehiculo.
 */
@Repository
public interface ClaseRepository extends JpaRepository<Clase, Long> {

    /**
     * Consulta clases agendadas en una fecha exacta.
     */
    List<Clase> findByFecha(LocalDate fecha);

    /**
     * Consulta clases agendadas dentro de un rango de fechas.
     */
    List<Clase> findByFechaBetween(LocalDate desde, LocalDate hasta);

    /**
     * Devuelve la agenda completa de un estudiante ordenada cronologicamente.
     */
    @Query("""
            SELECT c
            FROM Clase c
            WHERE c.id_estudiante = :idEstudiante
            ORDER BY c.fecha ASC, c.horaInicio ASC
            """)
    List<Clase> findAgendaByIdEstudiante(@Param("idEstudiante") Long idEstudiante);

    /**
     * Indica si el estudiante ya tiene una clase activa que cruza con el horario.
     */
    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM Clase c
            WHERE c.id_estudiante = :idEstudiante
              AND c.fecha = :fecha
              AND c.horaInicio < :horaFin
              AND c.horaFin > :horaInicio
              AND (c.estado IS NULL OR UPPER(c.estado) NOT LIKE '%CANCEL%')
            """)
    boolean existsStudentOverlap(@Param("idEstudiante") Long idEstudiante,
                                 @Param("fecha") LocalDate fecha,
                                 @Param("horaInicio") LocalTime horaInicio,
                                 @Param("horaFin") LocalTime horaFin);

    /**
     * Indica si el profesor ya tiene una clase activa que cruza con el horario.
     */
    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM Clase c
            WHERE c.id_profesor = :idProfesor
              AND c.fecha = :fecha
              AND c.horaInicio < :horaFin
              AND c.horaFin > :horaInicio
              AND (c.estado IS NULL OR UPPER(c.estado) NOT LIKE '%CANCEL%')
            """)
    boolean existsProfesorOverlap(@Param("idProfesor") Long idProfesor,
                                  @Param("fecha") LocalDate fecha,
                                  @Param("horaInicio") LocalTime horaInicio,
                                  @Param("horaFin") LocalTime horaFin);

    /**
     * Indica si el vehiculo ya esta asignado a una clase activa en ese horario.
     */
    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM Clase c
            WHERE c.placa_vehiculo = :placaVehiculo
              AND c.fecha = :fecha
              AND c.horaInicio < :horaFin
              AND c.horaFin > :horaInicio
              AND (c.estado IS NULL OR UPPER(c.estado) NOT LIKE '%CANCEL%')
            """)
    boolean existsVehiculoOverlap(@Param("placaVehiculo") String placaVehiculo,
                                  @Param("fecha") LocalDate fecha,
                                  @Param("horaInicio") LocalTime horaInicio,
                                  @Param("horaFin") LocalTime horaFin);

    /**
     * Lista profesores ocupados en un horario para excluirlos de la disponibilidad.
     */
    @Query("""
            SELECT DISTINCT c.id_profesor
            FROM Clase c
            WHERE c.id_profesor IS NOT NULL
              AND c.fecha = :fecha
              AND c.horaInicio < :horaFin
              AND c.horaFin > :horaInicio
              AND (c.estado IS NULL OR UPPER(c.estado) NOT LIKE '%CANCEL%')
            """)
    List<Long> findBusyProfesorIds(@Param("fecha") LocalDate fecha,
                                   @Param("horaInicio") LocalTime horaInicio,
                                   @Param("horaFin") LocalTime horaFin);

    /**
     * Lista placas de vehiculos ocupados en un horario para excluirlos de la disponibilidad.
     */
    @Query("""
            SELECT DISTINCT c.placa_vehiculo
            FROM Clase c
            WHERE c.placa_vehiculo IS NOT NULL
              AND c.fecha = :fecha
              AND c.horaInicio < :horaFin
              AND c.horaFin > :horaInicio
              AND (c.estado IS NULL OR UPPER(c.estado) NOT LIKE '%CANCEL%')
            """)
    List<String> findBusyVehiculoPlacas(@Param("fecha") LocalDate fecha,
                                        @Param("horaInicio") LocalTime horaInicio,
                                        @Param("horaFin") LocalTime horaFin);
}
