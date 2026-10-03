package com.labo.anapath.support;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.testorder.TestOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProblemReportServiceImpl implements ProblemReportService {

    private final ProblemReportRepository problemReportRepository;
    private final ProblemCategoryRepository problemCategoryRepository;
    private final TestOrderRepository testOrderRepository;
    private final ProblemReportMapper problemReportMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProblemReportResponseDto> findAll(int page, int size, UUID branchId) {
        return PageResponse.of(problemReportRepository.findByBranchId(branchId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(problemReportMapper::toResponseDto));
    }

    @Override
    @Transactional(readOnly = true)
    public ProblemReportResponseDto findById(UUID id) {
        return problemReportMapper.toResponseDto(problemReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Signalement de problème", id)));
    }

    @Override
    @Transactional
    public ProblemReportResponseDto create(ProblemReportRequestDto dto, UUID branchId) {
        ProblemReport report = new ProblemReport();
        report.setBranchId(branchId);
        report.setDescription(dto.getDescription());
        report.setTestOrder(testOrderRepository.findByIdAndBranchId(dto.getTestOrderId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande d'examen", dto.getTestOrderId())));
        if (dto.getProblemCategoryId() != null) {
            report.setProblemCategory(problemCategoryRepository.findById(dto.getProblemCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Catégorie de problème", dto.getProblemCategoryId())));
        }
        return problemReportMapper.toResponseDto(problemReportRepository.save(report));
    }

    @Override
    @Transactional
    public ProblemReportResponseDto updateStatus(UUID id, String status) {
        ProblemReport report = problemReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Signalement de problème", id));
        report.setStatus(status);
        return problemReportMapper.toResponseDto(problemReportRepository.save(report));
    }
}
