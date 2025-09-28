package com.Aplication.HARO.Model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table(name = "estado_cuenta")
public class EstadoCuenta {

	// import com.fasterxml.jackson.annotation.JsonProperty;
	// import io.swagger.v3.oas.annotations.media.Schema;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_estado") // o el que corresponda
	@JsonProperty(access = JsonProperty.Access.READ_ONLY) // <- no se acepta en requests
	@Schema(accessMode = Schema.AccessMode.READ_ONLY) // <- Swagger lo muestra solo en responses
	private Long id;

	@Column(name = "id_estudiante")
	private long idEstudiante;

	@Column(name = "monto_total", precision = 14, scale = 2)
	private BigDecimal montoTotal;

	@Column(name = "monto_pagado", precision = 14, scale = 2)
	private BigDecimal montoPagado;

	@Column(name = "estado")
	private String estado; // ENUM(Pendiente, Parcial, PazYSalvo)

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public long getIdEstudiante() {
		return idEstudiante;
	}

	public void setIdEstudiante(long idEstudiante) {
		this.idEstudiante = idEstudiante;
	}

	public BigDecimal getMontoTotal() {
		return montoTotal;
	}

	public void setMontoTotal(BigDecimal montoTotal) {
		this.montoTotal = montoTotal;
	}

	public BigDecimal getMontoPagado() {
		return montoPagado;
	}

	public void setMontoPagado(BigDecimal montoPagado) {
		this.montoPagado = montoPagado;
	}

	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

}
