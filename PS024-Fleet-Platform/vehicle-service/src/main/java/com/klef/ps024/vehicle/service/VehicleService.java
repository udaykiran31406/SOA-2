package com.klef.ps024.vehicle.service;

import com.klef.ps024.vehicle.dto.VehicleRequest;
import com.klef.ps024.vehicle.dto.VehicleResponse;
import com.klef.ps024.vehicle.entity.Vehicle;
import com.klef.ps024.vehicle.entity.VehicleStatus;
import com.klef.ps024.vehicle.exception.DuplicateRegistrationNumberException;
import com.klef.ps024.vehicle.exception.InvalidVehicleException;
import com.klef.ps024.vehicle.exception.VehicleNotFoundException;
import com.klef.ps024.vehicle.repository.VehicleRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;

@Service
public class VehicleService {

    private static final int FIRST_MODEL_YEAR = 1886;

    private final VehicleRepository vehicleRepository;

    public VehicleService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        validateYear(request.year());
        String registrationNumber = normalizeRegistrationNumber(request.registrationNumber());
        if (vehicleRepository.existsByRegistrationNumber(registrationNumber)) {
            throw new DuplicateRegistrationNumberException(registrationNumber);
        }

        Vehicle vehicle = new Vehicle(
                registrationNumber,
                request.make().trim(),
                request.model().trim(),
                request.year(),
                VehicleStatus.valueOf(request.status()),
                normalizeAssignedDriver(request.assignedDriver()));
        return save(vehicle);
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> findAll() {
        return vehicleRepository.findAll().stream()
                .map(VehicleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse findById(Long id) {
        return VehicleResponse.from(getVehicle(id));
    }

    @Transactional
    public VehicleResponse update(Long id, VehicleRequest request) {
        validateYear(request.year());
        String registrationNumber = normalizeRegistrationNumber(request.registrationNumber());
        Vehicle vehicle = getVehicle(id);

        if (vehicleRepository.existsByRegistrationNumberAndIdNot(registrationNumber, id)) {
            throw new DuplicateRegistrationNumberException(registrationNumber);
        }

        vehicle.setRegistrationNumber(registrationNumber);
        vehicle.setMake(request.make().trim());
        vehicle.setModel(request.model().trim());
        vehicle.setYear(request.year());
        vehicle.setStatus(VehicleStatus.valueOf(request.status()));
        vehicle.setAssignedDriver(normalizeAssignedDriver(request.assignedDriver()));
        return save(vehicle);
    }

    @Transactional
    public void delete(Long id) {
        Vehicle vehicle = getVehicle(id);
        vehicleRepository.delete(vehicle);
    }

    private Vehicle getVehicle(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new VehicleNotFoundException(id));
    }

    private VehicleResponse save(Vehicle vehicle) {
        try {
            return VehicleResponse.from(vehicleRepository.saveAndFlush(vehicle));
        } catch (DataIntegrityViolationException exception) {
            if (isRegistrationNumberConstraint(exception)) {
                throw new DuplicateRegistrationNumberException(vehicle.getRegistrationNumber(), exception);
            }
            throw exception;
        }
    }

    private void validateYear(Integer year) {
        int lastValidYear = Year.now().getValue() + 1;
        if (year < FIRST_MODEL_YEAR || year > lastValidYear) {
            throw new InvalidVehicleException("year", "year must be between "
                    + FIRST_MODEL_YEAR + " and " + lastValidYear + ".");
        }
    }

    private String normalizeRegistrationNumber(String registrationNumber) {
        return registrationNumber.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String normalizeAssignedDriver(String assignedDriver) {
        if (assignedDriver == null || assignedDriver.isBlank()) {
            return null;
        }
        return assignedDriver.trim();
    }

    private boolean isRegistrationNumberConstraint(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return "uk_vehicles_registration_number".equals(violation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }
}
