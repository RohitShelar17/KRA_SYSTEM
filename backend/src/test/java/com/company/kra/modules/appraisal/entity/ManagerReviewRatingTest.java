package com.company.kra.modules.appraisal.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The conversion from a reviewer's 1-5 rating to the 0-100 scale the formula
 * works in. This is where a manager's judgement becomes a number in somebody's
 * score, so the edges matter.
 */
class ManagerReviewRatingTest {

    @Test
    @DisplayName("A 1-5 rating maps onto the 0-100 scale")
    void ratingConvertsToPercent() {
        ManagerReview review = new ManagerReview();
        review.setQualityRating(new BigDecimal("4.0"));
        review.setOverallRating(new BigDecimal("4.5"));

        assertThat(review.qualityPercent()).isEqualByComparingTo("80.00");
        assertThat(review.overallPercent()).isEqualByComparingTo("90.00");
    }

    @Test
    @DisplayName("The bottom and top of the scale land on 20 and 100")
    void scaleEnds() {
        ManagerReview low = new ManagerReview();
        low.setOverallRating(new BigDecimal("1.0"));
        assertThat(low.overallPercent()).isEqualByComparingTo("20.00");

        ManagerReview high = new ManagerReview();
        high.setOverallRating(new BigDecimal("5.0"));
        assertThat(high.overallPercent()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("With no overall rating, the competencies that were rated stand in")
    void overallDerivedFromCompetencies() {
        ManagerReview review = new ManagerReview();
        review.setQualityRating(new BigDecimal("4.0"));
        review.setProductivityRating(new BigDecimal("5.0"));
        review.setTeamworkRating(new BigDecimal("3.0"));

        // Mean of 4, 5, 3 is 4, which is 80 on the formula's scale.
        assertThat(review.overallPercent()).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("An explicit overall rating wins over the derived mean")
    void explicitOverallWins() {
        ManagerReview review = new ManagerReview();
        review.setQualityRating(new BigDecimal("2.0"));
        review.setProductivityRating(new BigDecimal("2.0"));
        review.setOverallRating(new BigDecimal("5.0"));

        assertThat(review.overallPercent()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("A review with nothing rated yields nothing, not a zero")
    void emptyReviewYieldsNull() {
        ManagerReview review = new ManagerReview();

        assertThat(review.overallPercent()).isNull();
        assertThat(review.qualityPercent()).isNull();
    }

    @Test
    @DisplayName("The KRA and KPI ratings do not distort the derived overall")
    void goalRatingsAreNotCompetencies() {
        ManagerReview review = new ManagerReview();
        review.setQualityRating(new BigDecimal("4.0"));
        review.setKraRating(new BigDecimal("1.0"));
        review.setKpiRating(new BigDecimal("1.0"));

        // Only the quality competency was rated, so the mean is 4 -> 80.
        assertThat(review.overallPercent()).isEqualByComparingTo("80.00");
    }
}
