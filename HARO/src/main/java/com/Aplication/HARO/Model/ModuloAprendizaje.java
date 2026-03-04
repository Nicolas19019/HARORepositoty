package com.Aplication.HARO.Model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "modulo_aprendizaje",
        indexes = {
                @Index(name = "idx_modulo_aprendizaje_estudiante", columnList = "id_estudiante"),
                @Index(name = "idx_modulo_aprendizaje_curso", columnList = "curso"),
                @Index(name = "idx_modulo_aprendizaje_modulo", columnList = "modulo")
        }
)
public class ModuloAprendizaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_modulo_aprendizaje")
    private Long id;

    @Column(name = "id_estudiante", nullable = false)
    private Long idEstudiante;

    @Column(name = "curso", nullable = false, length = 120)
    private String curso;

    @Column(name = "modulo", nullable = false, length = 120)
    private String modulo;

    @Column(name = "puntaje_teorico")
    private Integer puntajeTeorico;

    @Column(name = "puntaje_practico")
    private Integer puntajePractico;

    @Column(name = "puntaje_simulador")
    private Integer puntajeSimulador;

    @Column(name = "puntaje_final")
    private Integer puntajeFinal;

    @Column(name = "porcentaje_avance")
    private Integer porcentajeAvance;

    @Column(name = "tiempo_total_minutos")
    private Integer tiempoTotalMinutos;

    @Column(name = "tiempo_completado_modulo_minutos")
    private Integer tiempoCompletadoModuloMinutos;

    @Column(name = "porcentaje_completado_modulo")
    private Integer porcentajeCompletadoModulo;

    @Column(name = "tiempo_total_curso_minutos")
    private Integer tiempoTotalCursoMinutos;

    @Column(name = "tiempo_completado_curso_minutos")
    private Integer tiempoCompletadoCursoMinutos;

    @Column(name = "porcentaje_completado_curso")
    private Integer porcentajeCompletadoCurso;

    @Column(name = "intentos", nullable = false)
    private Integer intentos = 0;

    @Column(name = "aprobado", nullable = false)
    private Boolean aprobado = false;

    @Column(name = "favorito", nullable = false)
    private Boolean favorito = false;

    @Column(name = "observaciones", length = 1000)
    private String observaciones;

    @Column(name = "fecha_evaluacion")
    private LocalDate fechaEvaluacion;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (creadoEn == null) creadoEn = now;
        if (actualizadoEn == null) actualizadoEn = now;
        if (intentos == null) intentos = 0;
        if (aprobado == null) aprobado = false;
        if (favorito == null) favorito = false;
    }

    @PreUpdate
    public void preUpdate() {
        actualizadoEn = Instant.now();
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

    public String getCurso() {
        return curso;
    }

    public void setCurso(String curso) {
        this.curso = curso;
    }

    public String getModulo() {
        return modulo;
    }

    public void setModulo(String modulo) {
        this.modulo = modulo;
    }

    public Integer getPuntajeTeorico() {
        return puntajeTeorico;
    }

    public void setPuntajeTeorico(Integer puntajeTeorico) {
        this.puntajeTeorico = puntajeTeorico;
    }

    public Integer getPuntajePractico() {
        return puntajePractico;
    }

    public void setPuntajePractico(Integer puntajePractico) {
        this.puntajePractico = puntajePractico;
    }

    public Integer getPuntajeSimulador() {
        return puntajeSimulador;
    }

    public void setPuntajeSimulador(Integer puntajeSimulador) {
        this.puntajeSimulador = puntajeSimulador;
    }

    public Integer getPuntajeFinal() {
        return puntajeFinal;
    }

    public void setPuntajeFinal(Integer puntajeFinal) {
        this.puntajeFinal = puntajeFinal;
    }

    public Integer getPorcentajeAvance() {
        return porcentajeAvance;
    }

    public void setPorcentajeAvance(Integer porcentajeAvance) {
        this.porcentajeAvance = porcentajeAvance;
    }

    public Integer getIntentos() {
        return intentos;
    }

    public Integer getTiempoTotalMinutos() {
        return tiempoTotalMinutos;
    }

    public void setTiempoTotalMinutos(Integer tiempoTotalMinutos) {
        this.tiempoTotalMinutos = tiempoTotalMinutos;
    }

    public Integer getTiempoCompletadoModuloMinutos() {
        return tiempoCompletadoModuloMinutos;
    }

    public void setTiempoCompletadoModuloMinutos(Integer tiempoCompletadoModuloMinutos) {
        this.tiempoCompletadoModuloMinutos = tiempoCompletadoModuloMinutos;
    }

    public Integer getPorcentajeCompletadoModulo() {
        return porcentajeCompletadoModulo;
    }

    public void setPorcentajeCompletadoModulo(Integer porcentajeCompletadoModulo) {
        this.porcentajeCompletadoModulo = porcentajeCompletadoModulo;
    }

    public Integer getTiempoTotalCursoMinutos() {
        return tiempoTotalCursoMinutos;
    }

    public void setTiempoTotalCursoMinutos(Integer tiempoTotalCursoMinutos) {
        this.tiempoTotalCursoMinutos = tiempoTotalCursoMinutos;
    }

    public Integer getTiempoCompletadoCursoMinutos() {
        return tiempoCompletadoCursoMinutos;
    }

    public void setTiempoCompletadoCursoMinutos(Integer tiempoCompletadoCursoMinutos) {
        this.tiempoCompletadoCursoMinutos = tiempoCompletadoCursoMinutos;
    }

    public Integer getPorcentajeCompletadoCurso() {
        return porcentajeCompletadoCurso;
    }

    public void setPorcentajeCompletadoCurso(Integer porcentajeCompletadoCurso) {
        this.porcentajeCompletadoCurso = porcentajeCompletadoCurso;
    }

    public void setIntentos(Integer intentos) {
        this.intentos = intentos;
    }

    public Boolean getAprobado() {
        return aprobado;
    }

    public void setAprobado(Boolean aprobado) {
        this.aprobado = aprobado;
    }

    public Boolean getFavorito() {
        return favorito;
    }

    public void setFavorito(Boolean favorito) {
        this.favorito = favorito;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public LocalDate getFechaEvaluacion() {
        return fechaEvaluacion;
    }

    public void setFechaEvaluacion(LocalDate fechaEvaluacion) {
        this.fechaEvaluacion = fechaEvaluacion;
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
