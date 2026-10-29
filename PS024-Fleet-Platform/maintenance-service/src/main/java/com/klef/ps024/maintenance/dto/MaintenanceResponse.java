package com.klef.ps024.maintenance.dto;

import com.klef.ps024.maintenance.entity.MaintenanceRecord;
import com.klef.ps024.maintenance.entity.MaintenanceStatus;
import com.klef.ps024.maintenance.entity.ServiceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record MaintenanceResponse(
        Long id,
        Long vehicleId,
        ServiceType serviceType,
        String description,
        LocalDate scheduledDate,
        LocalDate completedDate,
        BigDecimal cost,
        MaintenanceStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static MaintenanceResponse from(MaintenanceRecord record) {
        return new MaintenanceResponse(
                record.getId(),
                record.getVehicleId(),
                record.getServiceType(),
                record.getDescription(),
                record.getScheduledDate(),
                record.getCompletedDate(),
                record.getCost(),
                record.getStatus(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
