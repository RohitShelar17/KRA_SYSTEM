package com.company.kra.modules.organisation.repository;

import com.company.kra.modules.organisation.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findByCompanyIdAndHolidayDateBetweenAndActiveTrueOrderByHolidayDateAsc(
            Long companyId, LocalDate from, LocalDate to);

    boolean existsByCompanyIdAndHolidayDateAndActiveTrue(Long companyId, LocalDate date);
}
