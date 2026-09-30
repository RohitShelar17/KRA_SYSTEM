package com.company.kra.modules.report.definition;

import com.company.kra.common.enums.ReportFilterKey;
import com.company.kra.security.AccessScopeResolver;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Everything a report is allowed to know: the company, the caller's data scope
 * and the filters they asked for.
 *
 * <p>The scope is not optional decoration. A definition that ignores it would
 * leak another department's rows, so every query built from this context takes
 * {@link #scope()} as a parameter.</p>
 */
public record ReportContext(Long companyId,
                            AccessScopeResolver.Scope scope,
                            Map<ReportFilterKey, String> filters) {

    public Optional<String> raw(ReportFilterKey key) {
        String value = filters.get(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    public Long longValue(ReportFilterKey key) {
        return raw(key).map(Long::valueOf).orElse(null);
    }

    public Integer intValue(ReportFilterKey key) {
        return raw(key).map(Integer::valueOf).orElse(null);
    }

    public String text(ReportFilterKey key) {
        return raw(key).orElse(null);
    }

    public LocalDate date(ReportFilterKey key, LocalDate fallback) {
        return raw(key).map(LocalDate::parse).orElse(fallback);
    }

    public int intValue(ReportFilterKey key, int fallback) {
        Integer value = intValue(key);
        return value == null ? fallback : value;
    }

    /** The year a period report runs for, defaulting to the current one. */
    public int year() {
        return intValue(ReportFilterKey.YEAR, LocalDate.now().getYear());
    }
}
