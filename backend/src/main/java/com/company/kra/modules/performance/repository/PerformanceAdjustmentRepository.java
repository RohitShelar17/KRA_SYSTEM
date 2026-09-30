package com.company.kra.modules.performance.repository;

import com.company.kra.common.enums.PerformanceScope;
import com.company.kra.modules.performance.entity.PerformanceAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PerformanceAdjustmentRepository extends JpaRepository<PerformanceAdjustment, Long> {

    List<PerformanceAdjustment> findBySnapshotTypeAndSnapshotIdOrderByCreatedAtDesc(
            PerformanceScope snapshotType, Long snapshotId);

    List<PerformanceAdjustment> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
}
