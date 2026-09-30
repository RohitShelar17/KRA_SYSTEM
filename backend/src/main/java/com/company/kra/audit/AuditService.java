package com.company.kra.audit;

import com.company.kra.common.enums.AuditAction;
import com.company.kra.observability.TraceIdFilter;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Writes the explicit change trail. Rows are appended in their own transaction
 * so a rolled-back business operation still leaves the attempt recorded, and a
 * failure to audit never fails the operation being audited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditModule module, AuditAction action, String entityType, Long entityId,
                       Object oldValue, Object newValue, String reason) {
        try {
            AuditLog entry = new AuditLog();
            entry.setModule(module.name());
            entry.setAction(action.name());
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setOldValue(toJson(oldValue));
            entry.setNewValue(toJson(newValue));
            entry.setReason(reason);

            SecurityUtils.currentPrincipal().ifPresent(principal -> {
                entry.setActorUserId(principal.getUserId());
                entry.setActorName(principal.getDisplayName());
                entry.setCompanyId(principal.getCompanyId());
            });

            currentRequest().ifPresent(request -> {
                entry.setIpAddress(clientIp(request));
                entry.setUserAgent(truncate(request.getHeader("User-Agent"), 255));
            });
            entry.setTraceId(TraceIdFilter.current());

            auditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            log.error("Failed to write audit entry for {} {} {}", module, action, entityType, ex);
        }
    }

    public void record(AuditModule module, AuditAction action, String entityType, Long entityId) {
        record(module, action, entityType, entityId, null, null, null);
    }

    /** Sign-in attempts are audited even when no principal exists yet. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAuthEvent(AuditAction action, Long userId, String actorName, String detail) {
        try {
            AuditLog entry = new AuditLog();
            entry.setModule(AuditModule.AUTH.name());
            entry.setAction(action.name());
            entry.setEntityType("User");
            entry.setEntityId(userId);
            entry.setActorUserId(userId);
            entry.setActorName(actorName);
            entry.setReason(detail);
            currentRequest().ifPresent(request -> {
                entry.setIpAddress(clientIp(request));
                entry.setUserAgent(truncate(request.getHeader("User-Agent"), 255));
            });
            entry.setTraceId(TraceIdFilter.current());
            auditLogRepository.save(entry);
        } catch (RuntimeException ex) {
            log.error("Failed to write auth audit entry {}", action, ex);
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialise audit payload of type {}", value.getClass().getSimpleName());
            return null;
        }
    }

    private Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 45);
        }
        return truncate(request.getRemoteAddr(), 45);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Convenience for services that hold a principal already. */
    public AppUserPrincipal actor() {
        return SecurityUtils.requirePrincipal();
    }
}
