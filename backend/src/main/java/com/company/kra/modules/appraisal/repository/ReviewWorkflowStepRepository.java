package com.company.kra.modules.appraisal.repository;

import com.company.kra.common.enums.WorkflowStepType;
import com.company.kra.modules.appraisal.entity.ReviewWorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewWorkflowStepRepository extends JpaRepository<ReviewWorkflowStep, Long> {

    List<ReviewWorkflowStep> findByCompanyIdAndActiveTrueOrderByStepOrderAsc(Long companyId);

    List<ReviewWorkflowStep> findByCompanyIdOrderByStepOrderAsc(Long companyId);

    Optional<ReviewWorkflowStep> findByCompanyIdAndStepType(Long companyId, WorkflowStepType stepType);
}
