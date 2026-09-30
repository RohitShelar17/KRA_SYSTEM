package com.company.kra.modules.performance.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.modules.performance.dto.RatingBandRequest;
import com.company.kra.modules.performance.dto.RatingBandResponse;
import com.company.kra.modules.performance.entity.PerformanceRatingBand;
import com.company.kra.modules.performance.repository.PerformanceRatingBandRepository;
import com.company.kra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The rating bands a score lands in.
 *
 * <p>Bands are saved as a complete set and validated to be contiguous from
 * zero upwards with no overlap, so no score can fall between two bands or into
 * two at once.</p>
 */
@Service
@RequiredArgsConstructor
public class RatingBandService {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.011");

    private final PerformanceRatingBandRepository bandRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<RatingBandResponse> list() {
        Long companyId = SecurityUtils.requireCompanyId();
        return bands(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** The band a score belongs to, used when a snapshot is written. */
    @Transactional(readOnly = true)
    public Optional<PerformanceRatingBand> bandFor(Long companyId, BigDecimal score) {
        if (score == null) {
            return Optional.empty();
        }
        return bands(companyId).stream()
                .filter(band -> band.covers(score))
                .findFirst();
    }

    /** The whole band, for anything that needs its colour as well as its name. */
    @Transactional(readOnly = true)
    public Optional<PerformanceRatingBand> byId(Long bandId) {
        return bandId == null ? Optional.empty() : bandRepository.findById(bandId);
    }

    /** The band's name, for messages that quote it back to somebody. */
    @Transactional(readOnly = true)
    public String nameOf(Long bandId) {
        return bandId == null ? null
                : bandRepository.findById(bandId).map(PerformanceRatingBand::getName).orElse(null);
    }

    @Transactional(readOnly = true)
    public Optional<RatingBandResponse> responseFor(Long companyId, BigDecimal score) {
        return bandFor(companyId, score).map(this::toResponse);
    }

    @Transactional
    public List<RatingBandResponse> replaceAll(List<RatingBandRequest> requests) {
        Long companyId = SecurityUtils.requireCompanyId();
        validate(requests);

        List<PerformanceRatingBand> existing = bandRepository
                .findByCompanyIdAndActiveTrueOrderByDisplayOrderAsc(companyId);
        String before = describe(existing.stream().map(this::toResponse).toList());

        existing.forEach(band -> band.setActive(false));
        bandRepository.saveAll(existing);

        List<PerformanceRatingBand> saved = new ArrayList<>();
        int order = 0;
        for (RatingBandRequest request : requests) {
            PerformanceRatingBand band = new PerformanceRatingBand();
            band.setCompanyId(companyId);
            band.setName(request.name().trim());
            band.setDescription(request.description());
            band.setMinScore(request.minScore());
            band.setMaxScore(request.maxScore());
            band.setColourHex(emptyToNull(request.colourHex()));
            band.setDisplayOrder(order);
            saved.add(bandRepository.save(band));
            order += 10;
        }

        List<RatingBandResponse> responses = saved.stream().map(this::toResponse).toList();
        auditService.record(AuditModule.SETTINGS, AuditAction.UPDATE, "PerformanceRatingBand", null,
                Map.of("bands", before), Map.of("bands", describe(responses)), null);
        return responses;
    }

    // ------------------------------------------------------------ internals

    private List<PerformanceRatingBand> bands(Long companyId) {
        return bandRepository.findByCompanyIdAndActiveTrueOrderByDisplayOrderAsc(companyId);
    }

    /**
     * Contiguous and non-overlapping from 0 upwards. Checked by sorting on the
     * lower bound and walking the list, so the message can name the gap.
     */
    private void validate(List<RatingBandRequest> requests) {
        if (requests.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Define at least one rating band");
        }

        List<RatingBandRequest> sorted = new ArrayList<>(requests);
        sorted.sort(Comparator.comparing(RatingBandRequest::minScore));

        for (RatingBandRequest band : sorted) {
            if (band.maxScore().compareTo(band.minScore()) <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "\"" + band.name() + "\" has an upper bound at or below its lower bound");
            }
        }

        if (sorted.get(0).minScore().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "The lowest band must start at 0, so no score falls below every band");
        }

        for (int index = 1; index < sorted.size(); index++) {
            BigDecimal previousMax = sorted.get(index - 1).maxScore();
            BigDecimal currentMin = sorted.get(index).minScore();
            BigDecimal gap = currentMin.subtract(previousMax);

            if (gap.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "\"" + sorted.get(index).name() + "\" overlaps \"" + sorted.get(index - 1).name()
                                + "\". Bands must not share any score.");
            }
            if (gap.compareTo(TOLERANCE) > 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "There is a gap between \"" + sorted.get(index - 1).name() + "\" (ends at "
                                + strip(previousMax) + ") and \"" + sorted.get(index).name()
                                + "\" (starts at " + strip(currentMin) + "). A score in between "
                                + "would have no band.");
            }
        }

        BigDecimal highest = sorted.get(sorted.size() - 1).maxScore();
        if (highest.compareTo(new BigDecimal("100")) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "The highest band must reach at least 100. Over-achievement can exceed 100%, "
                            + "so consider setting it higher.");
        }
    }

    private String describe(List<RatingBandResponse> bands) {
        StringBuilder builder = new StringBuilder();
        bands.forEach(band -> builder.append(band.name()).append('=')
                .append(strip(band.minScore())).append('-').append(strip(band.maxScore())).append(' '));
        return builder.toString().trim();
    }

    private RatingBandResponse toResponse(PerformanceRatingBand band) {
        return new RatingBandResponse(band.getId(), band.getName(), band.getDescription(),
                band.getMinScore(), band.getMaxScore(), band.getColourHex(), band.getDisplayOrder());
    }

    private String strip(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
