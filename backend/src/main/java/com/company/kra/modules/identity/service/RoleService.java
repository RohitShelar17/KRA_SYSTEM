package com.company.kra.modules.identity.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.identity.dto.PermissionResponse;
import com.company.kra.modules.identity.dto.RolePermissionsRequest;
import com.company.kra.modules.identity.dto.RoleResponse;
import com.company.kra.modules.identity.entity.Permission;
import com.company.kra.modules.identity.entity.Role;
import com.company.kra.modules.identity.repository.PermissionRepository;
import com.company.kra.modules.identity.repository.RoleRepository;
import com.company.kra.security.SecurityUtils;
import com.company.kra.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        Long companyId = SecurityUtils.requireCompanyId();
        return roleRepository.findByCompanyIdAndActiveTrueOrderByNameAsc(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> permissions() {
        return permissionRepository.findAllByOrderByModuleAscCodeAsc().stream()
                .map(p -> new PermissionResponse(p.getId(), p.getCode(), p.getName(), p.getModule(),
                        p.getDescription()))
                .toList();
    }

    /**
     * Permission bundles are editable so a company can shape its own roles.
     * The change is audited because it silently widens what people can see.
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.USER_PRINCIPALS, allEntries = true)
    public RoleResponse updatePermissions(Long roleId, RolePermissionsRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Role role = roleRepository.findById(roleId)
                .filter(r -> r.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        Set<String> before = role.getPermissions().stream()
                .map(Permission::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Permission> permissions = permissionRepository.findByCodeIn(new ArrayList<>(request.permissionCodes()));
        if (permissions.size() != request.permissionCodes().size()) {
            Set<String> found = permissions.stream().map(Permission::getCode).collect(Collectors.toSet());
            Set<String> missing = new LinkedHashSet<>(request.permissionCodes());
            missing.removeAll(found);
            throw new ResourceNotFoundException("Permission", String.join(", ", missing));
        }

        role.setPermissions(new LinkedHashSet<>(permissions));
        roleRepository.save(role);

        auditService.record(AuditModule.SECURITY, AuditAction.UPDATE, "Role", roleId,
                before, request.permissionCodes(), "Role permissions changed");
        return toResponse(role);
    }

    private RoleResponse toResponse(Role role) {
        Set<String> codes = role.getPermissions().stream()
                .map(Permission::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                role.getDataScope(), role.isSystemRole(), role.isActive(), codes, codes.size());
    }
}
