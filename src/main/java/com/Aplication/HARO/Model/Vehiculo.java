package com.Aplication.HARO.Model;

import jakarta.persistence.*;


/**
 * Vehiculo disponible para clases practicas.
 *
 * Usa la placa como identificador y registra marca, modelo, anio, sede, estado
 * y visibilidad para la gestion de agenda y disponibilidad.
 */
@Entity
@Table(name = "vehiculo")
public class Vehiculo {

    @Id
    @Column(name = "placa", length = 20)
    private String placa;

    @Column(name = "marca")
    private String marca;
    @Column(name = "modelo")
    private String modelo;

    @Column(name = "anio")
    private Integer anio;
    @Column(name = "sede", length = 120)
    private String sede;
    @Column(name = "estado")
    private String estado;
    @Column(name = "visible", columnDefinition = "boolean default true")
    private Boolean visible = true;

  
	public String getPlaca() {
		return placa;
	}

	public void setPlaca(String placa) {
		this.placa = placa;
	}

	public String getMarca() {
		return marca;
	}

	public void setMarca(String marca) {
		this.marca = marca;
	}

	public String getModelo() {
		return modelo;
	}

	public void setModelo(String modelo) {
		this.modelo = modelo;
	}

	public Integer getAnio() {
		return anio;
	}

	public void setAnio(Integer anio) {
		this.anio = anio;
	}

	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	public String getSede() {
		return sede;
	}

	public void setSede(String sede) {
		this.sede = sede;
	}

	public Boolean getVisible() {
		return visible;
	}

	public void setVisible(Boolean visible) {
		this.visible = visible;
	}

    /**
     * Asegura que la visibilidad del vehiculo nunca quede nula.
     */
    @PrePersist
    @PreUpdate
    public void normalizarVisibilidad() {
        if (visible == null) visible = true;
    }


   
}
