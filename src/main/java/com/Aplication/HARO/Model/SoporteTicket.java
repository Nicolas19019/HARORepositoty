package com.Aplication.HARO.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Ticket de soporte academico o administrativo.
 *
 * Conserva solicitud, estado, responsable, adjuntos y datos del estudiante para
 * seguimiento interno.
 */
@Entity
@Table(name = "soporte_ticket", indexes = {
        @Index(name = "idx_soporte_ticket_created_at", columnList = "created_at"),
        @Index(name = "idx_soporte_ticket_estado", columnList = "estado"),
        @Index(name = "idx_soporte_ticket_tipo", columnList = "tipo"),
        @Index(name = "idx_soporte_ticket_email", columnList = "email_estudiante")
})
public class SoporteTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_soporte_ticket")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @Column(name = "tipo", length = 60, nullable = false)
    private String tipo;

    @Column(name = "mensaje", columnDefinition = "TEXT", nullable = false)
    private String mensaje;

    @Column(name = "estado", length = 30, nullable = false)
    private String estado;

    @Column(name = "nota_interna", columnDefinition = "TEXT")
    private String notaInterna;

    @Column(name = "email_estudiante", length = 180, nullable = false)
    private String emailEstudiante;

    @Column(name = "nombre_estudiante", length = 180, nullable = false)
    private String nombreEstudiante;

    @Column(name = "adjunto_nombre", length = 255)
    private String adjuntoNombre;

    @Column(name = "adjunto_url", columnDefinition = "TEXT")
    private String adjuntoUrl;

    @Column(name = "responsable", length = 180)
    private String responsable;

    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant updatedAt;

    /**
     * Inicializa las marcas de tiempo al crear el ticket de soporte.
     */
    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    /**
     * Actualiza la fecha de modificacion del ticket de soporte.
     */
    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getNotaInterna() {
        return notaInterna;
    }

    public void setNotaInterna(String notaInterna) {
        this.notaInterna = notaInterna;
    }

    public String getEmailEstudiante() {
        return emailEstudiante;
    }

    public void setEmailEstudiante(String emailEstudiante) {
        this.emailEstudiante = emailEstudiante;
    }

    public String getNombreEstudiante() {
        return nombreEstudiante;
    }

    public void setNombreEstudiante(String nombreEstudiante) {
        this.nombreEstudiante = nombreEstudiante;
    }

    public String getAdjuntoNombre() {
        return adjuntoNombre;
    }

    public void setAdjuntoNombre(String adjuntoNombre) {
        this.adjuntoNombre = adjuntoNombre;
    }

    public String getAdjuntoUrl() {
        return adjuntoUrl;
    }

    public void setAdjuntoUrl(String adjuntoUrl) {
        this.adjuntoUrl = adjuntoUrl;
    }

    public String getResponsable() {
        return responsable;
    }

    public void setResponsable(String responsable) {
        this.responsable = responsable;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
