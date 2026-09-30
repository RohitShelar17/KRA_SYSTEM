package com.company.kra.modules.identity.dto;

import com.company.kra.common.enums.DataScope;

import java.util.Set;

public record RoleResponse(Long id, String code, String name, String description, DataScope dataScope,
                           boolean systemRole, boolean active, Set<String> permissions,
                           int permissionCount) {
}
