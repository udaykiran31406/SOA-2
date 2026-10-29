package com.klef.ps024.vehicle.exception;

public class VehicleNotFoundException extends RuntimeException {

    public VehicleNotFoundException(Long id) {
        super("Vehicle with id " + id + " was not found.");
    }
}
