package com.klef.ps024.maintenance.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceExceptionHandlerTest {

    @Test
    void mapsDatabaseConstraintConflictsToConflictResponse() {
        MaintenanceExceptionHandler handler = new MaintenanceExceptionHandler();

        ResponseEntity<ApiError> response = handler.handleDataConflict();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .isEqualTo("Maintenance record conflicts with existing data or violates a database constraint.");
    }
}
