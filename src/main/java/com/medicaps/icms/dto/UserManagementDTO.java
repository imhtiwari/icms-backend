package com.medicaps.icms.dto;

import com.medicaps.icms.entity.User;
import java.time.LocalDateTime;

public class UserManagementDTO {
    private Long id;
    private String name;
    private String email;
    private String role;
    private Boolean enabled;
    private Boolean emailVerified;
    private Integer strikeCount;
    private LocalDateTime lastStrikeDate;
    private Boolean isBanned;
    private String banReason;
    private LocalDateTime complaintBannedUntil;
    private LocalDateTime createdAt;

    // Constructors
    public UserManagementDTO() {}

    public UserManagementDTO(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.role = user.getRole() != null ? user.getRole().name() : "UNKNOWN";
        this.enabled = user.isEnabled();
        this.emailVerified = user.isEmailVerified();
        this.strikeCount = user.getStrikeCount() != null ? user.getStrikeCount() : 0;
        this.lastStrikeDate = user.getLastStrikeDate();
        this.isBanned = user.getIsBanned();
        this.banReason = user.getBanReason();
        this.complaintBannedUntil = user.getComplaintBannedUntil();
        this.createdAt = user.getCreatedAt();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Boolean getEmailVerified() { return emailVerified; }
    public void setEmailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; }

    public Integer getStrikeCount() { return strikeCount; }
    public void setStrikeCount(Integer strikeCount) { this.strikeCount = strikeCount; }

    public LocalDateTime getLastStrikeDate() { return lastStrikeDate; }
    public void setLastStrikeDate(LocalDateTime lastStrikeDate) { this.lastStrikeDate = lastStrikeDate; }

    public Boolean getIsBanned() { return isBanned; }
    public void setIsBanned(Boolean isBanned) { this.isBanned = isBanned; }

    public String getBanReason() { return banReason; }
    public void setBanReason(String banReason) { this.banReason = banReason; }

    public LocalDateTime getComplaintBannedUntil() { return complaintBannedUntil; }
    public void setComplaintBannedUntil(LocalDateTime complaintBannedUntil) { this.complaintBannedUntil = complaintBannedUntil; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
