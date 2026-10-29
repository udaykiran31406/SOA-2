package com.klef.ps024.vehicle.exception;

public class InvalidVehicleException extends RuntimeException {

    private final String field;

    public InvalidVehicleException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
