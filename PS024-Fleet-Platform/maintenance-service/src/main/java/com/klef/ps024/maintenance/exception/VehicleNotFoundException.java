package com.klef.ps024.maintenance.exception;

public class VehicleNotFoundException extends RuntimeException {

    public VehicleNotFoundException(Long vehicleId) {
        super("Vehicle with id " + vehicleId + " was not found.");
    }
}
