package com.company.kra.audit;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Read-only change trail")
public class AuditController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "Search the audit trail by module, action, entity, actor or date")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> search(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(size, MAX_PAGE_SIZE));
        var result = auditLogRepository.search(module, action, entityType, entityId, actorUserId,
                from, to, pageable);
        return ResponseEntity.ok(ApiResponse.of(PageResponse.from(result, AuditLogResponse::from)));
    }

    public record AuditLogResponse(Long id, String actorName, Long actorUserId, String action, String module,
                                   String entityType, Long entityId, String oldValue, String newValue,
                                   String reason, String ipAddress, LocalDateTime createdAt) {

        static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(log.getId(), log.getActorName(), log.getActorUserId(), log.getAction(),
                    log.getModule(), log.getEntityType(), log.getEntityId(), log.getOldValue(),
                    log.getNewValue(), log.getReason(), log.getIpAddress(), log.getCreatedAt());
        }
    }
}
