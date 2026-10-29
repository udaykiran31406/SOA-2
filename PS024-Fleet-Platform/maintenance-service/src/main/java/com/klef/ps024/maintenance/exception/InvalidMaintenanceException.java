package com.klef.ps024.maintenance.exception;

public class InvalidMaintenanceException extends RuntimeException {

    private final String field;

    public InvalidMaintenanceException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
