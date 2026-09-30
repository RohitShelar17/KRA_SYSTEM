package com.company.kra.security;

import com.company.kra.common.enums.DataScope;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;

/**
 * The authenticated caller. Authorities are permission codes, never role names,
 * so authorisation checks ask what a user may do rather than who they are.
 */
@Getter
@Builder
public class AppUserPrincipal implements UserDetails {

    private final Long userId;
    private final Long employeeId;
    private final Long companyId;
    private final Long departmentId;
    private final String username;
    private final String email;
    private final String displayName;
    private final String passwordHash;
    private final boolean enabled;
    private final boolean accountLocked;
    private final boolean mustChangePassword;
    private final DataScope dataScope;
    private final Set<String> roles;
    private final Set<String> permissions;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !accountLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasPermission(String code) {
        return permissions.contains(code);
    }
}
