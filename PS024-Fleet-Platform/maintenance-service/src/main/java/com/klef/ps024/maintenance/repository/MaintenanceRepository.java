package com.klef.ps024.maintenance.repository;

import com.klef.ps024.maintenance.entity.MaintenanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceRepository extends JpaRepository<MaintenanceRecord, Long> {
}
