package com.company.kra.modules.identity.repository;

import com.company.kra.modules.identity.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCodeAndCompanyId(String code, Long companyId);

    List<Role> findByCompanyIdAndActiveTrueOrderByNameAsc(Long companyId);

    List<Role> findByCodeInAndCompanyId(List<String> codes, Long companyId);
}
