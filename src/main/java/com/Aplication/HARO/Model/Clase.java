package com.Aplication.HARO.Model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.*;

/**
 * Clase practica agendada en calendario interno.
 *
 * Relaciona estudiante, profesor y vehiculo mediante sus identificadores, y
 * conserva fecha, rango horario y estado para controlar disponibilidad.
 */
@Entity
@Table(name = "clase")
public class Clase {


	  @Id
	  @GeneratedValue(strategy = GenerationType.IDENTITY)
	  @Column(name = "id_clase")
	  @JsonProperty(access = JsonProperty.Access.READ_ONLY)                 // <- no editable en POST
	  @Schema(accessMode = Schema.AccessMode.READ_ONLY, example = "1")      // <- Swagger lo oculta en request
	  private Long id;

	  @Column(name = "id_estudiante", nullable = false)
	  @Schema(example = "1")
	  private Long id_estudiante;

	  @Column(name = "id_profesor", nullable = false)
	  @Schema(example = "2")
	  private Long id_profesor;

	  @Column(name = "placa_vehiculo", nullable = false, length = 20)
	  @Schema(example = "ABC123")
	  private String placa_vehiculo;

	  @Column(name = "fecha", nullable = false)
	  @JsonFormat(pattern = "yyyy-MM-dd")                                   // <- string en JSON
	  @Schema(type = "string", format = "date", example = "2025-10-01")
	  private LocalDate fecha;

	  @Column(name = "hora_inicio", nullable = false)
	  @JsonFormat(pattern = "HH:mm")                                        // <- string en JSON
	  @Schema(type = "string", example = "08:00")
	  private LocalTime horaInicio;

	  @Column(name = "hora_fin", nullable = false)
	  @JsonFormat(pattern = "HH:mm")                                        // <- string en JSON
	  @Schema(type = "string", example = "10:00")
	  private LocalTime horaFin;

	  @Column(name = "estado", length = 30, nullable = false)
	  @Schema(example = "Programada")
	  private String estado;

	  @Column(name = "estado_clase", length = 50)
	  @Schema(example = "Pendiente")
	  private String estadoClase;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getId_estudiante() {
		return id_estudiante;
	}

	public void setId_estudiante(Long id_estudiante) {
		this.id_estudiante = id_estudiante;
	}

	public Long getId_profesor() {
		return id_profesor;
	}

	public void setId_profesor(Long id_profesor) {
		this.id_profesor = id_profesor;
	}

	public String getPlaca_vehiculo() {
		return placa_vehiculo;
	}

	public void setPlaca_vehiculo(String placa_vehiculo) {
		this.placa_vehiculo = placa_vehiculo;
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

	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	public String getEstadoClase() {
		return estadoClase;
	}

	public void setEstadoClase(String estadoClase) {
		this.estadoClase = estadoClase;
	}

}
