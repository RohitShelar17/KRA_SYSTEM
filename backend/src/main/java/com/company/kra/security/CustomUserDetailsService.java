package com.company.kra.security;

import com.company.kra.common.enums.UserStatus;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.identity.entity.User;
import com.company.kra.modules.identity.repository.UserRepository;
import com.company.kra.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    /** Accepts either the username or the work email, matching the sign-in form. */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameIgnoreCase(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .orElseThrow(() -> new UsernameNotFoundException("No account found for " + identifier));
        return toPrincipal(user);
    }

    /**
      * Read on every authenticated request, so it is cached — and evicted
      * wherever roles, permissions, status or password change.
      */
    @Cacheable(cacheNames = CacheConfig.USER_PRINCIPALS, key = "#userId")
    @Transactional(readOnly = true)
    public AppUserPrincipal loadByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for id " + userId));
        return toPrincipal(user);
    }

    public AppUserPrincipal toPrincipal(User user) {
        Optional<Employee> employee = employeeRepository.findByUserId(user.getId());
        return AppUserPrincipal.builder()
                .userId(user.getId())
                .employeeId(employee.map(Employee::getId).orElse(null))
                .companyId(user.getCompanyId())
                .departmentId(employee.map(e -> e.getDepartment().getId()).orElse(null))
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(employee.map(Employee::fullName).orElse(user.getUsername()))
                .passwordHash(user.getPasswordHash())
                .enabled(user.isActive() && user.getStatus() == UserStatus.ACTIVE)
                .accountLocked(user.isCurrentlyLocked())
                .mustChangePassword(user.isMustChangePassword())
                .dataScope(user.widestScope())
                .roles(user.roleCodes())
                .permissions(user.permissionCodes())
                .build();
    }
}
