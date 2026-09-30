package com.company.kra.modules.worklog.repository;

import com.company.kra.modules.worklog.entity.DailyWorkLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DailyWorkLogRepository extends JpaRepository<DailyWorkLog, Long> {

    @EntityGraph(attributePaths = {"taskCategory", "project", "task"})
    List<DailyWorkLog> findByWorkLogDayIdOrderByStartTimeAsc(Long workLogDayId);

    @EntityGraph(attributePaths = {"taskCategory", "project", "task"})
    Optional<DailyWorkLog> findByIdAndCompanyId(Long id, Long companyId);

    @EntityGraph(attributePaths = {"taskCategory"})
    List<DailyWorkLog> findByEmployeeIdAndWorkDateOrderByStartTimeAsc(Long employeeId, LocalDate workDate);

    /**
     * Existing slots for the overlap check. Excluding the row being edited keeps
     * an update from colliding with itself.
     */
    @Query("""
            SELECT w FROM DailyWorkLog w
            WHERE w.employee.id = :employeeId
              AND w.workDate = :workDate
              AND (:excludeId IS NULL OR w.id <> :excludeId)
            ORDER BY w.startTime ASC
            """)
    List<DailyWorkLog> findSlotsForDay(@Param("employeeId") Long employeeId,
                                       @Param("workDate") LocalDate workDate,
                                       @Param("excludeId") Long excludeId);

    @Query("""
            SELECT COALESCE(SUM(w.durationMinutes), 0) FROM DailyWorkLog w
            WHERE w.employee.id = :employeeId AND w.workDate BETWEEN :from AND :to
            """)
    int sumMinutesBetween(@Param("employeeId") Long employeeId,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to);

    /** Category split for the day-distribution chart. */
    @Query("""
            SELECT w.taskCategory.name, w.taskCategory.colourHex, SUM(w.durationMinutes)
            FROM DailyWorkLog w
            WHERE w.employee.id = :employeeId AND w.workDate BETWEEN :from AND :to
            GROUP BY w.taskCategory.name, w.taskCategory.colourHex
            ORDER BY SUM(w.durationMinutes) DESC
            """)
    List<Object[]> categoryBreakdown(@Param("employeeId") Long employeeId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    long countByTaskId(Long taskId);

    void deleteByWorkLogDayId(Long workLogDayId);

    // ------------------------------------------------------------- reporting

    /** Every entry in the window: the raw record behind every rolled-up total. */
    @Query("""
            SELECT l.workDate, e.employeeCode, CONCAT(e.firstName, ' ', e.lastName), dep.name,
                   l.startTime, l.endTime, l.durationMinutes, cat.name, l.title,
                   l.status, l.completionPct, l.productive
            FROM DailyWorkLog l JOIN l.employee e LEFT JOIN e.department dep
                 LEFT JOIN l.taskCategory cat
            WHERE l.companyId = :companyId
              AND (:scopeAll = TRUE OR e.id IN :scopeIds)
              AND l.workDate BETWEEN :from AND :to
              AND (:employeeId IS NULL OR e.id = :employeeId)
              AND (:departmentId IS NULL OR dep.id = :departmentId)
            ORDER BY l.workDate DESC, l.startTime ASC
            """)
    List<Object[]> reportEntries(@Param("companyId") Long companyId,
                                 @Param("scopeAll") boolean scopeAll,
                                 @Param("scopeIds") Set<Long> scopeIds,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to,
                                 @Param("employeeId") Long employeeId,
                                 @Param("departmentId") Long departmentId);
}
