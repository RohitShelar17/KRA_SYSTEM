package com.company.kra.modules.notification.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills {placeholders} in a template from an event's values.
 *
 * <p>Deliberately not a general expression language. A notification template
 * is edited by administrators, so the only thing it can do is name a value the
 * event supplied — there is no way to write something that fails at render
 * time and leaves a person un-notified.</p>
 */
@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)}");

    /**
     * An unknown placeholder is left exactly as written. Printing "null" would
     * look like a bug to the reader, and dropping it silently would hide a
     * mis-typed template from whoever wrote it.
     */
    public String render(String template, Map<String, String> values) {
        if (template == null || template.isBlank()) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = values == null ? null : values.get(key);
            matcher.appendReplacement(out, Matcher.quoteReplacement(
                    value == null ? matcher.group() : value));
        }
        matcher.appendTail(out);
        return out.toString().trim();
    }

    /** Truncates to a column width without cutting a word in half mid-sentence. */
    public String fit(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text;
        }
        int cut = text.lastIndexOf(' ', limit - 1);
        return text.substring(0, cut > limit / 2 ? cut : limit - 1) + "…";
    }
}
