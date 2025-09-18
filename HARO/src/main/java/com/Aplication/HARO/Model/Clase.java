package com.Aplication.HARO.Model;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "clase")
public class Clase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_clase")
	public Long id;

    
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
    @Column(name = "fecha")
    private LocalDate fecha;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fin")
    private LocalTime horaFin;

    @Enumerated(EnumType.STRING)
    private EstadoCuenta estado; // ENUM

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Estudiante getEstudiante() {
		return estudiante;
	}

	public void setEstudiante(Estudiante estudiante) {
		this.estudiante = estudiante;
	}

	public Profesor getProfesor() {
		return profesor;
	}

	public void setProfesor(Profesor profesor) {
		this.profesor = profesor;
	}

	public Vehiculo getVehiculo() {
		return vehiculo;
	}

	public void setVehiculo(Vehiculo vehiculo) {
		this.vehiculo = vehiculo;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public void setFecha(LocalDate fecha) {
		this.fecha = fecha;
	}

	public LocalTime getHoraInicio() {
		return horaInicio;
	}

	public void setHoraInicio(LocalTime horaInicio) {
		this.horaInicio = horaInicio;
	}

	public LocalTime getHoraFin() {
		return horaFin;
	}

	public void setHoraFin(LocalTime horaFin) {
		this.horaFin = horaFin;
	}

	public EstadoCuenta getEstado() {
		return estado;
	}

	public void setEstado(EstadoCuenta estado) {
		this.estado = estado;
	}

 
}
