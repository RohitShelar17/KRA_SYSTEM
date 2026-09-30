package com.company.kra.modules.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Templates are edited by administrators, so the renderer has to behave
 * sensibly for text nobody reviewed.
 */
class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    @DisplayName("Placeholders are replaced with the event's values")
    void fillsPlaceholders() {
        String text = renderer.render("{employeeName} submitted {workDate}",
                Map.of("employeeName", "Priya Nair", "workDate", "2026-08-12"));

        assertThat(text).isEqualTo("Priya Nair submitted 2026-08-12");
    }

    @Test
    @DisplayName("A placeholder the event did not supply is left as written, not printed as null")
    void unknownPlaceholderSurvives() {
        String text = renderer.render("Your {periodLabel} score is {score}",
                Map.of("periodLabel", "August 2026"));

        assertThat(text).isEqualTo("Your August 2026 score is {score}");
        assertThat(text).doesNotContain("null");
    }

    @Test
    @DisplayName("A value containing a dollar or backslash is inserted literally")
    void regexSpecialCharactersAreSafe() {
        String text = renderer.render("Reason: {reason}",
                Map.of("reason", "cost centre $12 \\ revised"));

        assertThat(text).isEqualTo("Reason: cost centre $12 \\ revised");
    }

    @Test
    @DisplayName("An empty template renders as empty rather than throwing")
    void emptyTemplate() {
        assertThat(renderer.render(null, Map.of())).isEmpty();
        assertThat(renderer.render("  ", Map.of())).isEmpty();
    }

    @Test
    @DisplayName("Text is trimmed to a column width on a word boundary")
    void fitsToColumnWidth() {
        String fitted = renderer.fit("The quick brown fox jumped over the lazy dog", 20);

        assertThat(fitted).hasSizeLessThanOrEqualTo(21);
        assertThat(fitted).endsWith("…");
        assertThat(fitted).startsWith("The quick brown");
    }

    @Test
    @DisplayName("Text already short enough is left exactly as it is")
    void shortTextUntouched() {
        assertThat(renderer.fit("Short enough", 50)).isEqualTo("Short enough");
        assertThat(renderer.fit(null, 50)).isNull();
    }
}
