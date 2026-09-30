package com.company.kra.modules.report.repository;

import com.company.kra.modules.report.entity.ReportRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReportRequestRepository extends JpaRepository<ReportRequest, Long> {

    Page<ReportRequest> findByRequestedByOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<ReportRequest> findByIdAndCompanyId(Long id, Long companyId);
}
