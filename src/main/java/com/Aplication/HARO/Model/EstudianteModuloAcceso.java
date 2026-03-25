package com.Aplication.HARO.Model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "estudiante_modulo_acceso",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_estudiante_modulo_acceso_estudiante", columnNames = "id_estudiante")
        },
        indexes = {
                @Index(name = "idx_estudiante_modulo_acceso_estudiante", columnList = "id_estudiante"),
                @Index(name = "idx_estudiante_modulo_acceso_registrado", columnList = "registrado_en"),
                @Index(name = "idx_estudiante_modulo_acceso_ultimo_ingreso", columnList = "ultimo_ingreso_en")
        }
)
public class EstudianteModuloAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estudiante_modulo_acceso")
    private Long id;

    @Column(name = "id_estudiante", nullable = false)
    private Long idEstudiante;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    @Column(name = "primer_ingreso_en")
    private Instant primerIngresoEn;

    @Column(name = "ultimo_ingreso_en")
    private Instant ultimoIngresoEn;

    @Column(name = "total_ingresos", nullable = false)
    private Integer totalIngresos = 0;

    @Column(name = "origen_registro", length = 60)
    private String origenRegistro;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (registradoEn == null) registradoEn = now;
        if (creadoEn == null) creadoEn = now;
        if (actualizadoEn == null) actualizadoEn = now;
        if (totalIngresos == null || totalIngresos < 0) totalIngresos = 0;
    }

    @PreUpdate
    public void preUpdate() {
        actualizadoEn = Instant.now();
        if (totalIngresos == null || totalIngresos < 0) totalIngresos = 0;
    }

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

    public Instant getRegistradoEn() {
        return registradoEn;
    }

    public void setRegistradoEn(Instant registradoEn) {
        this.registradoEn = registradoEn;
    }

    public Instant getPrimerIngresoEn() {
        return primerIngresoEn;
    }

    public void setPrimerIngresoEn(Instant primerIngresoEn) {
        this.primerIngresoEn = primerIngresoEn;
    }

    public Instant getUltimoIngresoEn() {
        return ultimoIngresoEn;
    }

    public void setUltimoIngresoEn(Instant ultimoIngresoEn) {
        this.ultimoIngresoEn = ultimoIngresoEn;
    }

    public Integer getTotalIngresos() {
        return totalIngresos;
    }

    public void setTotalIngresos(Integer totalIngresos) {
        this.totalIngresos = totalIngresos;
    }

    public String getOrigenRegistro() {
        return origenRegistro;
    }

    public void setOrigenRegistro(String origenRegistro) {
        this.origenRegistro = origenRegistro;
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
