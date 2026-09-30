package com.company.kra.modules.worklog.service;

import com.company.kra.common.metrics.TimesheetMetrics;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.organisation.entity.CompanySettings;
import com.company.kra.modules.organisation.entity.Holiday;
import com.company.kra.modules.organisation.repository.HolidayRepository;
import com.company.kra.modules.organisation.service.CompanySettingsService;
import com.company.kra.modules.worklog.repository.WorkLogDayRepository;
import com.company.kra.modules.worklog.validation.TimesheetValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Supplies the KRA and performance modules with figures measured from
 * recorded time. The contract is declared in common; the implementation lives
 * here, next to the data it reads.
 */
@Service
@RequiredArgsConstructor
public class TimesheetMetricsProvider implements TimesheetMetrics {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final WorkLogDayRepository dayRepository;
    private final EmployeeRepository employeeRepository;
    private final HolidayRepository holidayRepository;
    private final CompanySettingsService settingsService;
    private final TimesheetValidator validator;
    private final WorkingCalendar workingCalendar;

    @Override
    @Transactional(readOnly = true)
    public TimeSummary summarise(Long employeeId, LocalDate from, LocalDate to) {
        long logged = dayRepository.sumLoggedMinutesBetween(employeeId, from, to);
        long productive = dayRepository.sumProductiveMinutesBetween(employeeId, from, to);
        int submitted = dayRepository.countSubmittedBetween(employeeId, from, to);
        return new TimeSummary((int) logged, (int) productive, submitted);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal productiveHoursRatioPercent(Long employeeId, LocalDate from, LocalDate to) {
        long logged = dayRepository.sumLoggedMinutesBetween(employeeId, from, to);
        if (logged == 0) {
            // Nothing recorded is not the same as zero productivity, so the
            // parameter stays unmeasured rather than reading as a failure.
            return null;
        }
        long productive = dayRepository.sumProductiveMinutesBetween(employeeId, from, to);
        return BigDecimal.valueOf(productive)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(logged), 2, RoundingMode.HALF_UP);
    }

    /** Working days per the company calendar, with holidays taken out. */
    @Override
    @Transactional(readOnly = true)
    public int workingDaysBetween(Long companyId, LocalDate from, LocalDate to) {
        return workingCalendar.workingDaysBetween(companyId, from, to).size();
    }

    @Override
    @Transactional(readOnly = true)
    public int expectedMinutesPerDay(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) {
            return 0;
        }
        CompanySettings settings = settingsService.settingsFor(employee.getCompanyId());
        return validator.resolveShift(employee, settings).netMinutes();
    }
}
