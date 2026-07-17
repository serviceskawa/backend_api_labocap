package com.labo.anapath.inventory;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupplierCategoryRepository extends JpaRepository<SupplierCategory, UUID> {
    List<SupplierCategory> findByBranchId(UUID branchId);
    List<SupplierCategory> findByBranchId(UUID branchId, Sort sort);
}
