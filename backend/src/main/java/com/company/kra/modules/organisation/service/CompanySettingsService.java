package com.company.kra.modules.organisation.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.common.enums.HolidayType;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.DuplicateResourceException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.organisation.dto.CompanySettingsRequest;
import com.company.kra.modules.organisation.dto.CompanySettingsResponse;
import com.company.kra.modules.organisation.dto.HolidayRequest;
import com.company.kra.modules.organisation.dto.HolidayResponse;
import com.company.kra.modules.organisation.entity.CompanySettings;
import com.company.kra.modules.organisation.entity.Holiday;
import com.company.kra.modules.organisation.repository.CompanyRepository;
import com.company.kra.modules.organisation.repository.CompanySettingsRepository;
import com.company.kra.modules.organisation.repository.HolidayRepository;
import com.company.kra.security.SecurityUtils;
import com.company.kra.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Working-hour policy and the holiday calendar. Phase 2 reads both of these
 * when it validates a timesheet, so they are configuration rather than code.
 */
@Service
@RequiredArgsConstructor
public class CompanySettingsService {

    private final CompanySettingsRepository settingsRepository;
    private final CompanyRepository companyRepository;
    private final HolidayRepository holidayRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public CompanySettingsResponse get() {
        Long companyId = SecurityUtils.requireCompanyId();
        return toResponse(requireSettings(companyId), companyId);
    }

    /** Phase 2 and later read the entity directly through this method. */
    @Transactional(readOnly = true)
    public CompanySettings settingsFor(Long companyId) {
        return requireSettings(companyId);
    }

    /**
      * Working hours and the working week feed the calendar and every day's
      * expected minutes, so changing them clears both caches rather than
      * leaving the old week in place for the TTL.
      */
    @CacheEvict(cacheNames = {CacheConfig.COMPANY_SETTINGS, CacheConfig.WORKING_CALENDAR},
            allEntries = true)
    @Transactional
    public CompanySettingsResponse update(CompanySettingsRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        CompanySettings settings = requireSettings(companyId);

        if (!request.workEndTime().isAfter(request.workStartTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Work end time must be after work start time");
        }
        int grossMinutes = (request.workEndTime().toSecondOfDay() - request.workStartTime().toSecondOfDay()) / 60;
        if (request.breakMinutes() >= grossMinutes) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Break time cannot fill the entire working day");
        }

        CompanySettingsRequest before = new CompanySettingsRequest(settings.getWorkStartTime(),
                settings.getWorkEndTime(), settings.getBreakMinutes(), settings.getWorkingDays(),
                settings.isWorklogApprovalEnabled(), settings.isOvertimeAllowed(),
                settings.getMaxOvertimeMinutes(), settings.getBackdateWindowDays(),
                settings.getWorklogCutoffTime(), settings.getMinSubmissionRatePct());

        settings.setWorkStartTime(request.workStartTime());
        settings.setWorkEndTime(request.workEndTime());
        settings.setBreakMinutes(request.breakMinutes());
        settings.setWorkingDays(request.workingDays().toUpperCase());
        settings.setWorklogApprovalEnabled(request.worklogApprovalEnabled());
        settings.setOvertimeAllowed(request.overtimeAllowed());
        settings.setMaxOvertimeMinutes(request.maxOvertimeMinutes());
        settings.setBackdateWindowDays(request.backdateWindowDays());
        settings.setWorklogCutoffTime(request.worklogCutoffTime());
        settings.setMinSubmissionRatePct(request.minSubmissionRatePct());
        settingsRepository.save(settings);

        auditService.record(AuditModule.SETTINGS, AuditAction.UPDATE, "CompanySettings", settings.getId(),
                before, request, null);
        return toResponse(settings, companyId);
    }

    @Transactional(readOnly = true)
    public List<HolidayResponse> holidays(int year) {
        Long companyId = SecurityUtils.requireCompanyId();
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        return holidayRepository
                .findByCompanyIdAndHolidayDateBetweenAndActiveTrueOrderByHolidayDateAsc(companyId, from, to)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @CacheEvict(cacheNames = CacheConfig.WORKING_CALENDAR, allEntries = true)
    @Transactional
    public HolidayResponse addHoliday(HolidayRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        if (holidayRepository.existsByCompanyIdAndHolidayDateAndActiveTrue(companyId, request.holidayDate())) {
            throw new DuplicateResourceException("Holiday", "date", request.holidayDate());
        }
        Holiday holiday = new Holiday();
        holiday.setCompanyId(companyId);
        holiday.setHolidayDate(request.holidayDate());
        holiday.setName(request.name().trim());
        holiday.setHolidayType(request.holidayType() == null ? HolidayType.PUBLIC : request.holidayType());
        Holiday saved = holidayRepository.save(holiday);

        auditService.record(AuditModule.SETTINGS, AuditAction.CREATE, "Holiday", saved.getId(),
                null, request, null);
        return toResponse(saved);
    }

    @CacheEvict(cacheNames = CacheConfig.WORKING_CALENDAR, allEntries = true)
    @Transactional
    public void removeHoliday(Long id) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday", id));
        holiday.setActive(false);
        holidayRepository.save(holiday);
        auditService.record(AuditModule.SETTINGS, AuditAction.DELETE, "Holiday", id, null, null, "Removed");
    }

    private CompanySettings requireSettings(Long companyId) {
        return settingsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company settings", companyId));
    }

    private CompanySettingsResponse toResponse(CompanySettings settings, Long companyId) {
        String companyName = companyRepository.findById(companyId)
                .map(company -> company.getName())
                .orElse(null);
        return new CompanySettingsResponse(companyId, companyName, settings.getWorkStartTime(),
                settings.getWorkEndTime(), settings.getBreakMinutes(), settings.getWorkingDays(),
                settings.isWorklogApprovalEnabled(), settings.isOvertimeAllowed(),
                settings.getMaxOvertimeMinutes(), settings.getBackdateWindowDays(),
                settings.getWorklogCutoffTime(), settings.getMinSubmissionRatePct(),
                settings.netWorkingMinutes());
    }

    private HolidayResponse toResponse(Holiday holiday) {
        return new HolidayResponse(holiday.getId(), holiday.getHolidayDate(), holiday.getName(),
                holiday.getHolidayType(),
                holiday.getHolidayDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
    }
}
