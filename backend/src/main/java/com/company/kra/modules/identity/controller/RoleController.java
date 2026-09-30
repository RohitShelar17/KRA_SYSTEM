package com.company.kra.modules.identity.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.modules.identity.dto.PermissionResponse;
import com.company.kra.modules.identity.dto.RolePermissionsRequest;
import com.company.kra.modules.identity.dto.RoleResponse;
import com.company.kra.modules.identity.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Roles and the permissions they bundle")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_MANAGE','EMPLOYEE_MANAGE')")
    @Operation(summary = "Roles available for assignment")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.of(roleService.list()));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Every permission the platform defines")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> permissions() {
        return ResponseEntity.ok(ApiResponse.of(roleService.permissions()));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "Replace the permission set of a role")
    public ResponseEntity<ApiResponse<RoleResponse>> updatePermissions(
            @PathVariable Long id, @Valid @RequestBody RolePermissionsRequest request) {
        return ResponseEntity.ok(ApiResponse.of(roleService.updatePermissions(id, request),
                "Role permissions updated"));
    }
}
