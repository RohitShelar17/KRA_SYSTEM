package com.company.kra.modules.employee.service;

import java.util.Map;
import java.util.Set;

/**
 * The only thing other modules may ask the employee module for. Keeping the
 * contract this narrow is what stops the organisation module reaching into
 * employee repositories directly.
 */
public interface EmployeeDirectory {

    /** Display names keyed by employee id; unknown ids are simply absent. */
    Map<Long, String> namesOf(Set<Long> employeeIds);

    String nameOf(Long employeeId);

    boolean exists(Long employeeId);

    /**
     * The account behind each employee, for modules that have to reach a
     * person rather than a record. Employees without a user account — and
     * inactive ones — are simply absent.
     */
    Map<Long, Account> accountsOf(Set<Long> employeeIds);

    /** Just enough of an account to address somebody. */
    record Account(Long employeeId, Long userId, String displayName, String email) {
    }
}
