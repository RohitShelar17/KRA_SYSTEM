package com.company.kra.modules.employee.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import com.company.kra.common.enums.EmployeeStatus;
import com.company.kra.common.enums.EmploymentType;
import com.company.kra.common.enums.WorkMode;
import com.company.kra.modules.identity.entity.User;
import com.company.kra.modules.organisation.entity.Department;
import com.company.kra.modules.organisation.entity.Designation;
import com.company.kra.modules.organisation.entity.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "employees")
public class Employee extends CompanyScopedEntity {

    @Column(name = "employee_code", nullable = false, length = 30)
    private String employeeCode;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "designation_id", nullable = false)
    private Designation designation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporting_manager_id")
    private Employee reportingManager;

    @Column(name = "joining_date", nullable = false)
    private LocalDate joiningDate;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Column(name = "work_location", length = 120)
    private String workLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", nullable = false, length = 10)
    private WorkMode workMode = WorkMode.OFFICE;

    @Column(name = "shift_start_time")
    private LocalTime shiftStartTime;

    @Column(name = "shift_end_time")
    private LocalTime shiftEndTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Column(name = "profile_image_url", length = 400)
    private String profileImageUrl;

    public String fullName() {
        return firstName + " " + lastName;
    }
}
