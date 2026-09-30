package com.company.kra.modules.task.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.common.dto.OptionDto;
import com.company.kra.modules.task.dto.ProjectRequest;
import com.company.kra.modules.task.dto.ProjectResponse;
import com.company.kra.modules.task.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Work attribution: what time is spent on")
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TASK_READ_SELF','TASK_READ_TEAM')")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.of(projectService.list()));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('TASK_READ_SELF','TASK_READ_TEAM','WORKLOG_WRITE_SELF')")
    @Operation(summary = "Active projects for the work log and task pickers")
    public ResponseEntity<ApiResponse<List<OptionDto>>> options() {
        return ResponseEntity.ok(ApiResponse.of(projectService.options()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TASK_READ_SELF','TASK_READ_TEAM')")
    public ResponseEntity<ApiResponse<ProjectResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(projectService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TASK_ASSIGN')")
    public ResponseEntity<ApiResponse<ProjectResponse>> create(@Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(projectService.create(request), "Project created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TASK_ASSIGN')")
    public ResponseEntity<ApiResponse<ProjectResponse>> update(@PathVariable Long id,
                                                               @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.ok(ApiResponse.of(projectService.update(id, request), "Project updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TASK_ASSIGN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        projectService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.message("Project deactivated"));
    }
}
