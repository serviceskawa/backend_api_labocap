package com.labo.anapath.prestationorder;

import com.labo.anapath.common.branch.BranchContext;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.patient.Patient;
import com.labo.anapath.patient.PatientRepository;
import com.labo.anapath.prestation.Prestation;
import com.labo.anapath.prestation.PrestationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrestationOrderServiceImpl implements PrestationOrderService {

    private final PrestationOrderRepository repository;
    private final PrestationRepository prestationRepository;
    private final PatientRepository patientRepository;
    private final PrestationOrderMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PrestationOrderResponseDto> findAll(int page, int size, UUID branchId) {
        return PageResponse.of(repository.findByBranchId(branchId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(mapper::toResponseDto));
    }

    @Override
    @Transactional(readOnly = true)
    public PrestationOrderResponseDto findById(UUID id) {
        return mapper.toResponseDto(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order prestation", id)));
    }

    @Override
    @Transactional
    public PrestationOrderResponseDto create(PrestationOrderRequestDto dto, UUID branchId) {
        Prestation prestation = prestationRepository.findById(dto.getPrestationId())
                .orElseThrow(() -> new ResourceNotFoundException("Prestation", dto.getPrestationId()));
        Patient patient = patientRepository.findByIdAndBranchId(dto.getPatientId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", dto.getPatientId()));
        PrestationOrder order = new PrestationOrder();
        order.setBranchId(branchId);
        order.setPrestation(prestation);
        order.setPatient(patient);
        // Total toujours copié depuis prestation.price, jamais du body
        order.setTotal(prestation.getPrice());
        order.setStatus("Nouveau");
        return mapper.toResponseDto(repository.save(order));
    }

    @Override
    @Transactional
    public PrestationOrderResponseDto update(UUID id, PrestationOrderRequestDto dto) {
        PrestationOrder order = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order prestation", id));
        Prestation prestation = prestationRepository.findById(dto.getPrestationId())
                .orElseThrow(() -> new ResourceNotFoundException("Prestation", dto.getPrestationId()));
        Patient patient = patientRepository.findByIdAndBranchId(dto.getPatientId(), BranchContext.get())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", dto.getPatientId()));
        order.setPrestation(prestation);
        order.setPatient(patient);
        order.setTotal(prestation.getPrice());
        if (dto.getStatus() != null) {
            order.setStatus(dto.getStatus());
        }
        return mapper.toResponseDto(repository.save(order));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order prestation", id));
        repository.deleteById(id);
    }
}
