package com.company.kra.modules.report.definitions;

import com.company.kra.common.enums.PerformanceScope;
import com.company.kra.common.enums.PeriodType;
import com.company.kra.common.enums.ReportFilterKey;
import com.company.kra.modules.appraisal.service.AppraisalReportData;
import com.company.kra.modules.kra.service.KraReportData;
import com.company.kra.modules.performance.service.PerformanceReportData;
import com.company.kra.modules.report.definition.ReportContext;
import com.company.kra.modules.report.definition.ReportDefinition;
import com.company.kra.modules.report.definition.SimpleReport;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.Set;

/**
 * The nine reports over goals, scores and appraisals.
 *
 * <p>All of them read stored snapshots or the scorecard the employee already
 * sees. Running a report computes nothing, so it cannot disagree with the
 * screens it summarises — and it cannot move anybody's number.</p>
 */
@Configuration
@RequiredArgsConstructor
public class PerformanceReports {

    private static final String GOALS = "Goals";
    private static final String SCORES = "Performance";
    private static final String APPRAISAL = "Appraisal";

    private final KraReportData kraData;
    private final PerformanceReportData performanceData;
    private final AppraisalReportData appraisalData;

    @Bean
    public ReportDefinition kraPerformanceReport() {
        return new SimpleReport("kra-performance", "KRA performance",
                "Assigned key result areas with their weight and achievement for the period.",
                GOALS, "KRA_READ_TEAM",
                Set.of(ReportFilterKey.EMPLOYEE_ID, ReportFilterKey.DEPARTMENT_ID,
                        ReportFilterKey.YEAR, ReportFilterKey.QUARTER),
                context -> kraData.kraPerformance(context.companyId(), context.scope(),
                        PeriodType.QUARTERLY, context.year(), quarter(context),
                        context.longValue(ReportFilterKey.EMPLOYEE_ID),
                        context.longValue(ReportFilterKey.DEPARTMENT_ID)));
    }

    @Bean
    public ReportDefinition kpiAchievementReport() {
        return new SimpleReport("kpi-achievement", "KPI achievement",
                "Every KPI with its target, actual and achievement — and the reason where one "
                        + "could not be scored.",
                GOALS, "KRA_READ_TEAM",
                Set.of(ReportFilterKey.EMPLOYEE_ID, ReportFilterKey.DEPARTMENT_ID,
                        ReportFilterKey.YEAR, ReportFilterKey.QUARTER),
                context -> kraData.kpiAchievement(context.companyId(), context.scope(),
                        PeriodType.QUARTERLY, context.year(), quarter(context),
                        context.longValue(ReportFilterKey.EMPLOYEE_ID),
                        context.longValue(ReportFilterKey.DEPARTMENT_ID)));
    }

    @Bean
    public ReportDefinition monthlyPerformanceReport() {
        return snapshotReport("monthly-performance", "Monthly performance",
                "Monthly scores with the parameters behind them.", PerformanceScope.MONTHLY,
                ReportFilterKey.MONTH);
    }

    @Bean
    public ReportDefinition quarterlyPerformanceReport() {
        return snapshotReport("quarterly-performance", "Quarterly performance",
                "Quarterly scores and the monthly average they rolled up from.",
                PerformanceScope.QUARTERLY, ReportFilterKey.QUARTER);
    }

    @Bean
    public ReportDefinition annualPerformanceReport() {
        return snapshotReport("annual-performance", "Annual performance",
                "Annual scores and the quarterly average behind them.", PerformanceScope.ANNUAL,
                null);
    }

    @Bean
    public ReportDefinition departmentPerformanceReport() {
        return new SimpleReport("department-performance", "Department performance",
                "Average, lowest and highest monthly score per department.",
                SCORES, "PERFORMANCE_READ_TEAM",
                Set.of(ReportFilterKey.YEAR, ReportFilterKey.MONTH),
                context -> performanceData.departmentPerformance(context.companyId(),
                        context.scope(), context.year(),
                        context.intValue(ReportFilterKey.MONTH)));
    }

    @Bean
    public ReportDefinition topPerformersReport() {
        return rankingReport("top-performers", "Top performers",
                "Highest monthly scores in the period.", false);
    }

    @Bean
    public ReportDefinition lowPerformersReport() {
        return rankingReport("low-performers", "Employees needing support",
                "Lowest monthly scores in the period — a prompt for a conversation, not a verdict.",
                true);
    }

    @Bean
    public ReportDefinition managerReviewReport() {
        return new SimpleReport("manager-review", "Appraisal progress",
                "Where each appraisal stands and what each reviewer rated.",
                APPRAISAL, "PERFORMANCE_REVIEW",
                Set.of(ReportFilterKey.CYCLE_ID, ReportFilterKey.DEPARTMENT_ID,
                        ReportFilterKey.EMPLOYEE_ID),
                context -> appraisalData.managerReview(context.companyId(), context.scope(),
                        context.longValue(ReportFilterKey.CYCLE_ID),
                        context.longValue(ReportFilterKey.DEPARTMENT_ID),
                        context.longValue(ReportFilterKey.EMPLOYEE_ID)));
    }

    // ------------------------------------------------------------- internals

    private ReportDefinition snapshotReport(String key, String title, String description,
                                            PerformanceScope grain, ReportFilterKey periodFilter) {
        Set<ReportFilterKey> filters = periodFilter == null
                ? Set.of(ReportFilterKey.EMPLOYEE_ID, ReportFilterKey.DEPARTMENT_ID,
                        ReportFilterKey.YEAR, ReportFilterKey.RATING_BAND_ID)
                : Set.of(ReportFilterKey.EMPLOYEE_ID, ReportFilterKey.DEPARTMENT_ID,
                        ReportFilterKey.YEAR, periodFilter, ReportFilterKey.RATING_BAND_ID);

        return new SimpleReport(key, title, description, SCORES, "PERFORMANCE_READ_TEAM", filters,
                context -> performanceData.snapshots(context.companyId(), context.scope(), grain,
                        context.year(),
                        periodFilter == null ? null : context.intValue(periodFilter),
                        context.longValue(ReportFilterKey.EMPLOYEE_ID),
                        context.longValue(ReportFilterKey.DEPARTMENT_ID),
                        context.longValue(ReportFilterKey.RATING_BAND_ID)));
    }

    private ReportDefinition rankingReport(String key, String title, String description,
                                           boolean lowestFirst) {
        return new SimpleReport(key, title, description, SCORES, "PERFORMANCE_READ_TEAM",
                Set.of(ReportFilterKey.YEAR, ReportFilterKey.MONTH, ReportFilterKey.DEPARTMENT_ID,
                        ReportFilterKey.LIMIT),
                context -> performanceData.ranking(context.companyId(), context.scope(),
                        context.year(), context.intValue(ReportFilterKey.MONTH),
                        context.longValue(ReportFilterKey.DEPARTMENT_ID), lowestFirst,
                        context.intValue(ReportFilterKey.LIMIT, 20)));
    }

    /** Defaults to the quarter the period sits in today. */
    private int quarter(ReportContext context) {
        Integer explicit = context.intValue(ReportFilterKey.QUARTER);
        return explicit == null ? (LocalDate.now().getMonthValue() - 1) / 3 + 1 : explicit;
    }
}
