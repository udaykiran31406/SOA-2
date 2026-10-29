package com.klef.ps024.vehicle.exception;

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
public class VehicleExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(VehicleExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError("Request validation failed.", fieldErrors));
    }

    @ExceptionHandler(InvalidVehicleException.class)
    public ResponseEntity<ApiError> handleInvalidVehicle(InvalidVehicleException exception) {
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

    @ExceptionHandler(VehicleNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(VehicleNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(DuplicateRegistrationNumberException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateRegistrationNumberException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                "Vehicle conflicts with existing data or violates a database constraint.", Map.of()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDatabaseFailure(DataAccessException exception) {
        LOGGER.error("Vehicle database operation failed.", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Vehicle data could not be processed.", Map.of()));
    }
}
