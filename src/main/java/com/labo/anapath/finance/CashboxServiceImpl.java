package com.labo.anapath.finance;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CashboxServiceImpl implements CashboxService {

    private final CashboxRepository cashboxRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CashboxResponseDto> findAll(int page, int size, UUID branchId) {
        return PageResponse.of(cashboxRepository.findByBranchId(branchId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toDto));
    }

    @Override
    @Transactional(readOnly = true)
    public CashboxResponseDto findById(UUID id, UUID branchId) {
        Cashbox cashbox = cashboxRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Caisse", id));
        if (!cashbox.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Caisse", id);
        }
        return toDto(cashbox);
    }

    @Override
    @Transactional
    public CashboxResponseDto create(CashboxRequestDto dto, UUID branchId) {
        Cashbox cashbox = new Cashbox();
        cashbox.setBranchId(branchId);
        cashbox.setName(dto.getName());
        cashbox.setType(dto.getType());
        return toDto(cashboxRepository.save(cashbox));
    }

    @Override
    @Transactional
    public CashboxResponseDto update(UUID id, CashboxRequestDto dto) {
        Cashbox cashbox = cashboxRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Caisse", id));
        cashbox.setName(dto.getName());
        cashbox.setType(dto.getType());
        return toDto(cashboxRepository.save(cashbox));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Cashbox cashbox = cashboxRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Caisse", id));
        cashboxRepository.delete(cashbox);
    }

    private CashboxResponseDto toDto(Cashbox c) {
        return new CashboxResponseDto(c.getId(), c.getName(), c.getType(),
                c.getBalance(), c.getStatut(), c.getBranchId(), c.getCreatedAt());
    }
}
