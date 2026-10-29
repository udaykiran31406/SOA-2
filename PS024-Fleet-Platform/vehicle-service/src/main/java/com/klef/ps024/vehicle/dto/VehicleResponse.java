package com.klef.ps024.vehicle.dto;

import com.klef.ps024.vehicle.entity.Vehicle;
import com.klef.ps024.vehicle.entity.VehicleStatus;

import java.time.Instant;

public record VehicleResponse(
        Long id,
        String registrationNumber,
        String make,
        String model,
        Integer year,
        VehicleStatus status,
        String assignedDriver,
        Instant createdAt,
        Instant updatedAt) {

    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getRegistrationNumber(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicle.getStatus(),
                vehicle.getAssignedDriver(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt());
    }
}
