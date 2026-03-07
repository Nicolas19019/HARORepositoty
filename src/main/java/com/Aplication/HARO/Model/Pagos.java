package com.Aplication.HARO.Model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "pago")
public class Pagos {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_pago")
	private Long id;

	@Column(name = "id_estado", nullable = false)
	private Long estadoCuenta; // <- Wrapper, puede ser null hasta validar

	@Column(name = "fecha_pago")
	private LocalDate fechaPago;

	@Column(precision = 14, scale = 2)
	private BigDecimal monto;

	@Column(name = "metodo")
	private String metodo;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getEstadoCuenta() {
		return estadoCuenta;
	}

	public void setEstadoCuenta(Long estadoCuenta) {
		this.estadoCuenta = estadoCuenta;
	}

	public LocalDate getFechaPago() {
		return fechaPago;
	}

	public void setFechaPago(LocalDate fechaPago) {
		this.fechaPago = fechaPago;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public void setMonto(BigDecimal monto) {
		this.monto = monto;
	}

	public String getMetodo() {
		return metodo;
	}

	public void setMetodo(String metodo) {
		this.metodo = metodo;
	}

}
