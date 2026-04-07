package com.medicaps.icms.entity;

public enum Role {
    OWNER("Owner"),
    ADMIN("Admin"),
    USER("User"),
    WORKER("Worker");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
