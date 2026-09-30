package com.company.kra.modules.kra.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Weightages are adjusted as a set, never one at a time: the total has to stay
 * at 100, and there is no valid intermediate state to save.
 */
public record WeightageUpdateRequest(
        @NotNull @Size(min = 1, message = "Send the full set of weightages")
        List<Item> items,

        @Size(max = 500)
        String reason) {

    public record Item(
            @NotNull Long id,
            @NotNull @DecimalMin(value = "0.00") BigDecimal weightage) {
    }
}
