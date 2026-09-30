package com.company.kra.modules.organisation.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.common.dto.OptionDto;
import com.company.kra.modules.organisation.dto.TeamRequest;
import com.company.kra.modules.organisation.dto.TeamResponse;
import com.company.kra.modules.organisation.service.TeamService;
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
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
@Tag(name = "Teams", description = "Teams within a department")
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    @PreAuthorize("hasAuthority('DEPARTMENT_READ')")
    @Operation(summary = "List teams, optionally by department")
    public ResponseEntity<ApiResponse<List<TeamResponse>>> list(@RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(ApiResponse.of(teamService.list(departmentId)));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('DEPARTMENT_READ')")
    public ResponseEntity<ApiResponse<List<OptionDto>>> options(@RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(ApiResponse.of(teamService.options(departmentId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DEPARTMENT_READ')")
    public ResponseEntity<ApiResponse<TeamResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(teamService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    public ResponseEntity<ApiResponse<TeamResponse>> create(@Valid @RequestBody TeamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(teamService.create(request), "Team created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    public ResponseEntity<ApiResponse<TeamResponse>> update(@PathVariable Long id,
                                                            @Valid @RequestBody TeamRequest request) {
        return ResponseEntity.ok(ApiResponse.of(teamService.update(id, request), "Team updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        teamService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.message("Team deactivated"));
    }
}
