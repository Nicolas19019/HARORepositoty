package com.Aplication.HARO.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "contenido_clase")
public class ContenidoClase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_contenido_clase")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_clase_aprendizaje", nullable = false)
    @JsonIgnore
    private ClaseAprendizaje clase;

    @Column(name = "id_clase_aprendizaje", insertable = false, updatable = false)
    private Long claseIdRef;

    @Column(name = "titulo", nullable = false, length = 180)
    private String titulo;

    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    @Column(name = "descripcion", length = 2000)
    private String descripcion;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "orden_contenido", nullable = false)
    private Integer orden = 1;

    @Column(name = "visible", nullable = false)
    private Boolean visible = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (creadoEn == null) creadoEn = now;
        if (actualizadoEn == null) actualizadoEn = now;
        if (visible == null) visible = true;
        if (orden == null || orden <= 0) orden = 1;
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

    public ClaseAprendizaje getClase() {
        return clase;
    }

    public void setClase(ClaseAprendizaje clase) {
        this.clase = clase;
    }

    @JsonProperty("claseId")
    public Long getClaseId() {
        return claseIdRef;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getOrden() {
        return orden;
    }

    public void setOrden(Integer orden) {
        this.orden = orden;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
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

    @JsonProperty("title")
    public String getTitle() {
        return titulo;
    }

    @JsonProperty("type")
    public String getType() {
        return tipo;
    }

    @JsonProperty("description")
    public String getDescription() {
        return descripcion;
    }
}
