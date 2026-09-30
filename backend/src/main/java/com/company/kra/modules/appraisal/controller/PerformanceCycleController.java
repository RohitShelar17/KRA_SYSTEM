package com.company.kra.modules.appraisal.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.modules.appraisal.dto.CycleLaunchResponse;
import com.company.kra.modules.appraisal.dto.CycleRequest;
import com.company.kra.modules.appraisal.dto.CycleResponse;
import com.company.kra.modules.appraisal.dto.WorkflowStepRequest;
import com.company.kra.modules.appraisal.dto.WorkflowStepResponse;
import com.company.kra.modules.appraisal.service.PerformanceCycleService;
import com.company.kra.modules.appraisal.service.WorkflowConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Appraisal cycles", description = "Review rounds and the workflow they follow")
public class PerformanceCycleController {

    private final PerformanceCycleService cycleService;
    private final WorkflowConfigService workflowConfigService;

    @GetMapping("/performance-cycles")
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE','PERFORMANCE_REVIEW','SELF_ASSESSMENT_SUBMIT')")
    public ResponseEntity<ApiResponse<List<CycleResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.of(cycleService.list()));
    }

    @GetMapping("/performance-cycles/active")
    @Operation(summary = "Cycles currently accepting self assessments or reviews")
    public ResponseEntity<ApiResponse<List<CycleResponse>>> active() {
        return ResponseEntity.ok(ApiResponse.of(cycleService.active()));
    }

    @GetMapping("/performance-cycles/{id}")
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE','PERFORMANCE_REVIEW','SELF_ASSESSMENT_SUBMIT')")
    public ResponseEntity<ApiResponse<CycleResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(cycleService.get(id)));
    }

    @PostMapping("/performance-cycles")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<ApiResponse<CycleResponse>> create(@Valid @RequestBody CycleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(cycleService.create(request), "Cycle created"));
    }

    @PutMapping("/performance-cycles/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<ApiResponse<CycleResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CycleRequest request) {
        return ResponseEntity.ok(ApiResponse.of(cycleService.update(id, request), "Cycle updated"));
    }

    @PostMapping("/performance-cycles/{id}/open")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Open the cycle and enrol everyone; safe to run again for late joiners")
    public ResponseEntity<ApiResponse<CycleLaunchResponse>> open(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(cycleService.open(id), "Cycle opened"));
    }

    @PostMapping("/performance-cycles/{id}/close")
    @PreAuthorize("hasAuthority('PERFORMANCE_FINALIZE')")
    @Operation(summary = "Close the cycle; refused while appraisals are still in progress")
    public ResponseEntity<ApiResponse<CycleResponse>> close(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(cycleService.close(id), "Cycle closed"));
    }

    @GetMapping("/review-workflow")
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE','PERFORMANCE_REVIEW','SELF_ASSESSMENT_SUBMIT')")
    @Operation(summary = "The configured review steps, in order")
    public ResponseEntity<ApiResponse<List<WorkflowStepResponse>>> workflow() {
        return ResponseEntity.ok(ApiResponse.of(workflowConfigService.list()));
    }

    @PutMapping("/review-workflow")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Replace the workflow; the order sent is the order steps run in")
    public ResponseEntity<ApiResponse<List<WorkflowStepResponse>>> saveWorkflow(
            @Valid @RequestBody List<WorkflowStepRequest> requests) {
        return ResponseEntity.ok(ApiResponse.of(workflowConfigService.replaceAll(requests),
                "Workflow saved"));
    }
}
