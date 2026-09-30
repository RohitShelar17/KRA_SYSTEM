package com.company.kra.security;

import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AppUserPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static AppUserPrincipal requirePrincipal() {
        return currentPrincipal()
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID, "No authenticated user"));
    }

    public static Optional<Long> currentUserId() {
        return currentPrincipal().map(AppUserPrincipal::getUserId);
    }

    public static Long requireCompanyId() {
        return requirePrincipal().getCompanyId();
    }
}
