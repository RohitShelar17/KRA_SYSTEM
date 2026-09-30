package com.company.kra.modules.identity.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import com.company.kra.common.enums.DataScope;
import com.company.kra.common.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends CompanyScopedEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "reset_token", length = 120)
    private String resetToken;

    @Column(name = "reset_token_expires_at")
    private LocalDateTime resetTokenExpiresAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    public boolean isCurrentlyLocked() {
        return status == UserStatus.LOCKED
                && lockedUntil != null
                && lockedUntil.isAfter(LocalDateTime.now());
    }

    public Set<String> permissionCodes() {
        Set<String> codes = new LinkedHashSet<>();
        roles.forEach(role -> role.getPermissions().forEach(permission -> codes.add(permission.getCode())));
        return codes;
    }

    public Set<String> roleCodes() {
        Set<String> codes = new LinkedHashSet<>();
        roles.forEach(role -> codes.add(role.getCode()));
        return codes;
    }

    /** The widest scope granted by any assigned role. */
    public DataScope widestScope() {
        DataScope scope = DataScope.SELF;
        for (Role role : roles) {
            scope = DataScope.widest(scope, role.getDataScope());
        }
        return scope;
    }
}
