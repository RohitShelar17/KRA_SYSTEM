package com.company.kra.modules.appraisal.repository;

import com.company.kra.modules.appraisal.entity.SelfAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SelfAssessmentRepository extends JpaRepository<SelfAssessment, Long> {

    Optional<SelfAssessment> findByAppraisalId(Long appraisalId);

    Optional<SelfAssessment> findByIdAndCompanyId(Long id, Long companyId);
}
