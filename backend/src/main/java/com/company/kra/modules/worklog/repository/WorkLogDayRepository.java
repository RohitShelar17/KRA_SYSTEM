package com.company.kra.modules.worklog.repository;

import com.company.kra.common.enums.WorkLogDayStatus;
import com.company.kra.modules.worklog.entity.WorkLogDay;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface WorkLogDayRepository extends JpaRepository<WorkLogDay, Long> {

    @EntityGraph(attributePaths = {"employee", "reviewedBy"})
    Optional<WorkLogDay> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    @EntityGraph(attributePaths = {"employee", "reviewedBy"})
    Optional<WorkLogDay> findByIdAndCompanyId(Long id, Long companyId);

    @EntityGraph(attributePaths = {"employee"})
    List<WorkLogDay> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDesc(
            Long employeeId, LocalDate from, LocalDate to);

    /** Manager review queue, restricted to the caller's access scope. */
    @EntityGraph(attributePaths = {"employee"})
    @Query("""
            SELECT d FROM WorkLogDay d
            WHERE d.companyId = :companyId
              AND (:scopeAll = TRUE OR d.employee.id IN :scopeIds)
              AND (:employeeId IS NULL OR d.employee.id = :employeeId)
              AND (:status IS NULL OR d.status = :status)
              AND (:from IS NULL OR d.workDate >= :from)
              AND (:to IS NULL OR d.workDate <= :to)
            """)
    Page<WorkLogDay> search(@Param("companyId") Long companyId,
                            @Param("scopeAll") boolean scopeAll,
                            @Param("scopeIds") Set<Long> scopeIds,
                            @Param("employeeId") Long employeeId,
                            @Param("status") WorkLogDayStatus status,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            Pageable pageable);

    @Query("""
            SELECT COUNT(d) FROM WorkLogDay d
            WHERE d.companyId = :companyId
              AND (:scopeAll = TRUE OR d.employee.id IN :scopeIds)
              AND d.status IN (com.company.kra.common.enums.WorkLogDayStatus.SUBMITTED,
                               com.company.kra.common.enums.WorkLogDayStatus.UNDER_REVIEW)
            """)
    long countAwaitingReview(@Param("companyId") Long companyId,
                             @Param("scopeAll") boolean scopeAll,
                             @Param("scopeIds") Set<Long> scopeIds);

    /** Employee ids that already have a day row on a date, used to find who has not filed. */
    @Query("SELECT d.employee.id FROM WorkLogDay d WHERE d.workDate = :date AND d.status <> "
            + "com.company.kra.common.enums.WorkLogDayStatus.DRAFT")
    Set<Long> findEmployeeIdsWithSubmissionOn(@Param("date") LocalDate date);

    /** Minutes logged over a window, for the derived productivity KPI. */
    @Query("""
            SELECT COALESCE(SUM(d.loggedMinutes), 0) FROM WorkLogDay d
            WHERE d.employee.id = :employeeId AND d.workDate BETWEEN :from AND :to
            """)
    long sumLoggedMinutesBetween(@Param("employeeId") Long employeeId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(d.productiveMinutes), 0) FROM WorkLogDay d
            WHERE d.employee.id = :employeeId AND d.workDate BETWEEN :from AND :to
            """)
    long sumProductiveMinutesBetween(@Param("employeeId") Long employeeId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /** Days actually filed in the window, for the submission-rate figure. */
    @Query("""
            SELECT COUNT(d) FROM WorkLogDay d
            WHERE d.employee.id = :employeeId
              AND d.workDate BETWEEN :from AND :to
              AND d.status <> com.company.kra.common.enums.WorkLogDayStatus.DRAFT
            """)
    int countSubmittedBetween(@Param("employeeId") Long employeeId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to);

    List<WorkLogDay> findByWorkDateAndStatusIn(LocalDate workDate, List<WorkLogDayStatus> statuses);

    // ------------------------------------------------------------- reporting

    /** One row per employee per day, for the timesheet report. */
    @Query("""
            SELECT d.workDate, e.employeeCode, CONCAT(e.firstName, ' ', e.lastName),
                   dep.name, d.officeMinutes, d.loggedMinutes, d.productiveMinutes,
                   d.unallocatedMinutes, d.overtimeMinutes, d.entryCount, d.status
            FROM WorkLogDay d JOIN d.employee e LEFT JOIN e.department dep
            WHERE d.companyId = :companyId
              AND (:scopeAll = TRUE OR e.id IN :scopeIds)
              AND d.workDate BETWEEN :from AND :to
              AND (:employeeId IS NULL OR e.id = :employeeId)
              AND (:departmentId IS NULL OR dep.id = :departmentId)
            ORDER BY d.workDate DESC, e.firstName ASC
            """)
    List<Object[]> reportTimesheet(@Param("companyId") Long companyId,
                                   @Param("scopeAll") boolean scopeAll,
                                   @Param("scopeIds") Set<Long> scopeIds,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to,
                                   @Param("employeeId") Long employeeId,
                                   @Param("departmentId") Long departmentId);

    /** The same figures rolled up per department. */
    @Query("""
            SELECT dep.name, COUNT(DISTINCT e.id), COUNT(d.id),
                   COALESCE(SUM(d.loggedMinutes), 0), COALESCE(SUM(d.productiveMinutes), 0),
                   COALESCE(SUM(d.unallocatedMinutes), 0), COALESCE(SUM(d.overtimeMinutes), 0)
            FROM WorkLogDay d JOIN d.employee e LEFT JOIN e.department dep
            WHERE d.companyId = :companyId
              AND (:scopeAll = TRUE OR e.id IN :scopeIds)
              AND d.workDate BETWEEN :from AND :to
              AND (:departmentId IS NULL OR dep.id = :departmentId)
            GROUP BY dep.name
            ORDER BY dep.name ASC
            """)
    List<Object[]> reportDepartmentTimesheet(@Param("companyId") Long companyId,
                                             @Param("scopeAll") boolean scopeAll,
                                             @Param("scopeIds") Set<Long> scopeIds,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to,
                                             @Param("departmentId") Long departmentId);

    /** Logged against productive minutes per employee, for the productivity report. */
    @Query("""
            SELECT e.employeeCode, CONCAT(e.firstName, ' ', e.lastName), dep.name,
                   COUNT(d.id), COALESCE(SUM(d.loggedMinutes), 0),
                   COALESCE(SUM(d.productiveMinutes), 0), COALESCE(SUM(d.meetingMinutes), 0),
                   COALESCE(SUM(d.breakMinutes), 0)
            FROM WorkLogDay d JOIN d.employee e LEFT JOIN e.department dep
            WHERE d.companyId = :companyId
              AND (:scopeAll = TRUE OR e.id IN :scopeIds)
              AND d.workDate BETWEEN :from AND :to
              AND (:employeeId IS NULL OR e.id = :employeeId)
              AND (:departmentId IS NULL OR dep.id = :departmentId)
            GROUP BY e.employeeCode, e.firstName, e.lastName, dep.name
            ORDER BY e.firstName ASC
            """)
    List<Object[]> reportProductivity(@Param("companyId") Long companyId,
                                      @Param("scopeAll") boolean scopeAll,
                                      @Param("scopeIds") Set<Long> scopeIds,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to,
                                      @Param("employeeId") Long employeeId,
                                      @Param("departmentId") Long departmentId);

    /** Employee id and date of every day already filed in the window. */
    @Query("""
            SELECT e.id, d.workDate FROM WorkLogDay d JOIN d.employee e
            WHERE d.companyId = :companyId
              AND d.workDate BETWEEN :from AND :to
              AND d.status <> com.company.kra.common.enums.WorkLogDayStatus.DRAFT
            """)
    List<Object[]> filedDaysBetween(@Param("companyId") Long companyId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);
}
