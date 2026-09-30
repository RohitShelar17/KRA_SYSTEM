package com.company.kra.modules.worklog.repository;

import com.company.kra.modules.worklog.entity.WorkLogApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkLogApprovalRepository extends JpaRepository<WorkLogApproval, Long> {

    List<WorkLogApproval> findByWorkLogDayIdOrderByCreatedAtAsc(Long workLogDayId);
}
