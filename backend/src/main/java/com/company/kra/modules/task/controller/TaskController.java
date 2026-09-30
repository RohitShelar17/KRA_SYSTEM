package com.company.kra.modules.task.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.common.dto.PageResponse;
import com.company.kra.common.enums.Priority;
import com.company.kra.common.enums.TaskStatus;
import com.company.kra.modules.task.dto.TaskBoardCountsResponse;
import com.company.kra.modules.task.dto.TaskRequest;
import com.company.kra.modules.task.dto.TaskResponse;
import com.company.kra.modules.task.dto.TaskStatusRequest;
import com.company.kra.modules.task.service.TaskService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Assigned and self-created work")
public class TaskController {

    private static final int MAX_PAGE_SIZE = 200;

    private final TaskService taskService;

    @GetMapping("/my")
    @PreAuthorize("hasAuthority('TASK_READ_SELF')")
    @Operation(summary = "The caller's own tasks")
    public ResponseEntity<ApiResponse<PageResponse<TaskResponse>>> mine(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "false") boolean openOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.of(taskService.mine(status, openOnly, pageable(page, size))));
    }

    @GetMapping("/team")
    @PreAuthorize("hasAuthority('TASK_READ_TEAM')")
    @Operation(summary = "Tasks across the caller's access scope")
    public ResponseEntity<ApiResponse<PageResponse<TaskResponse>>> team(
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(defaultValue = "false") boolean openOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueBefore,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.of(taskService.search(assigneeId, projectId, categoryId,
                status, priority, openOnly, dueBefore, query, pageable(page, size))));
    }

    @GetMapping("/counts")
    @PreAuthorize("hasAuthority('TASK_READ_SELF')")
    @Operation(summary = "Status counts behind the task tabs")
    public ResponseEntity<ApiResponse<TaskBoardCountsResponse>> counts(
            @RequestParam(required = false) Long employeeId) {
        return ResponseEntity.ok(ApiResponse.of(taskService.counts(employeeId)));
    }

    @GetMapping("/open")
    @PreAuthorize("hasAuthority('WORKLOG_WRITE_SELF')")
    @Operation(summary = "Open tasks the caller can attribute time to")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> open() {
        return ResponseEntity.ok(ApiResponse.of(taskService.openForCaller()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TASK_READ_SELF','TASK_READ_TEAM')")
    public ResponseEntity<ApiResponse<TaskResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(taskService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('TASK_READ_SELF','TASK_ASSIGN')")
    @Operation(summary = "Create a task for yourself, or assign one to a report")
    public ResponseEntity<ApiResponse<TaskResponse>> create(@Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(taskService.create(request), "Task created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TASK_READ_SELF','TASK_ASSIGN')")
    public ResponseEntity<ApiResponse<TaskResponse>> update(@PathVariable Long id,
                                                            @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(ApiResponse.of(taskService.update(id, request), "Task updated"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('TASK_READ_SELF','TASK_ASSIGN')")
    @Operation(summary = "Change status and completion percentage")
    public ResponseEntity<ApiResponse<TaskResponse>> changeStatus(
            @PathVariable Long id, @Valid @RequestBody TaskStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.of(taskService.changeStatus(id, request), "Task updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TASK_READ_SELF','TASK_ASSIGN')")
    @Operation(summary = "Cancel a task; work logged against it is kept")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Task cancelled"));
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "dueDate").and(Sort.by(Sort.Direction.DESC, "priority")));
    }
}
