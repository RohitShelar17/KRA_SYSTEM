package com.company.kra.modules.report.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.common.dto.PageResponse;
import com.company.kra.modules.report.dto.ReportDefinitionResponse;
import com.company.kra.modules.report.dto.ReportExportRequest;
import com.company.kra.modules.report.dto.ReportRequestResponse;
import com.company.kra.modules.report.dto.ReportResultResponse;
import com.company.kra.modules.report.dto.ReportRunRequest;
import com.company.kra.modules.report.entity.ReportRequest;
import com.company.kra.modules.report.service.ReportExportService;
import com.company.kra.modules.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * One controller for fifteen reports.
 *
 * <p>Reports are data, not endpoints: the catalogue lists what the caller may
 * run, and the same two paths run and export any of them. A sixteenth report
 * needs no change here.</p>
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "The report catalogue, runs and exports")
public class ReportController {

    private final ReportService reportService;
    private final ReportExportService exportService;

    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Operation(summary = "The reports this caller may run")
    public ResponseEntity<ApiResponse<List<ReportDefinitionResponse>>> catalogue() {
        return ResponseEntity.ok(ApiResponse.of(reportService.catalogue()));
    }

    @PostMapping("/{key}/run")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @Operation(summary = "Run a report and read a page of the result")
    public ResponseEntity<ApiResponse<ReportResultResponse>> run(@PathVariable String key,
                                                                 @RequestBody(required = false)
                                                                 ReportRunRequest request) {
        Map<String, String> filters = request == null ? Map.of() : request.filters();
        int page = request == null || request.page() == null ? 0 : request.page();
        int size = request == null || request.size() == null ? 25 : request.size();
        return ResponseEntity.ok(ApiResponse.of(reportService.run(key, filters, page, size)));
    }

    @PostMapping("/{key}/export")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    @Operation(summary = "Export a report; large results are queued and notified when ready")
    public ResponseEntity<ApiResponse<ReportRequestResponse>> export(
            @PathVariable String key, @Valid @RequestBody ReportExportRequest request) {
        ReportRequestResponse response = exportService.export(key, request.format(),
                request.filters());
        return ResponseEntity.ok(ApiResponse.of(response,
                response.status().name().equals("READY")
                        ? "Export ready" : "Export queued; you will be notified when it is ready"));
    }

    @GetMapping("/exports")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    @Operation(summary = "The caller's own exports")
    public ResponseEntity<ApiResponse<PageResponse<ReportRequestResponse>>> exports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(exportService.mine(page, size)));
    }

    @GetMapping("/exports/{id}")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    @Operation(summary = "One export's status")
    public ResponseEntity<ApiResponse<ReportRequestResponse>> exportStatus(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(
                exportService.toResponse(exportService.requireReadableOrPending(id))));
    }

    @GetMapping("/exports/{id}/download")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    @Operation(summary = "Download a finished export")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        ReportRequest request = exportService.requireReadable(id);
        Resource file = new FileSystemResource(Path.of(request.getFilePath()));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(request.getFormat().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + request.getFileName() + "\"")
                .body(file);
    }
}
