package com.company.kra.common.dto;

/** Lightweight id/label pair for dropdowns, so list screens do not fetch full records. */
public record OptionDto(Long id, String code, String label) {
}
