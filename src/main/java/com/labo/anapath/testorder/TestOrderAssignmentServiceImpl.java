package com.labo.anapath.testorder;

import com.labo.anapath.common.NomComplet;

import com.labo.anapath.branch.Branch;
import com.labo.anapath.branch.BranchRepository;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.report.TestPathologyMacro;
import com.labo.anapath.report.TestPathologyMacroRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestOrderAssignmentServiceImpl implements TestOrderAssignmentService {

    private final TestOrderAssignmentRepository assignmentRepository;
    private final TestOrderAssignmentDetailRepository detailRepository;
    private final TestOrderRepository testOrderRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final TestPathologyMacroRepository macroRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final SampleLabelRepository labelRepository;

    @Override
    @Transactional
    public AssignmentResponseDto create(AssignmentRequestDto dto, UUID branchId) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", dto.getUserId()));
        TestOrderAssignment assignment = new TestOrderAssignment();
        assignment.setBranchId(branchId);
        assignment.setUser(user);
        assignment.setNote(dto.getNote());
        assignment.setDate(dto.getDate() != null ? dto.getDate() : LocalDate.now());
        assignment.setCode(generateCode(branchId));
        TestOrderAssignment saved = assignmentRepository.save(assignment);
        log.info("Assignment créé: id={}, code={}", saved.getId(), saved.getCode());
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AssignmentResponseDto> findAll(int page, int size, UUID branchId) {
        Page<TestOrderAssignment> p = assignmentRepository.findHistoCyto(branchId, PageRequest.of(page, size));
        return PageResponse.of(p.map(this::toDto));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AssignmentResponseDto> findAllImmuno(int page, int size, UUID branchId) {
        Page<TestOrderAssignment> p = assignmentRepository.findImmuno(branchId, PageRequest.of(page, size));
        return PageResponse.of(p.map(this::toDto));
    }

    @Override
    @Transactional
    public AssignmentDetailResponseDto addDetail(UUID assignmentId, AssignmentDetailRequestDto dto) {
        TestOrderAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));
        TestOrder order = testOrderRepository.findById(dto.getTestOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", dto.getTestOrderId()));

        boolean alreadyAssigned = detailRepository.existsByTestOrderId(dto.getTestOrderId());

        TestOrderAssignmentDetail detail;
        if (!alreadyAssigned) {
            detail = new TestOrderAssignmentDetail();
            detail.setBranchId(assignment.getBranchId());
            detail.setTestOrderAssignment(assignment);
            detail.setTestOrder(order);
            detail.setTestOrderCode(order.getCode());
            detail.setNote(dto.getNote());
            detail.setLabels(encoderEtiquettes(dto.getLabels()));
            enrichirLeCatalogue(assignment.getBranchId(), dto.getLabels());
            detailRepository.save(detail);
        } else {
            detail = detailRepository.findByTestOrderId(dto.getTestOrderId())
                    .orElseGet(TestOrderAssignmentDetail::new);
            // Une demande déjà affectée voyait ses étiquettes ignorées : ni
            // enregistrées sur la ligne, ni versées au catalogue. Or c'est
            // précisément en la réaffectant qu'on précise quels prélèvements
            // partent cette fois-ci.
            if (dto.getLabels() != null && !dto.getLabels().isEmpty()) {
                detail.setLabels(encoderEtiquettes(dto.getLabels()));
                enrichirLeCatalogue(detail.getBranchId(), dto.getLabels());
                detailRepository.save(detail);
            }
        }

        Optional<TestPathologyMacro> existingMacro = macroRepository.findByTestOrderId(order.getId());
        if (existingMacro.isPresent()) {
            existingMacro.get().setAllStepsTrue();
            macroRepository.save(existingMacro.get());
        } else {
            TestPathologyMacro macro = new TestPathologyMacro();
            macro.setBranchId(assignment.getBranchId());
            macro.setTestOrderId(order.getId());
            macro.setTitle("Macro - " + order.getCode());
            macro.setMacroDate(dto.getDate() != null ? dto.getDate() : LocalDate.now());
            macro.setAllStepsTrue();
            macroRepository.save(macro);
        }

        return new AssignmentDetailResponseDto(detail.getId(), order.getId(), order.getCode(),
                decoderEtiquettes(detail.getLabels()), detail.getNote());
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentPrintDto getPrintData(UUID assignmentId) {
        TestOrderAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));
        Branch branch = branchRepository.findById(assignment.getBranchId()).orElse(null);
        List<AssignmentDetailResponseDto> details = assignment.getDetails().stream()
                .map(d -> new AssignmentDetailResponseDto(
                        d.getId(),
                        d.getTestOrder() != null ? d.getTestOrder().getId() : null,
                        d.getTestOrderCode(),
                        decoderEtiquettes(d.getLabels()),
                        d.getNote()))
                .toList();
        return new AssignmentPrintDto(
                toDto(assignment),
                details,
                branch != null ? branch.getName() : null,
                branch != null ? branch.getLocation() : null);
    }

    @Override
    @Transactional
    public AssignmentResponseDto update(UUID id, AssignmentRequestDto dto) {
        TestOrderAssignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", id));
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", dto.getUserId()));
        assignment.setUser(user);
        assignment.setNote(dto.getNote());
        if (dto.getDate() != null) assignment.setDate(dto.getDate());
        return toDto(assignmentRepository.save(assignment));
    }

    @Override
    @Transactional
    public void deleteDetail(UUID detailId) {
        TestOrderAssignmentDetail detail = detailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("Détail d'assignment", detailId));
        detailRepository.delete(detail);
    }

    private String generateCode(UUID branchId) {
        int year = LocalDate.now().getYear() % 100;
        long count = assignmentRepository.countByBranchId(branchId) + 1;
        return String.format("ASS%02d-%04d", year, count);
    }

    private AssignmentResponseDto toDto(TestOrderAssignment a) {
        String userName = a.getUser() != null
                ? NomComplet.de(a.getUser().getLastname(), a.getUser().getFirstname())
                : null;
        java.util.List<String> detailCodes = a.getDetails().stream()
                .map(TestOrderAssignmentDetail::getTestOrderCode)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new AssignmentResponseDto(
                a.getId(), a.getCode(),
                a.getUser() != null ? a.getUser().getId() : null,
                userName, a.getDate(), a.getNote(),
                a.getDetails().size(), detailCodes,
                a.getBranchId(), a.getCreatedAt());
    }

    /**
     * Fait entrer au catalogue les étiquettes qu'on vient d'employer.
     *
     * <p>Le vocabulaire du laboratoire se constitue ainsi par l'usage, plutôt
     * que d'être décidé d'avance : chaque site marque ses contenants à sa
     * façon, et une liste figée dans le code n'aurait convenu à personne.</p>
     *
     * <p>La casse est ignorée à la comparaison — « L1 » et « l1 » désignent le
     * même contenant — mais la première graphie employée est celle qui reste,
     * puisque c'est elle que le laboratoire écrit sur ses étiquettes.</p>
     */
    private void enrichirLeCatalogue(UUID branchId, List<String> etiquettes) {
        if (branchId == null || etiquettes == null) return;
        for (String brute : etiquettes) {
            if (brute == null) continue;
            String valeur = brute.trim();
            if (valeur.isEmpty() || valeur.length() > 40) continue;
            if (labelRepository.chercher(branchId, valeur).isPresent()) continue;
            labelRepository.save(new SampleLabel(branchId, valeur));
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public List<String> ajouterAuCatalogue(UUID branchId, String valeur) {
        enrichirLeCatalogue(branchId, List.of(valeur == null ? "" : valeur));
        return etiquettesConnues(branchId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<String> etiquettesConnues(UUID branchId) {
        return labelRepository.findByBranchIdOrderByValueAsc(branchId).stream()
                .map(SampleLabel::getValue)
                .toList();
    }

    /**
     * Sérialise les étiquettes, ou rien du tout.
     *
     * <p>Une liste vide est enregistrée comme nulle plutôt que comme « [] » :
     * les deux se lisent pareil, et un nul distingue à l'œil, en base, une
     * affectation sans étiquette d'une affectation antérieure à leur
     * existence.</p>
     */
    private String encoderEtiquettes(List<String> etiquettes) {
        if (etiquettes == null || etiquettes.isEmpty()) return null;
        List<String> propres = etiquettes.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .distinct()
                .toList();
        if (propres.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(propres);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new com.labo.anapath.common.exception.BusinessException(
                    "Les étiquettes n'ont pas pu être enregistrées.");
        }
    }

    /**
     * Relit les étiquettes. Un contenu illisible rend une liste vide plutôt que
     * de faire échouer la lecture de toute l'affectation.
     */
    @SuppressWarnings("unchecked")
    private List<String> decoderEtiquettes(String brut) {
        if (brut == null || brut.isBlank()) return List.of();
        try {
            return objectMapper.readValue(brut, List.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return List.of();
        }
    }
}
