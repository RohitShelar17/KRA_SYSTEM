package com.company.kra.modules.appraisal.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.common.dto.PageResponse;
import com.company.kra.common.enums.AppraisalStep;
import com.company.kra.modules.appraisal.dto.AdvanceStepRequest;
import com.company.kra.modules.appraisal.dto.AppraisalDetailResponse;
import com.company.kra.modules.appraisal.dto.AppraisalSummaryResponse;
import com.company.kra.modules.appraisal.dto.ManagerReviewRequest;
import com.company.kra.modules.appraisal.dto.ManagerReviewResponse;
import com.company.kra.modules.appraisal.dto.ReturnStepRequest;
import com.company.kra.modules.appraisal.dto.SelfAssessmentRequest;
import com.company.kra.modules.appraisal.dto.SelfAssessmentResponse;
import com.company.kra.modules.appraisal.service.AppraisalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/appraisals")
@RequiredArgsConstructor
@Tag(name = "Appraisals", description = "Self assessment, evaluation and the review workflow")
public class AppraisalController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AppraisalService appraisalService;

    @GetMapping("/my")
    @PreAuthorize("hasAuthority('SELF_ASSESSMENT_SUBMIT')")
    @Operation(summary = "The caller's own appraisals across cycles")
    public ResponseEntity<ApiResponse<List<AppraisalSummaryResponse>>> mine() {
        return ResponseEntity.ok(ApiResponse.of(appraisalService.myAppraisals()));
    }

    @GetMapping("/my/cycle/{cycleId}")
    @PreAuthorize("hasAuthority('SELF_ASSESSMENT_SUBMIT')")
    public ResponseEntity<ApiResponse<AppraisalDetailResponse>> myForCycle(@PathVariable Long cycleId) {
        return ResponseEntity.ok(ApiResponse.of(appraisalService.myAppraisalForCycle(cycleId)));
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAuthority('PERFORMANCE_REVIEW')")
    @Operation(summary = "Appraisals waiting on the caller, within their access scope")
    public ResponseEntity<ApiResponse<PageResponse<AppraisalSummaryResponse>>> queue(
            @RequestParam(required = false) Long cycleId,
            @RequestParam(required = false) AppraisalStep step,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.ASC, "id"));
        return ResponseEntity.ok(ApiResponse.of(appraisalService.queue(cycleId, step, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SELF_ASSESSMENT_SUBMIT','PERFORMANCE_REVIEW')")
    @Operation(summary = "One appraisal with its workflow, inputs and score")
    public ResponseEntity<ApiResponse<AppraisalDetailResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(appraisalService.detail(id)));
    }

    @PutMapping("/{id}/self-assessment")
    @PreAuthorize("hasAuthority('SELF_ASSESSMENT_SUBMIT')")
    @Operation(summary = "Save the self assessment as a draft")
    public ResponseEntity<ApiResponse<SelfAssessmentResponse>> saveSelfAssessment(
            @PathVariable Long id, @Valid @RequestBody SelfAssessmentRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                appraisalService.saveSelfAssessment(id, request, false), "Draft saved"));
    }

    @PostMapping("/{id}/self-assessment/submit")
    @PreAuthorize("hasAuthority('SELF_ASSESSMENT_SUBMIT')")
    @Operation(summary = "Submit the self assessment; it becomes read-only and the flow advances")
    public ResponseEntity<ApiResponse<SelfAssessmentResponse>> submitSelfAssessment(
            @PathVariable Long id, @Valid @RequestBody SelfAssessmentRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                appraisalService.saveSelfAssessment(id, request, true), "Self assessment submitted"));
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasAuthority('PERFORMANCE_REVIEW')")
    @Operation(summary = "Save the evaluation as a draft")
    public ResponseEntity<ApiResponse<ManagerReviewResponse>> saveReview(
            @PathVariable Long id, @Valid @RequestBody ManagerReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                appraisalService.saveReview(id, request, false), "Draft saved"));
    }

    @PostMapping("/{id}/review/submit")
    @PreAuthorize("hasAuthority('PERFORMANCE_REVIEW')")
    @Operation(summary = "Submit the evaluation; its ratings start counting towards the score")
    public ResponseEntity<ApiResponse<ManagerReviewResponse>> submitReview(
            @PathVariable Long id, @Valid @RequestBody ManagerReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                appraisalService.saveReview(id, request, true), "Review submitted"));
    }

    @PostMapping("/{id}/advance")
    @PreAuthorize("hasAuthority('PERFORMANCE_REVIEW')")
    @Operation(summary = "Move to the next step, or skip an optional one")
    public ResponseEntity<ApiResponse<AppraisalDetailResponse>> advance(
            @PathVariable Long id, @Valid @RequestBody AdvanceStepRequest request) {
        return ResponseEntity.ok(ApiResponse.of(appraisalService.advance(id, request), "Step advanced"));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAuthority('PERFORMANCE_REVIEW')")
    @Operation(summary = "Send the appraisal back a step so the earlier work can be redone")
    public ResponseEntity<ApiResponse<AppraisalDetailResponse>> returnStep(
            @PathVariable Long id, @Valid @RequestBody ReturnStepRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                appraisalService.returnStep(id, request.comments()), "Sent back a step"));
    }
}
