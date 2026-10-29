package com.klef.ps024.maintenance.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class MaintenanceExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaintenanceExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError("Request validation failed.", fieldErrors));
    }

    @ExceptionHandler(InvalidMaintenanceException.class)
    public ResponseEntity<ApiError> handleInvalidMaintenance(InvalidMaintenanceException exception) {
        return ResponseEntity.badRequest().body(new ApiError(
                "Request validation failed.",
                Map.of(exception.getField(), exception.getMessage())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequest() {
        return ResponseEntity.badRequest().body(new ApiError(
                "Request body is malformed or contains an unsupported value.", Map.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleInvalidPathParameter() {
        return ResponseEntity.badRequest().body(new ApiError("Path parameter is invalid.", Map.of()));
    }

    @ExceptionHandler(MaintenanceRecordNotFoundException.class)
    public ResponseEntity<ApiError> handleMaintenanceNotFound(MaintenanceRecordNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(VehicleNotFoundException.class)
    public ResponseEntity<ApiError> handleVehicleNotFound(VehicleNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(VehicleServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleVehicleServiceUnavailable(
            VehicleServiceUnavailableException exception) {
        LOGGER.warn("Vehicle reference validation could not complete.", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                "Maintenance record conflicts with existing data or violates a database constraint.", Map.of()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDatabaseFailure(DataAccessException exception) {
        LOGGER.error("Maintenance database operation failed.", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Maintenance data could not be processed.", Map.of()));
    }
}
