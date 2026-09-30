package com.company.kra.modules.performance.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.DataScope;
import com.company.kra.exception.BusinessException;
import com.company.kra.modules.performance.dto.RatingBandRequest;
import com.company.kra.modules.performance.entity.PerformanceRatingBand;
import com.company.kra.modules.performance.repository.PerformanceRatingBandRepository;
import com.company.kra.security.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bands decide what a number is called, so a gap or an overlap would leave a
 * score with no rating or two. Both are refused.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RatingBandValidationTest {

    @Mock private PerformanceRatingBandRepository bandRepository;
    @Mock private AuditService auditService;

    private RatingBandService service;

    @BeforeEach
    void setUp() {
        service = new RatingBandService(bandRepository, auditService);
        when(bandRepository.findByCompanyIdAndActiveTrueOrderByDisplayOrderAsc(anyLong()))
                .thenReturn(List.of());
        when(bandRepository.save(any())).thenAnswer(call -> {
            PerformanceRatingBand band = call.getArgument(0);
            band.setId(1L);
            return band;
        });

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal(), null, Set.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A contiguous set of bands saves")
    void contiguousBandsSave() {
        assertThatCode(() -> service.replaceAll(defaultBands())).doesNotThrowAnyException();
        verify(bandRepository, org.mockito.Mockito.atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("A gap between bands is refused, naming both sides")
    void gapIsRefused() {
        List<RatingBandRequest> bands = List.of(
                band("Low", "0", "49.99"),
                band("High", "60", "200"));

        assertThatThrownBy(() -> service.replaceAll(bands))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("gap")
                .hasMessageContaining("Low")
                .hasMessageContaining("High");
    }

    @Test
    @DisplayName("Overlapping bands are refused")
    void overlapIsRefused() {
        List<RatingBandRequest> bands = List.of(
                band("Low", "0", "60"),
                band("High", "50", "200"));

        assertThatThrownBy(() -> service.replaceAll(bands))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("overlaps");
    }

    @Test
    @DisplayName("Bands must start at zero so no score falls below every band")
    void mustStartAtZero() {
        List<RatingBandRequest> bands = List.of(
                band("Low", "10", "49.99"),
                band("High", "50", "200"));

        assertThatThrownBy(() -> service.replaceAll(bands))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must start at 0");
    }

    @Test
    @DisplayName("The top band must reach 100")
    void mustReachHundred() {
        List<RatingBandRequest> bands = List.of(
                band("Low", "0", "49.99"),
                band("High", "50", "80"));

        assertThatThrownBy(() -> service.replaceAll(bands))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least 100");
    }

    @Test
    @DisplayName("An inverted band is refused")
    void invertedBandRefused() {
        List<RatingBandRequest> bands = List.of(band("Backwards", "0", "0"));

        assertThatThrownBy(() -> service.replaceAll(bands))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at or below its lower bound");

        verify(bandRepository, never()).save(any());
    }

    @Test
    @DisplayName("A score lands in exactly one band")
    void scoreResolvesToOneBand() {
        when(bandRepository.findByCompanyIdAndActiveTrueOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(
                        entity("Outstanding", "90", "200"),
                        entity("Excellent", "80", "89.99"),
                        entity("Good", "70", "79.99")));

        assertThat(service.bandFor(1L, new BigDecimal("84.50")))
                .map(PerformanceRatingBand::getName)
                .contains("Excellent");
        assertThat(service.bandFor(1L, new BigDecimal("90")))
                .map(PerformanceRatingBand::getName)
                .contains("Outstanding");
        assertThat(service.bandFor(1L, new BigDecimal("111.50")))
                .map(PerformanceRatingBand::getName)
                .contains("Outstanding");
    }

    @Test
    @DisplayName("An unscored period has no band rather than the lowest one")
    void nullScoreHasNoBand() {
        assertThat(service.bandFor(1L, null)).isEmpty();
    }

    // ------------------------------------------------------------- fixtures

    private List<RatingBandRequest> defaultBands() {
        return List.of(
                band("Outstanding", "90", "200"),
                band("Excellent", "80", "89.99"),
                band("Good", "70", "79.99"),
                band("Meets Expectations", "60", "69.99"),
                band("Needs Improvement", "50", "59.99"),
                band("Unsatisfactory", "0", "49.99"));
    }

    private RatingBandRequest band(String name, String min, String max) {
        return new RatingBandRequest(name, null, new BigDecimal(min), new BigDecimal(max), null);
    }

    private PerformanceRatingBand entity(String name, String min, String max) {
        PerformanceRatingBand band = new PerformanceRatingBand();
        band.setName(name);
        band.setMinScore(new BigDecimal(min));
        band.setMaxScore(new BigDecimal(max));
        return band;
    }

    private AppUserPrincipal principal() {
        return AppUserPrincipal.builder()
                .userId(1L)
                .employeeId(1L)
                .companyId(1L)
                .departmentId(1L)
                .username("hr")
                .email("hr@company.com")
                .displayName("HR Admin")
                .passwordHash("hash")
                .enabled(true)
                .accountLocked(false)
                .mustChangePassword(false)
                .dataScope(DataScope.ORGANISATION)
                .roles(Set.of("HR_ADMIN"))
                .permissions(Set.of("SETTINGS_MANAGE"))
                .build();
    }
}
