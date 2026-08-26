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

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AssignmentDetailResponseDto modifierDetail(UUID detailId,
                                                      CorrectionDetailDto correction,
                                                      UUID branchId) {
        TestOrderAssignmentDetail detail = detailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("Détail d'assignment", detailId));
        // Le cloisonnement par branche vaut ici comme ailleurs : on corrige les
        // lots de son site, pas ceux du voisin.
        if (detail.getBranchId() != null && !detail.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Détail d'assignment", detailId);
        }

        // Les étiquettes sont remplacées, pas fusionnées : corriger veut dire
        // « voici ce qui est vrai maintenant ». Ajouter « Immuno payé » sans
        // retirer « Immuno non payé » laisserait les deux sur le contenant.
        detail.setLabels(encoderEtiquettes(correction.labels()));
        enrichirLeCatalogue(detail.getBranchId(), correction.labels());

        // La note ne se vide que si l'appelant l'envoie vide. Un nul veut dire
        // « je ne touche pas à la note » — sans quoi corriger une étiquette
        // effacerait une consigne qu'on n'avait pas l'intention de retirer.
        if (correction.note() != null) {
            detail.setNote(correction.note().isBlank() ? null : correction.note());
        }

        detailRepository.save(detail);
        log.info("Étiquettes corrigées : detailId={} demande={}", detailId, detail.getTestOrderCode());
        return new AssignmentDetailResponseDto(
                detail.getId(),
                detail.getTestOrder() != null ? detail.getTestOrder().getId() : null,
                detail.getTestOrderCode(),
                decoderEtiquettes(detail.getLabels()),
                detail.getNote());
    }

    @Override
    @Transactional
    public void deleteDetail(UUID detailId) {
        TestOrderAssignmentDetail detail = detailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("Détail d'assignment", detailId));
        detailRepository.delete(detail);
    }

    /**
     * Le prochain code d'affectation : {@code AF{aa}-{seq4}}.
     *
     * <p>Le préfixe était {@code ASS}, hérité de la réplication du backend
     * Laravel. C'était une erreur de transcription : les 250 affectations
     * antérieures portent {@code AF}, depuis {@code AF23-0001}. Seules les
     * quatre créées par ce backend s'en écartaient.</p>
     *
     * <p>La séquence se lit dans les codes de l'année en cours et non dans le
     * nombre total d'affectations de la branche : ce compteur-là ne repartait
     * jamais à zéro — la première de 2027 serait sortie en {@code AF27-0513} —
     * et redescendait à chaque suppression, rejouant un numéro déjà attribué.
     * La garde qui suit refuse en dernier ressort un code déjà pris.</p>
     */
    /** Le marquage du laboratoire, tel qu'il figure sur les bordereaux. */
    private static final String PREFIXE_AFFECTATION = "AF";

    private String generateCode(UUID branchId) {
        String anneeDuCode = String.valueOf(LocalDate.now().getYear() % 100);
        int suivant = assignmentRepository
                .findMaxSequenceForYear(branchId, anneeDuCode) + 1;
        String code = PREFIXE_AFFECTATION + anneeDuCode
                + String.format("-%04d", suivant);
        int garde = 0;
        while (assignmentRepository.existsByCodeIncludingDeleted(code) && garde < 10_000) {
            suivant++;
            garde++;
            code = PREFIXE_AFFECTATION + anneeDuCode + String.format("-%04d", suivant);
        }
        return code;
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
    @Transactional(readOnly = true)
    public List<DemandeDuMedecinDto> fileDuMedecin(UUID docteurId) {
        // Les demandes terminées restent visibles le jour même. La borne porte
        // sur la date du lot, seule date que la ligne connaisse : c'est une
        // approximation, mais elle va dans le bon sens — un lot du jour reste
        // affiché, un lot d'hier disparaît.
        return detailRepository.fileDuMedecin(docteurId, LocalDate.now()).stream()
                .map(this::versDemandeDuMedecin)
                .toList();
    }

    private DemandeDuMedecinDto versDemandeDuMedecin(TestOrderAssignmentDetail d) {
        var lot = d.getTestOrderAssignment();
        var demande = d.getTestOrder();
        var patient = demande == null ? null : demande.getPatient();
        return new DemandeDuMedecinDto(
                d.getId(),
                demande == null ? null : demande.getId(),
                d.getTestOrderCode(),
                patient == null ? null
                        : com.labo.anapath.common.NomComplet.de(
                                patient.getLastname(), patient.getFirstname()),
                d.statutDuMedecin().valeur(),
                demande == null || demande.getStatus() == null
                        ? null : demande.getStatus().name(),
                decoderEtiquettes(d.getLabels()),
                d.getNote(),
                lot == null ? null : lot.getCode(),
                lot == null ? null : lot.getDate(),
                lot == null ? null : lot.getNote());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public DemandeDuMedecinDto changerStatutDuMedecin(UUID detailId, String statut, UUID branchId) {
        TestOrderAssignmentDetail detail = detailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("Détail d'assignment", detailId));
        // Le cloisonnement par branche vaut ici comme ailleurs.
        if (detail.getBranchId() != null && !detail.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Détail d'assignment", detailId);
        }
        // Une valeur inconnue serait silencieusement ramenée à « à traiter » par
        // `DocteurStatus.depuis` — tolérable en lecture, jamais en écriture :
        // l'appelant croirait avoir posé un statut qu'il n'a pas posé.
        DocteurStatus voulu = DocteurStatus.depuis(statut);
        if (!voulu.valeur().equalsIgnoreCase(statut == null ? "" : statut.trim())) {
            throw new com.labo.anapath.common.exception.BusinessException(
                    "Statut inconnu : « " + statut + " ». Attendu : a_traiter, "
                            + "pris_en_charge ou termine.");
        }
        detail.setDocteurStatus(voulu.valeur());
        detailRepository.save(detail);
        log.info("Statut médecin changé : detailId={} statut={}", detailId, voulu.valeur());
        return versDemandeDuMedecin(detail);
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
    public List<EtiquetteDto> catalogue(UUID branchId) {
        return labelRepository
                .findByBranchIdAndDeletedAtIsNullOrderByValueAsc(branchId).stream()
                .map(e -> new EtiquetteDto(e.getId(), e.getValue(),
                        labelRepository.compterUsages(branchId, e.getValue())))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public EtiquetteDto renommer(UUID branchId, UUID id, String valeur) {
        String propre = valeur == null ? "" : valeur.trim();
        if (propre.isEmpty() || propre.length() > 40) {
            throw new com.labo.anapath.common.exception.BusinessException(
                    "Une étiquette tient en 1 à 40 caractères.");
        }
        SampleLabel etiquette = etiquetteDeLaBranche(branchId, id);
        labelRepository.chercher(branchId, propre)
                .filter(autre -> !autre.getId().equals(id))
                .ifPresent(autre -> {
                    throw new com.labo.anapath.common.exception.BusinessException(
                            "« " + propre + " » existe déjà dans le catalogue.");
                });
        etiquette.setValue(propre);
        labelRepository.save(etiquette);
        return new EtiquetteDto(etiquette.getId(), propre,
                labelRepository.compterUsages(branchId, propre));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void retirer(UUID branchId, UUID id) {
        SampleLabel etiquette = etiquetteDeLaBranche(branchId, id);
        etiquette.setDeletedAt(java.time.LocalDateTime.now());
        labelRepository.save(etiquette);
    }

    /**
     * L'étiquette, à condition qu'elle appartienne à la branche qui la demande.
     *
     * <p>Sans cette vérification, un identifiant deviné suffirait à renommer le
     * vocabulaire d'un autre site : la permission autorise à administrer son
     * catalogue, pas celui du voisin.</p>
     */
    private SampleLabel etiquetteDeLaBranche(UUID branchId, UUID id) {
        return labelRepository.findById(id)
                .filter(e -> e.getDeletedAt() == null)
                .filter(e -> e.getBranchId() != null
                        && e.getBranchId().equals(branchId))
                .orElseThrow(() -> new ResourceNotFoundException("SampleLabel", id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<String> etiquettesConnues(UUID branchId) {
        return labelRepository
                .findByBranchIdAndDeletedAtIsNullOrderByValueAsc(branchId).stream()
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
        return Etiquettes.encoder(objectMapper, etiquettes);
    }

    /**
     * Relit les étiquettes. Un contenu illisible rend une liste vide plutôt que
     * de faire échouer la lecture de toute l'affectation.
     */
    @SuppressWarnings("unchecked")
    private List<String> decoderEtiquettes(String brut) {
        return Etiquettes.decoder(objectMapper, brut);
    }
}
