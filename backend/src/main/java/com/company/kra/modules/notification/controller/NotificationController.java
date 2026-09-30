package com.company.kra.modules.notification.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.common.dto.PageResponse;
import com.company.kra.modules.notification.dto.MarkReadRequest;
import com.company.kra.modules.notification.dto.NotificationResponse;
import com.company.kra.modules.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The notification feed.
 *
 * <p>Every endpoint reads the caller's own feed. There is no path that takes a
 * user id, so one person's notifications cannot be read — or marked read — by
 * another, whatever permissions they hold.</p>
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "The caller's own notification feed")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    @Operation(summary = "The caller's notifications, newest first")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> feed(
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(notificationService.feed(unread, page, size)));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    @Operation(summary = "How many unread notifications the caller has")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.of(Map.of("unread", notificationService.unreadCount())));
    }

    @PostMapping("/read")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    @Operation(summary = "Mark the named notifications read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markRead(
            @Valid @RequestBody MarkReadRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                Map.of("updated", notificationService.markRead(request.ids()))));
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    @Operation(summary = "Mark every unread notification read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead() {
        return ResponseEntity.ok(ApiResponse.of(Map.of("updated", notificationService.markAllRead())));
    }
}
