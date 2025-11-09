package com.Aplication.HARO.Model;

import jakarta.persistence.*;
import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Table(name = "estado_cuenta")
@DynamicInsert
public class EstadoCuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_cuenta") // <-- PK correcta según tu tabla
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @Column(name = "id_estudiante", nullable = false)
    private Long idEstudiante;

    @Column(name = "monto_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoTotal = BigDecimal.ZERO;

    @Column(name = "monto_pagado", nullable = false, precision = 14, scale = 2)
    private BigDecimal montoPagado = BigDecimal.ZERO;

    @Column(name = "estado", nullable = false, length = 50)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String estado = "Pendiente"; // valor por defecto en el objeto

 

    public Long getId() {
		return id;
	}



	public void setId(Long id) {
		this.id = id;
	}



	public Long getIdEstudiante() {
		return idEstudiante;
	}



	public void setIdEstudiante(Long idEstudiante) {
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



	// ===== Reglas de negocio previas al INSERT/UPDATE =====
    @PrePersist @PreUpdate
    public void calcularEstadoYValidar() {
        if (montoTotal == null) montoTotal = BigDecimal.ZERO;
        if (montoPagado == null) montoPagado = BigDecimal.ZERO;

        if (montoTotal.signum() < 0 || montoPagado.signum() < 0) {
            throw new IllegalArgumentException("Los montos no pueden ser negativos.");
        }
        if (montoPagado.compareTo(montoTotal) > 0) {
            // Si NO quieres permitir sobrepago (recomendado):
            throw new IllegalArgumentException("El monto_pagado no puede superar el monto_total.");
            // Si SÍ quieres permitir sobrepago, comenta la línea de arriba
            // y descomenta esta:
            // estado = "Saldo a favor";
            // return;
        }

        int cmp = montoPagado.compareTo(montoTotal);
        if (cmp == 0) estado = "Pagado";
        else estado = "En deuda"; // cuando pagado < total
    }
}
