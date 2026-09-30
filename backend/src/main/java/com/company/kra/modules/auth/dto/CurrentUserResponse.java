package com.company.kra.modules.auth.dto;

import java.util.Set;

/**
 * Everything the shell needs to render itself: identity, where the person sits
 * in the organisation, and the permission codes the sidebar filters on.
 */
public record CurrentUserResponse(Long userId, Long employeeId, String employeeCode, String username,
                                  String fullName, String email, String phone, Long departmentId,
                                  String departmentName, Long teamId, String teamName, String designation,
                                  Long reportingManagerId, String reportingManagerName,
                                  String profileImageUrl, String dataScope, boolean mustChangePassword,
                                  Set<String> roles, Set<String> permissions) {
}
