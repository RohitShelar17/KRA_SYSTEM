package com.company.kra.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The startup gate that stops production running on development defaults.
 * Each of these is a configuration mistake that is invisible until it is
 * exploited, which is why it is a refusal rather than a warning in a log.
 */
class ProductionReadinessCheckTest {

    private static final String GOOD_SECRET = "a-real-secret-of-more-than-thirty-two-characters";

    @Test
    @DisplayName("A properly configured deployment starts")
    void goodConfigurationPasses() {
        assertThatCode(() -> check(GOOD_SECRET, "a-real-password", "https://kra.example.com").verify())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("The shipped development signing key is refused")
    void defaultSecretIsRefused() {
        assertThatThrownBy(() -> check(
                "change-me-in-every-environment-this-is-a-development-only-secret-key-256bit",
                "a-real-password", "https://kra.example.com").verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET is still the development default");
    }

    @Test
    @DisplayName("A signing key short enough to brute force is refused")
    void shortSecretIsRefused() {
        assertThatThrownBy(() -> check("too-short", "a-real-password", "https://kra.example.com")
                .verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shorter than 32 characters");
    }

    @Test
    @DisplayName("An empty database password is refused")
    void emptyDatabasePasswordIsRefused() {
        assertThatThrownBy(() -> check(GOOD_SECRET, "  ", "https://kra.example.com").verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD is empty");
    }

    @Test
    @DisplayName("CORS still allowing localhost is refused")
    void localhostOriginIsRefused() {
        assertThatThrownBy(() -> check(GOOD_SECRET, "a-real-password",
                "https://kra.example.com,http://localhost:5173").verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("still allows localhost");
    }

    @Test
    @DisplayName("Every problem is named, not just the first one")
    void namesEveryProblem() {
        // "change-me" is both the shipped default and too short, and the check
        // says so twice rather than stopping at the first thing it noticed.
        assertThatThrownBy(() -> check("change-me", "", "http://localhost:5173").verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("4 unsafe setting(s)")
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageContaining("localhost");
    }

    private ProductionReadinessCheck check(String secret, String password, String origins) {
        return new ProductionReadinessCheck(secret, password, origins);
    }
}
