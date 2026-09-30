package com.company.kra.modules.identity.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record RolePermissionsRequest(
        @NotEmpty(message = "A role must grant at least one permission")
        Set<String> permissionCodes) {
}
