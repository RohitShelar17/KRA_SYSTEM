package com.company.kra.modules.employee.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.common.dto.OptionDto;
import com.company.kra.common.dto.PageResponse;
import com.company.kra.common.enums.EmployeeStatus;
import com.company.kra.modules.employee.dto.EmployeeRequest;
import com.company.kra.modules.employee.dto.EmployeeResponse;
import com.company.kra.modules.employee.dto.InviteResponse;
import com.company.kra.modules.employee.dto.EmployeeSummaryResponse;
import com.company.kra.modules.employee.dto.EmployeeUpdateRequest;
import com.company.kra.modules.employee.dto.ManagerChangeRequest;
import com.company.kra.modules.employee.dto.ReportingLineResponse;
import com.company.kra.modules.employee.service.AccountInviteService;
import com.company.kra.modules.employee.service.EmployeeService;
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
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
@Tag(name = "Employees", description = "Employee directory and reporting hierarchy")
public class EmployeeController {

    private static final int MAX_PAGE_SIZE = 200;

    private final EmployeeService employeeService;
    private final AccountInviteService inviteService;

    @GetMapping
    @PreAuthorize("hasAuthority('EMPLOYEE_READ')")
    @Operation(summary = "Search the employee directory within the caller's access scope")
    public ResponseEntity<ApiResponse<PageResponse<EmployeeSummaryResponse>>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long designationId,
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "firstName") String sort,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(size, MAX_PAGE_SIZE),
                Sort.by(sortDirection, sort));

        return ResponseEntity.ok(ApiResponse.of(employeeService.search(query, departmentId, teamId,
                designationId, managerId, status, activeOnly, pageable)));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('EMPLOYEE_READ')")
    @Operation(summary = "Employee options for manager and head pickers")
    public ResponseEntity<ApiResponse<List<OptionDto>>> options() {
        return ResponseEntity.ok(ApiResponse.of(employeeService.options()));
    }

    @GetMapping("/me")
    @Operation(summary = "The caller's own employee record")
    public ResponseEntity<ApiResponse<EmployeeResponse>> me() {
        return ResponseEntity.ok(ApiResponse.of(employeeService.me()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_READ')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(employeeService.get(id)));
    }

    @GetMapping("/{id}/team")
    @PreAuthorize("hasAuthority('EMPLOYEE_READ')")
    @Operation(summary = "Direct reports of an employee")
    public ResponseEntity<ApiResponse<List<EmployeeSummaryResponse>>> team(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(employeeService.directReports(id)));
    }

    @GetMapping("/{id}/reporting-history")
    @PreAuthorize("hasAuthority('EMPLOYEE_READ')")
    @Operation(summary = "Effective-dated reporting lines for an employee")
    public ResponseEntity<ApiResponse<List<ReportingLineResponse>>> reportingHistory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(employeeService.reportingHistory(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    @Operation(summary = "Create an employee and their sign-in account")
    public ResponseEntity<ApiResponse<EmployeeResponse>> create(@Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(employeeService.create(request), "Employee created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> update(@PathVariable Long id,
                                                                @Valid @RequestBody EmployeeUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(employeeService.update(id, request), "Employee updated"));
    }

    @PostMapping("/{id}/invite")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    @Operation(summary = "Re-issue the sign-in invite, returning a single-use link to pass on")
    public ResponseEntity<ApiResponse<InviteResponse>> invite(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(inviteService.resend(id),
                "Invite issued. The link works once and expires."));
    }

    @PutMapping("/{id}/manager")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    @Operation(summary = "Reassign the reporting manager from an effective date")
    public ResponseEntity<ApiResponse<EmployeeResponse>> changeManager(
            @PathVariable Long id, @Valid @RequestBody ManagerChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.of(employeeService.changeManager(id, request),
                "Reporting manager updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    @Operation(summary = "Deactivate an employee and disable their account")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id,
                                                        @RequestParam(required = false) String reason) {
        employeeService.deactivate(id, reason);
        return ResponseEntity.ok(ApiResponse.message("Employee deactivated"));
    }
}
