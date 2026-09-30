package com.company.kra.modules.appraisal.repository;

import com.company.kra.modules.appraisal.entity.ReviewStepAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewStepActionRepository extends JpaRepository<ReviewStepAction, Long> {

    List<ReviewStepAction> findByAppraisalIdOrderByCreatedAtAsc(Long appraisalId);
}
