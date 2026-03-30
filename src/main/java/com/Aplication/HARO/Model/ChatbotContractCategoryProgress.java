package com.Aplication.HARO.Model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "chatbot_contract_category_progress",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chatbot_contract_category_progress_process_category",
                        columnNames = {"proceso_id", "category_code"}
                )
        }
)
public class ChatbotContractCategoryProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    private ChatbotMatriculaProceso proceso;

    @Column(name = "category_code", nullable = false, length = 20)
    private String categoryCode;

    @Column(name = "category_label", nullable = false, length = 40)
    private String categoryLabel;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 0;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "current_contract_index", nullable = false)
    private Integer currentContractIndex = 0;

    @Column(name = "contract_form_data", columnDefinition = "TEXT")
    private String contractFormData;

    @Column(name = "signed_contract_files", columnDefinition = "TEXT")
    private String signedContractFiles;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (status == null || status.isBlank()) status = "PENDING";
        if (currentContractIndex == null || currentContractIndex < 0) currentContractIndex = 0;
        if (orderIndex == null || orderIndex < 0) orderIndex = 0;
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

    public ChatbotMatriculaProceso getProceso() {
        return proceso;
    }

    public void setProceso(ChatbotMatriculaProceso proceso) {
        this.proceso = proceso;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getCategoryLabel() {
        return categoryLabel;
    }

    public void setCategoryLabel(String categoryLabel) {
        this.categoryLabel = categoryLabel;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCurrentContractIndex() {
        return currentContractIndex;
    }

    public void setCurrentContractIndex(Integer currentContractIndex) {
        this.currentContractIndex = currentContractIndex;
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

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
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
