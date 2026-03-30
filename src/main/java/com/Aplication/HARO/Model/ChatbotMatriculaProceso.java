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

    @Column(name = "edad")
    private Integer edad;

    @Column(name = "direccion", length = 220)
    private String direccion;

    @Column(name = "sede", length = 120)
    private String sede;

    @Column(name = "student_password_hash", length = 100)
    private String studentPasswordHash;

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

    @Column(name = "origen_registro", length = 20)
    private String origenRegistro;

    /**
     * EPAYCO / EFECTIVO.
     */
    @Column(name = "metodo_pago", length = 20)
    private String metodoPago;

    @Column(name = "payment_confirmed_at")
    private Instant paymentConfirmedAt;

    @Column(name = "payment_validated_by_admin_id")
    private Long paymentValidatedByAdminId;

    @Column(name = "payment_observation", columnDefinition = "TEXT")
    private String paymentObservation;

    /**
     * FULL / HALF. Se usa para saber si el link de pago corresponde a pago completo o abono (50%).
     * Si viene null/vacio, se asume FULL.
     */
    @Column(name = "payment_plan", length = 20)
    private String paymentPlan;

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

    @Column(name = "contract_email_sent", columnDefinition = "boolean default false")
    private Boolean contractEmailSent = false;

    @Column(name = "contract_email_sent_at")
    private Instant contractEmailSentAt;

    @Column(name = "contract_chatbot_sent", columnDefinition = "boolean default false")
    private Boolean contractChatbotSent = false;

    @Column(name = "contract_chatbot_sent_at")
    private Instant contractChatbotSentAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (paymentStatus == null || paymentStatus.isBlank()) paymentStatus = "PENDING";
        if (contractStatus == null || contractStatus.isBlank()) contractStatus = "PENDING_SIGNATURE";
        if (flowStatus == null || flowStatus.isBlank()) flowStatus = "DRAFT";
        if (contractEmailSent == null) contractEmailSent = false;
        if (contractChatbotSent == null) contractChatbotSent = false;
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

    public Integer getEdad() {
        return edad;
    }

    public void setEdad(Integer edad) {
        this.edad = edad;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getSede() {
        return sede;
    }

    public void setSede(String sede) {
        this.sede = sede;
    }

    public String getStudentPasswordHash() {
        return studentPasswordHash;
    }

    public void setStudentPasswordHash(String studentPasswordHash) {
        this.studentPasswordHash = studentPasswordHash;
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

    public String getOrigenRegistro() {
        return origenRegistro;
    }

    public void setOrigenRegistro(String origenRegistro) {
        this.origenRegistro = origenRegistro;
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }

    public Instant getPaymentConfirmedAt() {
        return paymentConfirmedAt;
    }

    public void setPaymentConfirmedAt(Instant paymentConfirmedAt) {
        this.paymentConfirmedAt = paymentConfirmedAt;
    }

    public Long getPaymentValidatedByAdminId() {
        return paymentValidatedByAdminId;
    }

    public void setPaymentValidatedByAdminId(Long paymentValidatedByAdminId) {
        this.paymentValidatedByAdminId = paymentValidatedByAdminId;
    }

    public String getPaymentObservation() {
        return paymentObservation;
    }

    public void setPaymentObservation(String paymentObservation) {
        this.paymentObservation = paymentObservation;
    }

    public String getPaymentPlan() {
        return paymentPlan;
    }

    public void setPaymentPlan(String paymentPlan) {
        this.paymentPlan = paymentPlan;
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

    public Boolean getContractEmailSent() {
        return contractEmailSent;
    }

    public void setContractEmailSent(Boolean contractEmailSent) {
        this.contractEmailSent = contractEmailSent;
    }

    public Instant getContractEmailSentAt() {
        return contractEmailSentAt;
    }

    public void setContractEmailSentAt(Instant contractEmailSentAt) {
        this.contractEmailSentAt = contractEmailSentAt;
    }

    public Boolean getContractChatbotSent() {
        return contractChatbotSent;
    }

    public void setContractChatbotSent(Boolean contractChatbotSent) {
        this.contractChatbotSent = contractChatbotSent;
    }

    public Instant getContractChatbotSentAt() {
        return contractChatbotSentAt;
    }

    public void setContractChatbotSentAt(Instant contractChatbotSentAt) {
        this.contractChatbotSentAt = contractChatbotSentAt;
    }
}
