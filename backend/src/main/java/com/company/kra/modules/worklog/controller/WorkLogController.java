package com.company.kra.modules.worklog.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.common.dto.PageResponse;
import com.company.kra.common.enums.WorkLogDayStatus;
import com.company.kra.modules.worklog.dto.CategoryBreakdownResponse;
import com.company.kra.modules.worklog.dto.MissingSubmissionResponse;
import com.company.kra.modules.worklog.dto.ReviewDecisionRequest;
import com.company.kra.modules.worklog.dto.UnlockRequest;
import com.company.kra.modules.worklog.dto.UnlockResponse;
import com.company.kra.modules.worklog.dto.SubmitDayRequest;
import com.company.kra.modules.worklog.dto.WorkLogDayResponse;
import com.company.kra.modules.worklog.dto.WorkLogDaySummaryResponse;
import com.company.kra.modules.worklog.dto.WorkLogEntryRequest;
import com.company.kra.modules.worklog.service.WorkLogService;
import com.company.kra.modules.worklog.service.WorkLogUnlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/worklogs")
@RequiredArgsConstructor
@Tag(name = "Work logs", description = "Daily timesheet, submission and review")
public class WorkLogController {

    private static final int MAX_PAGE_SIZE = 200;

    private final WorkLogService workLogService;
    private final WorkLogUnlockService unlockService;

    @GetMapping("/today")
    @PreAuthorize("hasAuthority('WORKLOG_WRITE_SELF')")
    @Operation(summary = "Today's timeline and totals for the caller")
    public ResponseEntity<ApiResponse<WorkLogDayResponse>> today() {
        return ResponseEntity.ok(ApiResponse.of(workLogService.getDay(null, LocalDate.now())));
    }

    @GetMapping("/day")
    @Operation(summary = "One day for the caller, or for someone within their access scope")
    public ResponseEntity<ApiResponse<WorkLogDayResponse>> day(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long employeeId) {
        return ResponseEntity.ok(ApiResponse.of(workLogService.getDay(employeeId, date)));
    }

    @GetMapping("/range")
    @Operation(summary = "Day summaries between two dates")
    public ResponseEntity<ApiResponse<List<WorkLogDaySummaryResponse>>> range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long employeeId) {
        return ResponseEntity.ok(ApiResponse.of(workLogService.range(employeeId, from, to)));
    }

    @GetMapping("/breakdown")
    @Operation(summary = "Minutes by activity category over a period")
    public ResponseEntity<ApiResponse<List<CategoryBreakdownResponse>>> breakdown(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long employeeId) {
        return ResponseEntity.ok(ApiResponse.of(workLogService.categoryBreakdown(employeeId, from, to)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('WORKLOG_WRITE_SELF')")
    @Operation(summary = "Add a work entry; overlaps and the daily cap are enforced")
    public ResponseEntity<ApiResponse<WorkLogDayResponse>> addEntry(
            @Valid @RequestBody WorkLogEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(workLogService.addEntry(request), "Entry added"));
    }

    @PutMapping("/{entryId}")
    @PreAuthorize("hasAuthority('WORKLOG_WRITE_SELF')")
    public ResponseEntity<ApiResponse<WorkLogDayResponse>> updateEntry(
            @PathVariable Long entryId, @Valid @RequestBody WorkLogEntryRequest request) {
        return ResponseEntity.ok(ApiResponse.of(workLogService.updateEntry(entryId, request),
                "Entry updated"));
    }

    @DeleteMapping("/{entryId}")
    @PreAuthorize("hasAuthority('WORKLOG_WRITE_SELF')")
    public ResponseEntity<ApiResponse<WorkLogDayResponse>> deleteEntry(@PathVariable Long entryId) {
        return ResponseEntity.ok(ApiResponse.of(workLogService.deleteEntry(entryId), "Entry removed"));
    }

    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('WORKLOG_WRITE_SELF')")
    @Operation(summary = "Submit a day for review; it becomes read-only")
    public ResponseEntity<ApiResponse<WorkLogDayResponse>> submit(
            @Valid @RequestBody SubmitDayRequest request) {
        return ResponseEntity.ok(ApiResponse.of(workLogService.submit(request), "Work log submitted"));
    }

    @GetMapping("/pending-approvals")
    @PreAuthorize("hasAuthority('WORKLOG_APPROVE')")
    @Operation(summary = "Days waiting for the caller to review")
    public ResponseEntity<ApiResponse<PageResponse<WorkLogDaySummaryResponse>>> pendingApprovals(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) WorkLogDayStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        WorkLogDayStatus effective = status == null ? WorkLogDayStatus.SUBMITTED : status;
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "workDate"));
        return ResponseEntity.ok(ApiResponse.of(
                workLogService.search(employeeId, effective, from, to, pageable)));
    }

    @GetMapping("/pending-count")
    @PreAuthorize("hasAuthority('WORKLOG_APPROVE')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> pendingCount() {
        return ResponseEntity.ok(ApiResponse.of(Map.of("pending", workLogService.pendingReviewCount())));
    }

    @PostMapping("/{dayId}/review")
    @PreAuthorize("hasAuthority('WORKLOG_APPROVE')")
    @Operation(summary = "Approve, reject, return for changes, or reopen an approved day")
    public ResponseEntity<ApiResponse<WorkLogDayResponse>> review(
            @PathVariable Long dayId, @Valid @RequestBody ReviewDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.of(workLogService.review(dayId, request), "Decision recorded"));
    }

    @GetMapping("/missing")
    @PreAuthorize("hasAuthority('WORKLOG_READ_TEAM')")
    @Operation(summary = "People inside the caller's scope who have not filed a given date")
    public ResponseEntity<ApiResponse<List<MissingSubmissionResponse>>> missing(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.of(workLogService.missingSubmissions(date)));
    }

    @PostMapping("/unlocks")
    @PreAuthorize("hasAuthority('WORKLOG_APPROVE')")
    @Operation(summary = "Reopen one past date for one employee, outside the back-dating window")
    public ResponseEntity<ApiResponse<UnlockResponse>> unlock(@Valid @RequestBody UnlockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(unlockService.grant(request), "Date reopened"));
    }

    @GetMapping("/unlocks")
    @PreAuthorize("hasAuthority('WORKLOG_APPROVE')")
    @Operation(summary = "Back-dating exceptions inside the caller's scope")
    public ResponseEntity<ApiResponse<List<UnlockResponse>>> unlocks(
            @RequestParam(defaultValue = "true") boolean liveOnly) {
        return ResponseEntity.ok(ApiResponse.of(unlockService.list(liveOnly)));
    }

    @DeleteMapping("/unlocks/{id}")
    @PreAuthorize("hasAuthority('WORKLOG_APPROVE')")
    @Operation(summary = "Close a reopened date again before it expires")
    public ResponseEntity<ApiResponse<Void>> closeUnlock(@PathVariable Long id) {
        unlockService.revoke(id);
        return ResponseEntity.ok(ApiResponse.message("Date closed again"));
    }
}
