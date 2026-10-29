package com.klef.ps024.maintenance.service;

import com.klef.ps024.maintenance.dto.MaintenanceRequest;
import com.klef.ps024.maintenance.dto.MaintenanceResponse;
import com.klef.ps024.maintenance.entity.MaintenanceRecord;
import com.klef.ps024.maintenance.entity.MaintenanceStatus;
import com.klef.ps024.maintenance.entity.ServiceType;
import com.klef.ps024.maintenance.exception.InvalidMaintenanceException;
import com.klef.ps024.maintenance.exception.MaintenanceRecordNotFoundException;
import com.klef.ps024.maintenance.repository.MaintenanceRepository;
import com.klef.ps024.maintenance.service.vehicle.VehicleReferenceValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final VehicleReferenceValidator vehicleReferenceValidator;

    public MaintenanceService(
            MaintenanceRepository maintenanceRepository,
            VehicleReferenceValidator vehicleReferenceValidator) {
        this.maintenanceRepository = maintenanceRepository;
        this.vehicleReferenceValidator = vehicleReferenceValidator;
    }

    @Transactional
    public MaintenanceResponse create(MaintenanceRequest request) {
        validateDates(request);
        vehicleReferenceValidator.assertVehicleExists(request.vehicleId());
        MaintenanceRecord record = new MaintenanceRecord(
                request.vehicleId(),
                ServiceType.valueOf(request.serviceType()),
                request.description().trim(),
                request.scheduledDate(),
                request.completedDate(),
                request.cost(),
                MaintenanceStatus.valueOf(request.status()));
        return save(record);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceResponse> findAll() {
        return maintenanceRepository.findAll().stream()
                .map(MaintenanceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MaintenanceResponse findById(Long id) {
        return MaintenanceResponse.from(getRecord(id));
    }

    @Transactional
    public MaintenanceResponse update(Long id, MaintenanceRequest request) {
        validateDates(request);
        MaintenanceRecord record = getRecord(id);
        vehicleReferenceValidator.assertVehicleExists(request.vehicleId());
        record.setVehicleId(request.vehicleId());
        record.setServiceType(ServiceType.valueOf(request.serviceType()));
        record.setDescription(request.description().trim());
        record.setScheduledDate(request.scheduledDate());
        record.setCompletedDate(request.completedDate());
        record.setCost(request.cost());
        record.setStatus(MaintenanceStatus.valueOf(request.status()));
        return save(record);
    }

    @Transactional
    public void delete(Long id) {
        maintenanceRepository.delete(getRecord(id));
    }

    private MaintenanceRecord getRecord(Long id) {
        return maintenanceRepository.findById(id)
                .orElseThrow(() -> new MaintenanceRecordNotFoundException(id));
    }

    private MaintenanceResponse save(MaintenanceRecord record) {
        return MaintenanceResponse.from(maintenanceRepository.saveAndFlush(record));
    }

    private void validateDates(MaintenanceRequest request) {
        if (request.completedDate() != null
                && request.completedDate().isBefore(request.scheduledDate())) {
            throw new InvalidMaintenanceException(
                    "completedDate", "completedDate must not be before scheduledDate.");
        }

        if ("COMPLETED".equals(request.status()) && request.completedDate() == null) {
            throw new InvalidMaintenanceException(
                    "completedDate", "completedDate is required when status is COMPLETED.");
        }
    }
}
