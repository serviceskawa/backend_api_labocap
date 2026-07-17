package com.labo.anapath.inventory;

import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierCategoryServiceImpl implements SupplierCategoryService {

    private final SupplierCategoryRepository supplierCategoryRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryMapper inventoryMapper;

    @Override
    @Transactional(readOnly = true)
    public List<SupplierCategoryResponseDto> findAll(UUID branchId) {
        // Laravel liste les catégories via `latest()` : du plus récent au plus ancien.
        return supplierCategoryRepository
                .findByBranchId(branchId, Sort.by("createdAt").descending())
                .stream().map(inventoryMapper::toSupplierCategoryResponseDto).toList();
    }

    @Override
    @Transactional
    public SupplierCategoryResponseDto create(SupplierCategoryRequestDto dto, UUID branchId) {
        SupplierCategory category = new SupplierCategory();
        category.setBranchId(branchId);
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        return inventoryMapper.toSupplierCategoryResponseDto(supplierCategoryRepository.save(category));
    }

    @Override
    @Transactional
    public SupplierCategoryResponseDto update(UUID id, SupplierCategoryRequestDto dto) {
        SupplierCategory category = supplierCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie fournisseur", id));
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        return inventoryMapper.toSupplierCategoryResponseDto(supplierCategoryRepository.save(category));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        SupplierCategory category = supplierCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie fournisseur", id));
        if (supplierRepository.existsBySupplierCategory(category)) {
            throw new InvalidOperationException("Impossible de supprimer une catégorie liée à des fournisseurs");
        }
        supplierCategoryRepository.delete(category);
    }
}
