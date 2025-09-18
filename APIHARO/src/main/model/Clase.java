package com.carritos.academiacarros.model;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "clase")
public class Clase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_clase")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_estudiante", nullable = false,
                foreignKey = @ForeignKey(name = "fk_clase_estudiante"))
    private Estudiante estudiante;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_profesor", nullable = false,
                foreignKey = @ForeignKey(name = "fk_clase_profesor"))
    private Profesor profesor;

    // El diagrama muestra PK=placa en Vehiculo, por eso relacionamos por placa (String).
    @ManyToOne(optional = false)
    @JoinColumn(name = "placa_vehiculo", nullable = false,
                foreignKey = @ForeignKey(name = "fk_clase_vehiculo"))
    private Vehiculo vehiculo;

    private LocalDate fecha;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fin")
    private LocalTime horaFin;

    @Enumerated(EnumType.STRING)
    private EstadoClase estado; // ENUM

    // getters/setters
    // ...
}
