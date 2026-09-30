package com.company.kra.modules.report.service;

import com.company.kra.common.enums.ReportFilterKey;
import com.company.kra.common.report.ReportRow;
import com.company.kra.common.report.ReportTable;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.modules.report.definition.ReportContext;
import com.company.kra.modules.report.definition.ReportDefinition;
import com.company.kra.modules.report.dto.ReportDefinitionResponse;
import com.company.kra.modules.report.dto.ReportResultResponse;
import com.company.kra.modules.report.registry.ReportRegistry;
import com.company.kra.security.AccessScopeResolver;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs reports: resolves the definition, applies the caller's scope, enforces
 * the row cap and pages the result.
 *
 * <p>Authorisation happens twice on purpose. The catalogue hides a report the
 * caller may not run, and running one checks the permission again — hiding a
 * button is a courtesy, the check is the control.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRegistry registry;
    private final AccessScopeResolver accessScopeResolver;

    @Value("${app.reports.max-rows:20000}")
    private int maxRows;

    /** The reports this caller may run, in registry order. */
    @Transactional(readOnly = true)
    public List<ReportDefinitionResponse> catalogue() {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        return registry.all().stream()
                .filter(definition -> mayRun(principal, definition))
                .map(definition -> new ReportDefinitionResponse(definition.key(), definition.title(),
                        definition.description(), definition.group(), definition.filters()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReportResultResponse run(String key, Map<String, String> filters, int page, int size) {
        ReportDefinition definition = require(key);
        ReportTable table = execute(definition, filters);

        int pageSize = Math.min(Math.max(size, 1), 200);
        int pageIndex = Math.max(page, 0);
        int from = Math.min(pageIndex * pageSize, table.size());
        int to = Math.min(from + pageSize, table.size());

        List<List<Object>> rows = table.rows().subList(from, to).stream()
                .map(ReportRow::values)
                .toList();

        int totalPages = (int) Math.ceil((double) table.size() / pageSize);
        return new ReportResultResponse(definition.key(), definition.title(),
                definition.description(), table.columns(), rows, pageIndex, pageSize,
                table.size(), Math.max(totalPages, 1), table.size() >= maxRows,
                LocalDateTime.now());
    }

    /** The whole result, for an export. */
    @Transactional(readOnly = true)
    public ReportTable runFully(String key, Map<String, String> filters) {
        return execute(require(key), filters);
    }

    public ReportDefinition require(String key) {
        ReportDefinition definition = registry.require(key);
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        if (!mayRun(principal, definition)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "You do not have access to the " + definition.title() + " report");
        }
        return definition;
    }

    /** Filters as a person reads them, for the header of an exported file. */
    public Map<String, String> describe(ReportDefinition definition, Map<String, String> filters) {
        Map<String, String> described = new LinkedHashMap<>();
        parse(definition, filters).forEach((key, value) ->
                described.put(humanise(key.name()), value));
        return described;
    }

    // ------------------------------------------------------------- internals

    private ReportTable execute(ReportDefinition definition, Map<String, String> filters) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        AccessScopeResolver.Scope scope = accessScopeResolver.visibleEmployeeIds(principal);

        ReportContext context = new ReportContext(principal.getCompanyId(), scope,
                parse(definition, filters));

        ReportTable table = definition.run(context);
        if (table.size() > maxRows) {
            // Refusing beats truncating: a report that silently dropped rows
            // would be read as complete and acted on.
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "This report matched " + table.size() + " rows, over the " + maxRows
                            + " limit. Narrow the date range or filter by department.");
        }
        log.debug("Report {} produced {} rows for user {}", definition.key(), table.size(),
                principal.getUserId());
        return table;
    }

    /**
     * Keeps only the filters this report declared. An unknown or unsupported
     * filter is dropped rather than quietly changing what the report means.
     */
    private Map<ReportFilterKey, String> parse(ReportDefinition definition,
                                               Map<String, String> filters) {
        Map<ReportFilterKey, String> parsed = new EnumMap<>(ReportFilterKey.class);
        if (filters == null) {
            return parsed;
        }
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            ReportFilterKey key = keyFor(entry.getKey());
            if (key != null && definition.filters().contains(key)) {
                parsed.put(key, entry.getValue().trim());
            }
        }
        return parsed;
    }

    /** Accepts both {@code fromDate} and {@code FROM_DATE}. */
    private ReportFilterKey keyFor(String name) {
        String normalised = name.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ENGLISH);
        try {
            return ReportFilterKey.valueOf(normalised);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private boolean mayRun(AppUserPrincipal principal, ReportDefinition definition) {
        return principal.hasPermission("REPORT_VIEW")
                && principal.hasPermission(definition.requiredPermission());
    }

    private String humanise(String key) {
        String words = key.toLowerCase(Locale.ENGLISH).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
