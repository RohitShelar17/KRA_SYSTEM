package com.company.kra.modules.organisation.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.modules.organisation.dto.CompanySettingsRequest;
import com.company.kra.modules.organisation.dto.CompanySettingsResponse;
import com.company.kra.modules.organisation.dto.HolidayRequest;
import com.company.kra.modules.organisation.dto.HolidayResponse;
import com.company.kra.modules.organisation.service.CompanySettingsService;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Working hours, policies and the holiday calendar")
public class SettingsController {

    private final CompanySettingsService settingsService;

    @GetMapping("/company")
    @Operation(summary = "Working-hour policy; every authenticated user may read it")
    public ResponseEntity<ApiResponse<CompanySettingsResponse>> company() {
        return ResponseEntity.ok(ApiResponse.of(settingsService.get()));
    }

    @PutMapping("/company")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<ApiResponse<CompanySettingsResponse>> update(
            @Valid @RequestBody CompanySettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.of(settingsService.update(request), "Settings saved"));
    }

    @GetMapping("/holidays")
    @Operation(summary = "Holiday calendar for a year")
    public ResponseEntity<ApiResponse<List<HolidayResponse>>> holidays(
            @RequestParam(required = false) Integer year) {
        int target = year == null ? LocalDate.now().getYear() : year;
        return ResponseEntity.ok(ApiResponse.of(settingsService.holidays(target)));
    }

    @PostMapping("/holidays")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<ApiResponse<HolidayResponse>> addHoliday(@Valid @RequestBody HolidayRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(settingsService.addHoliday(request), "Holiday added"));
    }

    @DeleteMapping("/holidays/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> removeHoliday(@PathVariable Long id) {
        settingsService.removeHoliday(id);
        return ResponseEntity.ok(ApiResponse.message("Holiday removed"));
    }
}
