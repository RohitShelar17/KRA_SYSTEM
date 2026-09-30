package com.company.kra.modules.identity.repository;

import com.company.kra.modules.identity.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByModuleAscCodeAsc();

    List<Permission> findByCodeIn(List<String> codes);
}
