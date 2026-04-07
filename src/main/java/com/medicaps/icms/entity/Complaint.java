package com.medicaps.icms.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "complaints")
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Title is required")
    @Column(nullable = false)
    private String title;

    @NotBlank(message = "Description is required")
    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "block_number")
    private String blockNumber;

    @Column(name = "building_number")
    private String buildingNumber;

    @Column(name = "room_number")
    private String roomNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_category")
    private AssetCategory assetCategory = AssetCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintStatus status = ComplaintStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority = Priority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "user"})
    private User user;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @OneToMany(mappedBy = "complaint", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "user", "evidences", "assignedWorker"})
    private Set<Evidence> evidences = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "complaints"})
    private Worker assignedWorker;

    @Column(name = "is_false_complaint")
    private Boolean isFalseComplaint = false;

    @Column(name = "false_complaint_reason")
    private String falseComplaintReason;

    // AI Validation Fields
    @Column(name = "ai_validation_score")
    private Double aiValidationScore;

    @Column(name = "ai_validation_reason", columnDefinition = "TEXT")
    private String aiValidationReason;

    @Column(name = "ai_validated_at")
    private LocalDateTime aiValidatedAt;

    @Column(name = "ai_validation_passed")
    private Boolean aiValidationPassed;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    public Complaint() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBlockNumber() { return blockNumber; }
    public void setBlockNumber(String blockNumber) { this.blockNumber = blockNumber; }

    public String getBuildingNumber() { return buildingNumber; }
    public void setBuildingNumber(String buildingNumber) { this.buildingNumber = buildingNumber; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

    public AssetCategory getAssetCategory() { 
        return assetCategory != null ? assetCategory : AssetCategory.OTHER; 
    }
    public void setAssetCategory(AssetCategory assetCategory) { this.assetCategory = assetCategory; }

    public ComplaintStatus getStatus() { return status; }
    public void setStatus(ComplaintStatus status) { this.status = status; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public Set<Evidence> getEvidences() { return evidences; }
    public void setEvidences(Set<Evidence> evidences) { this.evidences = evidences; }

    public Worker getAssignedWorker() { return assignedWorker; }
    public void setAssignedWorker(Worker assignedWorker) { this.assignedWorker = assignedWorker; }

    public Boolean getIsFalseComplaint() { return isFalseComplaint; }
    public void setIsFalseComplaint(Boolean isFalseComplaint) { this.isFalseComplaint = isFalseComplaint; }

    public String getFalseComplaintReason() { return falseComplaintReason; }
    public void setFalseComplaintReason(String falseComplaintReason) { this.falseComplaintReason = falseComplaintReason; }

    // AI Validation Getters and Setters
    public Double getAiValidationScore() { return aiValidationScore; }
    public void setAiValidationScore(Double aiValidationScore) { this.aiValidationScore = aiValidationScore; }

    public String getAiValidationReason() { return aiValidationReason; }
    public void setAiValidationReason(String aiValidationReason) { this.aiValidationReason = aiValidationReason; }

    public LocalDateTime getAiValidatedAt() { return aiValidatedAt; }
    public void setAiValidatedAt(LocalDateTime aiValidatedAt) { this.aiValidatedAt = aiValidatedAt; }

    public Boolean getAiValidationPassed() { return aiValidationPassed; }
    public void setAiValidationPassed(Boolean aiValidationPassed) { this.aiValidationPassed = aiValidationPassed; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.status == ComplaintStatus.RESOLVED && this.resolvedAt == null) {
            this.resolvedAt = LocalDateTime.now();
        }
    }
}
