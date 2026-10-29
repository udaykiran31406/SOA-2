package com.klef.ps024.maintenance.exception;

public class MaintenanceRecordNotFoundException extends RuntimeException {

    public MaintenanceRecordNotFoundException(Long id) {
        super("Maintenance record with id " + id + " was not found.");
    }
}
