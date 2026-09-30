package com.company.kra.modules.employee.repository;

import com.company.kra.common.enums.EmployeeStatus;
import com.company.kra.modules.employee.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @EntityGraph(attributePaths = {"department", "designation", "team", "reportingManager", "user"})
    Optional<Employee> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Employee> findByUserId(Long userId);

    Optional<Employee> findByEmployeeCodeIgnoreCaseAndCompanyId(String employeeCode, Long companyId);

    boolean existsByEmployeeCodeIgnoreCaseAndCompanyId(String employeeCode, Long companyId);

    boolean existsByEmailIgnoreCase(String email);

    List<Employee> findByReportingManagerIdAndActiveTrue(Long managerId);

    long countByDepartmentIdAndActiveTrue(Long departmentId);

    long countByCompanyIdAndActiveTrue(Long companyId);

    long countByCompanyIdAndStatusAndActiveTrue(Long companyId, EmployeeStatus status);

    /**
     * Directory search. The id set is supplied by AccessScopeResolver, so a
     * caller can never widen their own scope through a filter parameter.
     */
    @EntityGraph(attributePaths = {"department", "designation", "team", "reportingManager", "user"})
    @Query("""
            SELECT e FROM Employee e
            WHERE e.companyId = :companyId
              AND (:scopeAll = TRUE OR e.id IN :scopeIds)
              AND (:departmentId IS NULL OR e.department.id = :departmentId)
              AND (:teamId IS NULL OR e.team.id = :teamId)
              AND (:designationId IS NULL OR e.designation.id = :designationId)
              AND (:managerId IS NULL OR e.reportingManager.id = :managerId)
              AND (:status IS NULL OR e.status = :status)
              AND (:activeOnly = FALSE OR e.active = TRUE)
              AND (:search IS NULL
                   OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(e.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Employee> search(@Param("companyId") Long companyId,
                          @Param("scopeAll") boolean scopeAll,
                          @Param("scopeIds") Set<Long> scopeIds,
                          @Param("departmentId") Long departmentId,
                          @Param("teamId") Long teamId,
                          @Param("designationId") Long designationId,
                          @Param("managerId") Long managerId,
                          @Param("status") EmployeeStatus status,
                          @Param("activeOnly") boolean activeOnly,
                          @Param("search") String search,
                          Pageable pageable);

    /** Direct report ids, used to walk the reporting subtree breadth-first. */
    @Query("SELECT e.id FROM Employee e WHERE e.reportingManager.id IN :managerIds AND e.active = TRUE")
    Set<Long> findIdsByManagerIds(@Param("managerIds") Set<Long> managerIds);

    @Query("SELECT e.id FROM Employee e WHERE e.department.id = :departmentId AND e.active = TRUE")
    Set<Long> findIdsByDepartmentId(@Param("departmentId") Long departmentId);

    @Query("SELECT e.id FROM Employee e WHERE e.companyId = :companyId AND e.active = TRUE")
    Set<Long> findIdsByCompanyId(@Param("companyId") Long companyId);

    @EntityGraph(attributePaths = {"department", "designation"})
    List<Employee> findByCompanyIdAndActiveTrueOrderByFirstNameAsc(Long companyId);
}
