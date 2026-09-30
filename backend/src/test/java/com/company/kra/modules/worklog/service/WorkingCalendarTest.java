package com.company.kra.modules.worklog.service;

import com.company.kra.modules.organisation.entity.CompanySettings;
import com.company.kra.modules.organisation.entity.Holiday;
import com.company.kra.modules.organisation.repository.HolidayRepository;
import com.company.kra.modules.organisation.service.CompanySettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * The one definition of a working day.
 *
 * <p>Three parts of the system now read this rather than deciding for
 * themselves &mdash; the score engine, the reports and the dashboards &mdash;
 * so what it answers is worth stating outright. It had no test until a second
 * copy of the rule drifted away from it.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkingCalendarTest {

    private static final Long COMPANY = 1L;

    @Mock private CompanySettingsService settingsService;
    @Mock private HolidayRepository holidayRepository;

    private WorkingCalendar calendar;

    @BeforeEach
    void setUp() {
        calendar = new WorkingCalendar(settingsService, holidayRepository);
        when(settingsService.settingsFor(anyLong())).thenReturn(settings("MON,TUE,WED,THU,FRI"));
        when(holidayRepository.findByCompanyIdAndHolidayDateBetweenAndActiveTrueOrderByHolidayDateAsc(
                anyLong(), any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Week-offs are excluded")
    void weekendsAreNotWorkingDays() {
        // Monday 7 September 2026 to Sunday 13 September: five working days.
        List<LocalDate> days = calendar.workingDaysBetween(COMPANY,
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13));

        assertThat(days).hasSize(5)
                .containsExactly(
                        LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 9),
                        LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11));
    }

    @Test
    @DisplayName("A declared holiday is excluded even though it is a weekday")
    void holidaysAreNotWorkingDays() {
        LocalDate holiday = LocalDate.of(2026, 9, 14); // A Monday.
        when(holidayRepository.findByCompanyIdAndHolidayDateBetweenAndActiveTrueOrderByHolidayDateAsc(
                anyLong(), any(), any())).thenReturn(List.of(holiday(holiday, "Ganesh Chaturthi")));

        List<LocalDate> days = calendar.workingDaysBetween(COMPANY,
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 18));

        assertThat(days).hasSize(4).doesNotContain(holiday);
    }

    @Test
    @DisplayName("A six-day working week counts Saturday")
    void workingWeekIsConfiguration() {
        when(settingsService.settingsFor(anyLong())).thenReturn(settings("MON,TUE,WED,THU,FRI,SAT"));

        List<LocalDate> days = calendar.workingDaysBetween(COMPANY,
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13));

        assertThat(days).hasSize(6).contains(LocalDate.of(2026, 9, 12));
    }

    @Test
    @DisplayName("A single date answers whether that one day is worked")
    void singleDayWindow() {
        assertThat(calendar.workingDaysBetween(COMPANY,
                LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 13))).isEmpty();
        assertThat(calendar.workingDaysBetween(COMPANY,
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 15))).hasSize(1);
    }

    @Test
    @DisplayName("A backwards or absent window is empty rather than an error")
    void nonsenseWindowsAreEmpty() {
        assertThat(calendar.workingDaysBetween(COMPANY,
                LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 14))).isEmpty();
        assertThat(calendar.workingDaysBetween(COMPANY, null, LocalDate.of(2026, 9, 14))).isEmpty();
        assertThat(calendar.workingDaysBetween(COMPANY, LocalDate.of(2026, 9, 14), null)).isEmpty();
    }

    private Holiday holiday(LocalDate date, String name) {
        Holiday holiday = new Holiday();
        holiday.setHolidayDate(date);
        holiday.setName(name);
        return holiday;
    }

    private CompanySettings settings(String workingDays) {
        CompanySettings settings = new CompanySettings();
        settings.setWorkingDays(workingDays);
        return settings;
    }
}
