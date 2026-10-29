package com.klef.ps024.vehicle.exception;

public class DuplicateRegistrationNumberException extends RuntimeException {

    public DuplicateRegistrationNumberException(String registrationNumber) {
        super("A vehicle with registration number " + registrationNumber + " already exists.");
    }

    public DuplicateRegistrationNumberException(String registrationNumber, Throwable cause) {
        super("A vehicle with registration number " + registrationNumber + " already exists.", cause);
    }
}
