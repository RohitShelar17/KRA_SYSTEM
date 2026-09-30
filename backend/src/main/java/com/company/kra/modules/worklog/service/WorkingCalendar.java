package com.company.kra.modules.worklog.service;

import com.company.kra.modules.organisation.entity.CompanySettings;
import com.company.kra.modules.organisation.entity.Holiday;
import com.company.kra.modules.organisation.repository.HolidayRepository;
import com.company.kra.modules.organisation.service.CompanySettingsService;
import com.company.kra.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which dates a company actually works.
 *
 * <p>One definition, used by the metrics the score engine reads and by the
 * "who has not filed" report. Two copies of this rule would eventually
 * disagree, and then a person would be chased for a day the score never
 * expected.</p>
 */
@Component
@RequiredArgsConstructor
public class WorkingCalendar {

    private final CompanySettingsService settingsService;
    private final HolidayRepository holidayRepository;

    /**
      * Cached: the score engine asks this for every employee for the same
      * window, and the answer only changes when the holiday list or the
      * working-week setting does.
      */
    @Cacheable(cacheNames = CacheConfig.WORKING_CALENDAR, key = "#companyId + ':' + #from + ':' + #to")
    @Transactional(readOnly = true)
    public List<LocalDate> workingDaysBetween(Long companyId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        CompanySettings settings = settingsService.settingsFor(companyId);

        Set<LocalDate> holidays = new HashSet<>();
        List<Holiday> rows = holidayRepository
                .findByCompanyIdAndHolidayDateBetweenAndActiveTrueOrderByHolidayDateAsc(
                        companyId, from, to);
        rows.forEach(holiday -> holidays.add(holiday.getHolidayDate()));

        String workingDays = settings.getWorkingDays().toUpperCase(Locale.ENGLISH);
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            String token = cursor.getDayOfWeek()
                    .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    .toUpperCase(Locale.ENGLISH);
            if (workingDays.contains(token) && !holidays.contains(cursor)) {
                dates.add(cursor);
            }
        }
        return dates;
    }
}
