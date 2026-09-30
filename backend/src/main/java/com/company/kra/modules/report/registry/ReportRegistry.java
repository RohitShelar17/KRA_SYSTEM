package com.company.kra.modules.report.registry;

import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.report.definition.ReportDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every report the application knows about, indexed by key.
 *
 * <p>Definitions are found as beans, so adding one is a bean and nothing else.
 * Duplicate keys fail at startup rather than silently shadowing each other —
 * two reports answering to the same name is the kind of thing that is
 * discovered months later by an export that contains the wrong rows.</p>
 */
@Slf4j
@Component
public class ReportRegistry {

    private final Map<String, ReportDefinition> byKey = new LinkedHashMap<>();

    public ReportRegistry(List<ReportDefinition> definitions) {
        definitions.stream()
                .sorted(Comparator.comparing(ReportDefinition::group)
                        .thenComparing(ReportDefinition::title))
                .forEach(definition -> {
                    ReportDefinition clash = byKey.put(definition.key(), definition);
                    if (clash != null) {
                        throw new IllegalStateException(
                                "Two reports claim the key '" + definition.key() + "'");
                    }
                });
        log.info("Report registry holds {} definitions", byKey.size());
    }

    public List<ReportDefinition> all() {
        return List.copyOf(byKey.values());
    }

    public ReportDefinition require(String key) {
        ReportDefinition definition = byKey.get(key);
        if (definition == null) {
            throw new ResourceNotFoundException("Report", key);
        }
        return definition;
    }
}
