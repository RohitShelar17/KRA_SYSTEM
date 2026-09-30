package com.company.kra.modules.organisation.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.common.dto.OptionDto;
import com.company.kra.modules.organisation.dto.DesignationRequest;
import com.company.kra.modules.organisation.dto.DesignationResponse;
import com.company.kra.modules.organisation.service.DesignationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/designations")
@RequiredArgsConstructor
@Tag(name = "Designations", description = "Job titles and grades")
public class DesignationController {

    private final DesignationService designationService;

    @GetMapping
    @PreAuthorize("hasAuthority('DEPARTMENT_READ')")
    @Operation(summary = "List designations, optionally by department")
    public ResponseEntity<ApiResponse<List<DesignationResponse>>> list(
            @RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(ApiResponse.of(designationService.list(departmentId)));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('DEPARTMENT_READ')")
    public ResponseEntity<ApiResponse<List<OptionDto>>> options(@RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(ApiResponse.of(designationService.options(departmentId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DEPARTMENT_READ')")
    public ResponseEntity<ApiResponse<DesignationResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(designationService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DESIGNATION_MANAGE')")
    public ResponseEntity<ApiResponse<DesignationResponse>> create(@Valid @RequestBody DesignationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(designationService.create(request), "Designation created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('DESIGNATION_MANAGE')")
    public ResponseEntity<ApiResponse<DesignationResponse>> update(@PathVariable Long id,
                                                                   @Valid @RequestBody DesignationRequest request) {
        return ResponseEntity.ok(ApiResponse.of(designationService.update(id, request), "Designation updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DESIGNATION_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        designationService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.message("Designation deactivated"));
    }
}
