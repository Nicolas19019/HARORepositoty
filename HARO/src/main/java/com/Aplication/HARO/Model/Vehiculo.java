package com.Aplication.HARO.Model;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "vehiculo")
public class Vehiculo {

    @Id
    @Column(name = "placa", length = 20)
    private String placa; // PK y UNIQUE

    @Column(name = "marca")
    private String marca;
    @Column(name = "modelo")
    private String modelo;

    @Column(name = "anio")
    private Integer anio; // usar Integer en lugar de java.time.Year por compatibilidad JPA
    @Column(name = "estado")
    private String estado;

  
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



   
}
