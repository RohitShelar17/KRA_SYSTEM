package com.company.kra.modules.report.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.dto.PageResponse;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.common.enums.ReportFormat;
import com.company.kra.common.enums.ReportRequestStatus;
import com.company.kra.common.events.NotificationRaised;
import com.company.kra.common.report.ReportTable;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.report.definition.ReportDefinition;
import com.company.kra.modules.report.dto.ReportRequestResponse;
import com.company.kra.modules.report.entity.ReportRequest;
import com.company.kra.modules.report.render.ReportRenderer;
import com.company.kra.modules.report.repository.ReportRequestRepository;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Turns a report into a file.
 *
 * <p>Small exports are rendered inline so the file is ready by the time the
 * response returns. Anything larger is queued: the request comes back
 * immediately, the file is written on a background thread and the person is
 * notified when it is there. Either way one {@code report_requests} row records
 * who exported what, with which filters, and how many rows left the system.</p>
 */
@Slf4j
@Service
public class ReportExportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ReportService reportService;
    private final ReportRequestRepository requestRepository;
    private final List<ReportRenderer> renderers;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final int asyncThreshold;
    private final Path exportDirectory;

    public ReportExportService(ReportService reportService,
                               ReportRequestRepository requestRepository,
                               List<ReportRenderer> renderers,
                               AuditService auditService,
                               ObjectMapper objectMapper,
                               ApplicationEventPublisher events,
                               @Value("${app.reports.async-threshold:5000}") int asyncThreshold,
                               @Value("${app.reports.export-dir:exports}") String exportDirectory) {
        this.reportService = reportService;
        this.requestRepository = requestRepository;
        this.renderers = renderers;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.events = events;
        this.asyncThreshold = asyncThreshold;
        this.exportDirectory = Path.of(exportDirectory);
    }

    /**
     * Starts an export. The returned request is READY when the file was small
     * enough to write inline, and QUEUED when it is still being written.
     */
    @Transactional
    public ReportRequestResponse export(String key, ReportFormat format, Map<String, String> filters) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        ReportDefinition definition = reportService.require(key);

        ReportRequest request = new ReportRequest();
        request.setCompanyId(principal.getCompanyId());
        request.setReportKey(definition.key());
        request.setReportTitle(definition.title());
        request.setFormat(format);
        request.setFilters(toJson(filters));
        request.setRequestedBy(principal.getUserId());
        request.setRequestedByName(principal.getDisplayName());
        request.setStatus(ReportRequestStatus.RUNNING);
        requestRepository.save(request);

        ReportTable table = reportService.runFully(key, filters);

        auditService.record(AuditModule.REPORT, AuditAction.EXPORT, "ReportRequest",
                request.getId(), null,
                Map.of("report", definition.key(), "format", format.name(),
                        "rows", String.valueOf(table.size()),
                        "filters", String.valueOf(filters)), null);

        if (table.size() > asyncThreshold) {
            request.setStatus(ReportRequestStatus.QUEUED);
            requestRepository.save(request);
            writeLater(request.getId(), definition.title(),
                    reportService.describe(definition, filters), table, format,
                    principal.getCompanyId(), principal.getEmployeeId());
            return toResponse(request);
        }

        write(request, definition.title(), reportService.describe(definition, filters), table, format);
        return toResponse(request);
    }

    /**
     * Renders on a background thread. The request row is the only thing the
     * caller is holding, so every outcome — including failure — is written
     * back to it.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeLater(Long requestId, String heading, Map<String, String> describedFilters,
                           ReportTable table, ReportFormat format, Long companyId, Long employeeId) {
        ReportRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null) {
            return;
        }
        write(request, heading, describedFilters, table, format);

        if (request.isReady()) {
            events.publishEvent(NotificationRaised.to(companyId, "EXPORT_READY")
                    .recipient(employeeId)
                    .about("ReportRequest", request.getId())
                    .with("reportTitle", request.getReportTitle())
                    .with("format", format.name())
                    .with("rowCount", request.getRowCount())
                    .build());
        }
    }

    private void write(ReportRequest request, String heading, Map<String, String> describedFilters,
                       ReportTable table, ReportFormat format) {
        ReportRenderer renderer = renderers.stream()
                .filter(candidate -> candidate.format() == format)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "No renderer for " + format));

        String fileName = request.getReportKey() + "-"
                + LocalDateTime.now().format(STAMP) + "." + format.extension();
        Path target = exportDirectory.resolve(fileName);

        try {
            Files.createDirectories(exportDirectory);
            try (OutputStream out = Files.newOutputStream(target)) {
                renderer.render(table, heading, describedFilters, out);
            }
            request.setFileName(fileName);
            request.setFilePath(target.toAbsolutePath().toString());
            request.setByteSize(Files.size(target));
            request.setRowCount(table.size());
            request.setStatus(ReportRequestStatus.READY);
            request.setCompletedAt(LocalDateTime.now());
            log.info("Exported {} rows of {} as {}", table.size(), request.getReportKey(), format);
        } catch (IOException | RuntimeException failure) {
            request.setStatus(ReportRequestStatus.FAILED);
            request.setErrorDetail(trim(failure.getMessage()));
            request.setCompletedAt(LocalDateTime.now());
            log.error("Export of {} failed", request.getReportKey(), failure);
        }
        requestRepository.save(request);
    }

    /**
     * One of the caller's own export requests, whatever state it is in.
     *
     * <p>An export can hold rows from a scope the caller no longer has, so
     * only the person who ran it may see or download it.</p>
     */
    @Transactional(readOnly = true)
    public ReportRequest requireReadableOrPending(Long id) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        ReportRequest request = requestRepository.findByIdAndCompanyId(id, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Export", id));

        if (!request.getRequestedBy().equals(principal.getUserId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "This export belongs to somebody else. Run your own copy of the report.");
        }
        return request;
    }

    /** The file, for the person who asked for it. */
    @Transactional(readOnly = true)
    public ReportRequest requireReadable(Long id) {
        ReportRequest request = requireReadableOrPending(id);
        if (!request.isReady()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    request.getStatus() == ReportRequestStatus.FAILED
                            ? "That export failed: " + request.getErrorDetail()
                            : "That export is still being written. Try again in a moment.");
        }
        return request;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportRequestResponse> mine(int page, int size) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        return PageResponse.from(requestRepository.findByRequestedByOrderByCreatedAtDesc(
                principal.getUserId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))), this::toResponse);
    }

    public ReportRequestResponse toResponse(ReportRequest request) {
        return new ReportRequestResponse(request.getId(), request.getReportKey(),
                request.getReportTitle(), request.getFormat(), request.getStatus(),
                request.getRowCount(), request.getFileName(), request.getByteSize(),
                request.getErrorDetail(), request.getRequestedByName(), request.getCreatedAt(),
                request.getCompletedAt(),
                request.isReady() ? "/api/v1/reports/exports/" + request.getId() + "/download" : null);
    }

    private String toJson(Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(filters);
        } catch (JsonProcessingException failure) {
            // The filters are a record of what was asked for, not the export
            // itself; losing them must not lose the export.
            log.warn("Could not store the filter set for an export", failure);
            return null;
        }
    }

    private String trim(String message) {
        String text = message == null ? "Unknown failure" : message;
        return text.length() > 500 ? text.substring(0, 497) + "…" : text;
    }
}
