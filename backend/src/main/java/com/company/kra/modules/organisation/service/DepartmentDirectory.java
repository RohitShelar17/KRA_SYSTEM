package com.company.kra.modules.organisation.service;

import java.util.Optional;

/**
 * The narrow contract other modules may ask the organisation module for.
 *
 * <p>The appraisal module needs to reach a department head to tell them a
 * review is waiting; it asks here rather than reading the departments table
 * itself.</p>
 */
public interface DepartmentDirectory {

    /** The head of a department, when one is appointed. */
    Optional<Long> headOf(Long departmentId);

    String nameOf(Long departmentId);
}
