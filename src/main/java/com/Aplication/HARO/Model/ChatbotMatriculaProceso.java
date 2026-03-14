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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "chatbot_matricula_proceso",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chatbot_matricula_documento", columnNames = {"numero_documento"})
        }
)
public class ChatbotMatriculaProceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "nombre_completo", nullable = false, length = 160)
    private String nombreCompleto;

    @Column(name = "numero_documento", nullable = false, length = 40)
    private String numeroDocumento;

    @Column(name = "categoria", nullable = false, length = 40)
    private String categoria;

    @Column(name = "email", nullable = false, length = 190)
    private String email;

    @Column(name = "telefono", nullable = false, length = 40)
    private String telefono;

    @Column(name = "payment_link", columnDefinition = "TEXT")
    private String paymentLink;

    @Column(name = "contract_link", columnDefinition = "TEXT")
    private String contractLink;

    @Column(name = "contract_form_data", columnDefinition = "TEXT")
    private String contractFormData;

    @Column(name = "signed_contract_files", columnDefinition = "TEXT")
    private String signedContractFiles;

    @Column(name = "payment_status", nullable = false, length = 40)
    private String paymentStatus = "PENDING";

    @Column(name = "expected_amount", precision = 14, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "payment_amount", precision = 14, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "contract_status", nullable = false, length = 40)
    private String contractStatus = "PENDING_SIGNATURE";

    @Column(name = "flow_status", nullable = false, length = 40)
    private String flowStatus = "DRAFT";

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "contract_signed_at")
    private Instant contractSignedAt;

    @Column(name = "enrolled_at")
    private Instant enrolledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (paymentStatus == null || paymentStatus.isBlank()) paymentStatus = "PENDING";
        if (contractStatus == null || contractStatus.isBlank()) contractStatus = "PENDING_SIGNATURE";
        if (flowStatus == null || flowStatus.isBlank()) flowStatus = "DRAFT";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getPaymentLink() {
        return paymentLink;
    }

    public void setPaymentLink(String paymentLink) {
        this.paymentLink = paymentLink;
    }

    public String getContractLink() {
        return contractLink;
    }

    public void setContractLink(String contractLink) {
        this.contractLink = contractLink;
    }

    public String getContractFormData() {
        return contractFormData;
    }

    public void setContractFormData(String contractFormData) {
        this.contractFormData = contractFormData;
    }

    public String getSignedContractFiles() {
        return signedContractFiles;
    }

    public void setSignedContractFiles(String signedContractFiles) {
        this.signedContractFiles = signedContractFiles;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getContractStatus() {
        return contractStatus;
    }

    public void setContractStatus(String contractStatus) {
        this.contractStatus = contractStatus;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public void setExpectedAmount(BigDecimal expectedAmount) {
        this.expectedAmount = expectedAmount;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount(BigDecimal paymentAmount) {
        this.paymentAmount = paymentAmount;
    }

    public String getFlowStatus() {
        return flowStatus;
    }

    public void setFlowStatus(String flowStatus) {
        this.flowStatus = flowStatus;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Instant getContractSignedAt() {
        return contractSignedAt;
    }

    public void setContractSignedAt(Instant contractSignedAt) {
        this.contractSignedAt = contractSignedAt;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(Instant enrolledAt) {
        this.enrolledAt = enrolledAt;
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
