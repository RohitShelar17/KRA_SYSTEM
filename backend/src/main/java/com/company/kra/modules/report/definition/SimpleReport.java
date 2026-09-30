package com.company.kra.modules.report.definition;

import com.company.kra.common.enums.ReportFilterKey;
import com.company.kra.common.report.ReportTable;

import java.util.Set;
import java.util.function.Function;

/**
 * A report defined as data rather than as a class.
 *
 * <p>Every report shares the same shape — metadata plus one query — so the
 * fifteen definitions are fifteen declarations in a configuration class
 * instead of fifteen near-identical files. The record's components satisfy the
 * interface's accessors directly.</p>
 */
public record SimpleReport(String key, String title, String description, String group,
                           String requiredPermission, Set<ReportFilterKey> filters,
                           Function<ReportContext, ReportTable> runner) implements ReportDefinition {

    @Override
    public ReportTable run(ReportContext context) {
        return runner.apply(context);
    }
}
