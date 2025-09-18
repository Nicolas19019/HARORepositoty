package com.Aplication.HARO.Model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "estado_cuenta")
public class EstadoCuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado")
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "id_estudiante", nullable = false,
                foreignKey = @ForeignKey(name = "fk_estado_estudiante"))
    private Estudiante estudiante;

    @Column(name = "monto_total", precision = 14, scale = 2)
    private BigDecimal montoTotal;

    @Column(name = "monto_pagado", precision = 14, scale = 2)
    private BigDecimal montoPagado;

    @Enumerated(EnumType.STRING)
    private EstadoCuenta estado; // ENUM(Pendiente, Parcial, PazYSalvo)

    @OneToMany(mappedBy = "estadoCuenta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Pagos> pagos = new ArrayList<>();

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

	public EstadoCuenta getEstado() {
		return estado;
	}

	public void setEstado(EstadoCuenta estado) {
		this.estado = estado;
	}

	public List<Pagos> getPagos() {
		return pagos;
	}

	public void setPagos(List<Pagos> pagos) {
		this.pagos = pagos;
	}

    
    
    
}
