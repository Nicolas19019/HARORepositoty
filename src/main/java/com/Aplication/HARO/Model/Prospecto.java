package com.Aplication.HARO.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Prospecto capturado antes de convertirse en estudiante.
 *
 * Mantiene telefono, servicio consultado y contador de interacciones para
 * seguimiento comercial desde chatbot u otros canales.
 */
@Entity
@Table(
        name = "prospecto",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_prospecto_telefono",
                        columnNames = {"telefono"}
                )
        }
)
public class Prospecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telefono", nullable = false, length = 30)
    private String telefono;

    @Column(name = "servicio", nullable = false, length = 80)
    private String servicio;

    @Column(name = "consultas", nullable = false)
    private Integer consultas = 1;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn = Instant.now();

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn = Instant.now();

    /**
     * Inicializa fechas y contador minimo de consultas al crear el prospecto.
     */
    @PrePersist
    public void prePersist() {
        if (consultas == null || consultas < 1) {
            consultas = 1;
        }
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
        if (actualizadoEn == null) {
            actualizadoEn = Instant.now();
        }
    }

    /**
     * Actualiza la fecha de modificacion y normaliza el contador de consultas.
     */
    @PreUpdate
    public void preUpdate() {
        actualizadoEn = Instant.now();
        if (consultas == null || consultas < 1) {
            consultas = 1;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getServicio() {
        return servicio;
    }

    public void setServicio(String servicio) {
        this.servicio = servicio;
    }

    public Integer getConsultas() {
        return consultas;
    }

    public void setConsultas(Integer consultas) {
        this.consultas = consultas;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(Instant actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }
}
