package com.company.kra.modules.report.entity;

import com.company.kra.common.entity.BaseEntity;
import com.company.kra.common.enums.ReportFormat;
import com.company.kra.common.enums.ReportRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * One export: who ran what, with which filters, and how many rows left the
 * system.
 *
 * <p>The row is written before the file is, so an export that fails halfway is
 * still visible as a FAILED attempt rather than disappearing.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "report_requests")
public class ReportRequest extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "report_key", nullable = false, length = 60)
    private String reportKey;

    @Column(name = "report_title", nullable = false, length = 150)
    private String reportTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportFormat format;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String filters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportRequestStatus status = ReportRequestStatus.QUEUED;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "file_name", length = 200)
    private String fileName;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "byte_size")
    private Long byteSize;

    @Column(name = "error_detail", length = 500)
    private String errorDetail;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "requested_by_name", length = 150)
    private String requestedByName;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public boolean isReady() {
        return status == ReportRequestStatus.READY;
    }
}
