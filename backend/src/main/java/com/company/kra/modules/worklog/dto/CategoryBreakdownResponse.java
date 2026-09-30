package com.company.kra.modules.worklog.dto;

public record CategoryBreakdownResponse(String categoryName, String colourHex, int minutes,
                                        double percent) {
}
