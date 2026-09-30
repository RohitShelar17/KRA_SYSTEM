package com.company.kra.modules.notification.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MarkReadRequest(@NotEmpty(message = "Name at least one notification") List<Long> ids) {
}
