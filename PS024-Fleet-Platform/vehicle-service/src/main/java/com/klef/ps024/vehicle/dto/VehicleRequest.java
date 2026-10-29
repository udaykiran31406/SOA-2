package com.klef.ps024.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record VehicleRequest(
        @NotBlank
        @Size(max = 20)
        String registrationNumber,

        @NotBlank
        @Size(max = 100)
        String make,

        @NotBlank
        @Size(max = 100)
        String model,

        @NotNull
        @Min(1886)
        @Max(2100)
        Integer year,

        @NotBlank
        @Pattern(regexp = "ACTIVE|INACTIVE|UNDER_MAINTENANCE")
        String status,

        @Size(max = 150)
        String assignedDriver) {
}
