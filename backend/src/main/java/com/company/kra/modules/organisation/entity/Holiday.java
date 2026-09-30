package com.company.kra.modules.organisation.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import com.company.kra.common.enums.HolidayType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "holidays")
public class Holiday extends CompanyScopedEntity {

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "holiday_type", nullable = false, length = 20)
    private HolidayType holidayType = HolidayType.PUBLIC;
}
