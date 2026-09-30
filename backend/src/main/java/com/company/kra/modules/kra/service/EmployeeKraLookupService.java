package com.company.kra.modules.kra.service;

import com.company.kra.common.enums.KraStatus;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.kra.entity.EmployeeKra;
import com.company.kra.modules.kra.repository.EmployeeKraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class EmployeeKraLookupService implements KraLookup {

    private final EmployeeKraRepository kraRepository;

    @Override
    @Transactional(readOnly = true)
    public KraRef requireAssigned(Long employeeKraId, Long employeeId, LocalDate onDate) {
        EmployeeKra kra = kraRepository.findById(employeeKraId)
                .orElseThrow(() -> new ResourceNotFoundException("KRA", employeeKraId));

        if (!kra.getEmployee().getId().equals(employeeId)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "You can only attribute work to your own KRAs");
        }
        if (!kra.isActive() || kra.getStatus() == KraStatus.CLOSED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "That KRA is closed");
        }
        if (onDate.isBefore(kra.getPeriodStart()) || onDate.isAfter(kra.getPeriodEnd())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "\"" + kra.getName() + "\" covers " + kra.periodLabel()
                            + ", which does not include " + onDate);
        }
        return new KraRef(kra.getId(), kra.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public String nameOf(Long employeeKraId) {
        if (employeeKraId == null) {
            return null;
        }
        return kraRepository.findById(employeeKraId).map(EmployeeKra::getName).orElse(null);
    }
}
