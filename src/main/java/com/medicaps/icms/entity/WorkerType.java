package com.medicaps.icms.entity;

public enum WorkerType {
    TECHNICAL("Technical Staff"),
    ELECTRICAL("Electrical"),
    PLUMBING("Plumbing"),
    CARPENTRY("Carpentry"),
    MAINTENANCE("Maintenance"),
    IT("IT Support"),
    LAB("Lab Technician"),
    HOUSEKEEPING("Housekeeping"),
    SECURITY("Security"),
    OTHER("Other");

    private final String displayName;

    WorkerType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
