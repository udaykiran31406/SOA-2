package com.klef.ps024.maintenance.exception;

public class VehicleServiceUnavailableException extends RuntimeException {

    public VehicleServiceUnavailableException(Throwable cause) {
        super("Vehicle Service is unavailable; the maintenance request could not verify its vehicle.", cause);
    }
}
