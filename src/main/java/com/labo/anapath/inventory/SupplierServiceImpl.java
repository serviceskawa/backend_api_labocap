package com.labo.anapath.inventory;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierCategoryRepository supplierCategoryRepository;
    private final InventoryMapper inventoryMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SupplierResponseDto> findAll(int page, int size, UUID branchId) {
        // Laravel liste les fournisseurs via `latest()` : du plus récent au plus ancien.
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PageResponse.of(supplierRepository.findByBranchId(branchId, pageable)
                .map(inventoryMapper::toSupplierResponseDto));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierResponseDto> search(String q, UUID branchId) {
        return supplierRepository.findByBranchIdAndNameContainingIgnoreCase(branchId, q)
                .stream().map(inventoryMapper::toSupplierResponseDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierResponseDto findById(UUID id) {
        return inventoryMapper.toSupplierResponseDto(
                supplierRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Fournisseur", id)));
    }

    @Override
    @Transactional
    public SupplierResponseDto create(SupplierRequestDto dto, UUID branchId) {
        Supplier supplier = new Supplier();
        supplier.setBranchId(branchId);
        applyDto(dto, supplier);
        return inventoryMapper.toSupplierResponseDto(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public SupplierResponseDto update(UUID id, SupplierRequestDto dto) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fournisseur", id));
        applyDto(dto, supplier);
        return inventoryMapper.toSupplierResponseDto(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fournisseur", id));
        supplierRepository.delete(supplier);
    }

    private void applyDto(SupplierRequestDto dto, Supplier supplier) {
        supplier.setName(dto.getName());
        supplier.setPhone(dto.getPhone());
        supplier.setEmail(dto.getEmail());
        supplier.setAddress(dto.getAddress());
        supplier.setInformation(dto.getInformation());
        supplier.setCategory(dto.getCategory());
        if (dto.getCategoryId() != null) {
            supplier.setSupplierCategory(supplierCategoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Catégorie fournisseur", dto.getCategoryId())));
        } else {
            supplier.setSupplierCategory(null);
        }
    }
}
