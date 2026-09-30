package com.company.kra.modules.organisation.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.common.dto.OptionDto;
import com.company.kra.common.dto.PageResponse;
import com.company.kra.modules.organisation.dto.DepartmentRequest;
import com.company.kra.modules.organisation.dto.DepartmentResponse;
import com.company.kra.modules.organisation.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@Tag(name = "Departments", description = "Department structure")
public class DepartmentController {

    private static final int MAX_PAGE_SIZE = 200;

    private final DepartmentService departmentService;

    @GetMapping
    @PreAuthorize("hasAuthority('DEPARTMENT_READ')")
    @Operation(summary = "Search departments")
    public ResponseEntity<ApiResponse<PageResponse<DepartmentResponse>>> search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "name") String sort) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(size, MAX_PAGE_SIZE),
                Sort.by(sort).ascending());
        return ResponseEntity.ok(ApiResponse.of(departmentService.search(query, activeOnly, pageable)));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('DEPARTMENT_READ')")
    @Operation(summary = "Department options for dropdowns")
    public ResponseEntity<ApiResponse<List<OptionDto>>> options() {
        return ResponseEntity.ok(ApiResponse.of(departmentService.options()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DEPARTMENT_READ')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(departmentService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
    @Operation(summary = "Create a department")
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(departmentService.create(request), "Department created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> update(@PathVariable Long id,
                                                                  @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(ApiResponse.of(departmentService.update(id, request), "Department updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DEPARTMENT_MANAGE')")
    @Operation(summary = "Deactivate a department; history keeps resolving")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        departmentService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.message("Department deactivated"));
    }
}
