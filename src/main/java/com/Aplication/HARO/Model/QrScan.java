package com.Aplication.HARO.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Evento de escaneo QR.
 *
 * Guarda origen, canal, campana, sede y metadatos tecnicos para medir trafico
 * y efectividad de enlaces QR.
 */
@Entity
@Table(name = "qr_scan", indexes = {
        @Index(name = "idx_qr_scan_created_at", columnList = "created_at"),
        @Index(name = "idx_qr_scan_source", columnList = "source"),
        @Index(name = "idx_qr_scan_channel", columnList = "channel"),
        @Index(name = "idx_qr_scan_target", columnList = "target"),
        @Index(name = "idx_qr_scan_page", columnList = "page")
})
public class QrScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_qr_scan")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @Column(name = "source", length = 60)
    private String source;

    @Column(name = "channel", length = 60)
    private String channel;

    @Column(name = "target", length = 120)
    private String target;

    @Column(name = "page", length = 120)
    private String page;

    @Column(name = "qr_name", length = 120)
    private String qrName;

    @Column(name = "campaign", length = 120)
    private String campaign;

    @Column(name = "sede", length = 120)
    private String sede;

    @Column(name = "location", length = 120)
    private String location;

    @Column(name = "ip_address", length = 120)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "referer", columnDefinition = "TEXT")
    private String referer;

    @Column(name = "query_params", columnDefinition = "TEXT")
    private String queryParams;

    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant createdAt;

    /**
     * Asigna la fecha de creacion cuando se registra un escaneo QR.
     */
    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public String getQrName() {
        return qrName;
    }

    public void setQrName(String qrName) {
        this.qrName = qrName;
    }

    public String getCampaign() {
        return campaign;
    }

    public void setCampaign(String campaign) {
        this.campaign = campaign;
    }

    public String getSede() {
        return sede;
    }

    public void setSede(String sede) {
        this.sede = sede;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    public String getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(String queryParams) {
        this.queryParams = queryParams;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
