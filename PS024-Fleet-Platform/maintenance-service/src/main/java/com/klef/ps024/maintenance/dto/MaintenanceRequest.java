package com.klef.ps024.maintenance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MaintenanceRequest(
        @NotNull
        @Positive
        Long vehicleId,

        @NotBlank
        @Pattern(regexp = "PREVENTIVE|REPAIR|INSPECTION|OIL_CHANGE|TIRE_SERVICE|OTHER")
        String serviceType,

        @NotBlank
        @Size(max = 1000)
        String description,

        @NotNull
        LocalDate scheduledDate,

        LocalDate completedDate,

        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal cost,

        @NotBlank
        @Pattern(regexp = "SCHEDULED|IN_PROGRESS|COMPLETED|CANCELLED")
        String status) {
}
