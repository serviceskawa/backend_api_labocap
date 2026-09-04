package com.labo.anapath.testorder;

import com.labo.anapath.common.NomComplet;

import com.labo.anapath.branch.Branch;
import com.labo.anapath.branch.BranchRepository;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ReaffectationNonConfirmeeException;
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
import java.time.LocalDateTime;
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
    private final com.labo.anapath.report.ReportRepository reportRepository;

    /**
     * Le seuil au-delà duquel un dossier sans compte rendu devient urgent.
     *
     * <p>Le même que celui de l'alerte par courriel, à dessein : deux
     * définitions de l'urgence finiraient par se contredire, et le médecin
     * arbitrerait entre un écran rouge et un courriel muet.</p>
     */
    @org.springframework.beans.factory.annotation.Value("${app.alerts.report.days:18}")
    private int joursAvantAlerte;

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

        Optional<TestOrderAssignmentDetail> courante =
                detailRepository.findByTestOrderId(dto.getTestOrderId());
        boolean memeLot = courante
                .map(c -> c.getTestOrderAssignment() != null
                        && assignmentId.equals(c.getTestOrderAssignment().getId()))
                .orElse(false);

        TestOrderAssignmentDetail detail;
        if (memeLot) {
            detail = courante.get();
            // Une demande déjà présente dans ce lot voyait ses étiquettes
            // ignorées : ni enregistrées sur la ligne, ni versées au catalogue.
            // Or c'est précisément en la reprenant qu'on précise quels
            // prélèvements partent cette fois-ci.
            if (dto.getLabels() != null && !dto.getLabels().isEmpty()) {
                detail.setLabels(encoderEtiquettes(dto.getLabels()));
                enrichirLeCatalogue(detail.getBranchId(), dto.getLabels());
                detailRepository.save(detail);
            }
        } else {
            if (courante.isPresent()) {
                if (!dto.isConfirmerReaffectation()) {
                    throw new ReaffectationNonConfirmeeException(
                            order.getCode(), medecinDe(courante.get()));
                }
                // La ligne précédente reste, datée. C'est elle qui dira plus
                // tard à qui le dossier avait d'abord été confié — l'effacer
                // rendrait l'historique muet sur tout ce qui précède le
                // médecin actuel.
                TestOrderAssignmentDetail precedente = courante.get();
                precedente.setRemplaceeLe(LocalDateTime.now());
                detailRepository.save(precedente);
                log.info("Demande {} réaffectée : {} → lot {}",
                        order.getCode(), medecinDe(precedente), assignment.getCode());
            }
            detail = new TestOrderAssignmentDetail();
            detail.setBranchId(assignment.getBranchId());
            detail.setTestOrderAssignment(assignment);
            detail.setTestOrder(order);
            detail.setTestOrderCode(order.getCode());
            detail.setNote(dto.getNote());
            detail.setLabels(encoderEtiquettes(dto.getLabels()));
            enrichirLeCatalogue(assignment.getBranchId(), dto.getLabels());
            detailRepository.save(detail);
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
                statutDe(order), decoderEtiquettes(detail.getLabels()), detail.getNote(),
                detail.getRemplaceeLe());
    }

    /** Combien de dossiers de la file précèdent l'année demandée. */
    @Override
    @Transactional(readOnly = true)
    public long arriereDuMedecin(UUID docteurId, int annee) {
        return detailRepository.compterAnterieures(
                docteurId, LocalDate.now(), LocalDate.of(annee, 1, 1).atStartOfDay());
    }

    /** Le médecin d'une ligne d'affectation, nom de famille en tête. */
    private static String medecinDe(TestOrderAssignmentDetail detail) {
        TestOrderAssignment lot = detail.getTestOrderAssignment();
        if (lot == null || lot.getUser() == null) return "un médecin inconnu";
        return NomComplet.de(lot.getUser().getLastname(), lot.getUser().getFirstname());
    }

    /**
     * À qui la demande a été confiée, dans l'ordre.
     *
     * <p>Rend une liste vide plutôt qu'une erreur pour une demande jamais
     * affectée : au comptoir, c'est le cas de toutes celles qui viennent
     * d'arriver.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public HistoriqueAffectationDto historiqueDe(UUID demandeId, UUID branchId) {
        TestOrder demande = testOrderRepository.findByIdAndBranchId(demandeId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", demandeId));
        List<HistoriqueAffectationDto.EtapeAffectationDto> etapes =
                detailRepository.historiqueDe(demandeId).stream()
                        .map(this::versEtape)
                        .toList();
        return new HistoriqueAffectationDto(demandeId, demande.getCode(), etapes);
    }

    private HistoriqueAffectationDto.EtapeAffectationDto versEtape(
            TestOrderAssignmentDetail detail) {
        TestOrderAssignment lot = detail.getTestOrderAssignment();
        User medecin = lot == null ? null : lot.getUser();
        return new HistoriqueAffectationDto.EtapeAffectationDto(
                detail.getId(),
                lot == null ? null : lot.getId(),
                lot == null ? null : lot.getCode(),
                lot == null ? null : lot.getDate(),
                medecin == null ? null : medecin.getId(),
                medecin == null ? null
                        : NomComplet.de(medecin.getLastname(), medecin.getFirstname()),
                // Le nom de qui a composé le lot, quand l'utilisateur existe
                // encore. Un compte supprimé ne doit pas faire disparaître
                // l'étape : c'est le transfert qu'on trace, pas l'agent.
                lot == null || lot.getCreatedBy() == null ? null
                        : userRepository.findById(lot.getCreatedBy())
                                .map(u -> NomComplet.de(u.getLastname(), u.getFirstname()))
                                .orElse(null),
                detail.getCreatedAt(),
                detail.getRemplaceeLe(),
                detail.getDocteurStatus());
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
                        statutDe(d.getTestOrder()),
                        decoderEtiquettes(d.getLabels()),
                        d.getNote(),
                        d.getRemplaceeLe()))
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
                statutDe(detail.getTestOrder()),
                decoderEtiquettes(detail.getLabels()),
                detail.getNote(),
                detail.getRemplaceeLe());
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
    public List<DemandeDuMedecinDto> fileDuMedecin(UUID docteurId, Integer annee) {
        // Les demandes terminées restent visibles le jour même. La borne porte
        // sur la date du lot, seule date que la ligne connaisse : c'est une
        // approximation, mais elle va dans le bon sens — un lot du jour reste
        // affiché, un lot d'hier disparaît.
        //
        // L'année, elle, se filtre ici et non sur le téléphone. Un médecin de
        // production traîne 3 574 dossiers ouverts dont aucun de l'année :
        // trier au retour reviendrait à en faire descendre trois mille cinq
        // cents pour n'en afficher aucun, sur une connexion mobile.
        var lignes = annee == null
                ? detailRepository.fileDuMedecin(docteurId, LocalDate.now())
                : detailRepository.fileDuMedecinPourLannee(
                        docteurId, LocalDate.now(),
                        LocalDate.of(annee, 1, 1).atStartOfDay(),
                        LocalDate.of(annee + 1, 1, 1).atStartOfDay());

        // Les comptes rendus en une seule requête : un par ligne ferait une
        // trentaine d'allers-retours pour un écran d'accueil.
        var idsDemandes = lignes.stream()
                .map(TestOrderAssignmentDetail::getTestOrder)
                .filter(java.util.Objects::nonNull)
                .map(TestOrder::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        java.util.Map<UUID, String> etatsComptesRendus = idsDemandes.isEmpty()
                ? java.util.Map.of()
                : reportRepository.findByTestOrder_IdIn(idsDemandes).stream()
                        .filter(r -> r.getTestOrder() != null && r.getStatus() != null)
                        .collect(java.util.stream.Collectors.toMap(
                                r -> r.getTestOrder().getId(),
                                r -> r.getStatus().name(),
                                (a, b) -> a));

        return lignes.stream()
                .map(d -> versDemandeDuMedecin(d, etatsComptesRendus))
                .toList();
    }

    /**
     * Un dossier en retard, au sens de l'alerte par courriel.
     *
     * <p>Même définition, mot pour mot : créé il y a plus de {@code
     * app.alerts.report.days} jours, et compte rendu ni validé ni remis. Deux
     * définitions de l'urgence finiraient par se contredire, et le médecin
     * arbitrerait entre un écran rouge et un courriel muet.</p>
     */
    /**
     * Le bon a-t-il été marqué urgent, et reste-t-il à remettre ?
     *
     * <p>L'urgence est une décision prise à l'accueil : on veut voir ce cas
     * passer devant. Elle cesse de valoir une fois le résultat remis — elle
     * portait sur le délai, et ce délai est tenu. La laisser vivre après la
     * remise ferait grossir un compteur que plus rien ne peut faire baisser,
     * et qu'on finirait par ne plus regarder.</p>
     */
    private boolean estMarqueUrgent(TestOrder demande) {
        if (demande == null || !Boolean.TRUE.equals(demande.getIsUrgent())) {
            return false;
        }
        return demande.getStatus() != TestOrderStatus.DELIVERED
                && demande.getStatus() != TestOrderStatus.CANCELLED;
    }

    private boolean estEnRetard(TestOrder demande, String etatCompteRendu) {
        if (demande == null || demande.getCreatedAt() == null) return false;
        if ("VALIDATED".equals(etatCompteRendu) || "DELIVERED".equals(etatCompteRendu)) {
            return false;
        }
        return demande.getCreatedAt().toLocalDate()
                .isBefore(LocalDate.now().minusDays(joursAvantAlerte));
    }

    /** L'état d'une demande, ou null si la ligne n'en désigne aucune. */
    private static String statutDe(TestOrder demande) {
        return demande == null || demande.getStatus() == null
                ? null : demande.getStatus().name();
    }

    private DemandeDuMedecinDto versDemandeDuMedecin(
            TestOrderAssignmentDetail d, java.util.Map<UUID, String> etatsComptesRendus) {
        var lot = d.getTestOrderAssignment();
        var demande = d.getTestOrder();
        var patient = demande == null ? null : demande.getPatient();
        String etatCompteRendu = demande == null || demande.getId() == null
                ? null : etatsComptesRendus.get(demande.getId());
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
                etatCompteRendu,
                estEnRetard(demande, etatCompteRendu),
                estMarqueUrgent(demande),
                demande == null || demande.getCreatedAt() == null
                        ? null : demande.getCreatedAt().getYear(),
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
        // Un seul dossier : la lecture ponctuelle du compte rendu ne coûte rien,
        // et la ligne renvoyée doit porter la même urgence que dans la file.
        var demandeTouchee = detail.getTestOrder();
        java.util.Map<UUID, String> etat = demandeTouchee == null || demandeTouchee.getId() == null
                ? java.util.Map.of()
                : reportRepository.findByTestOrderId(demandeTouchee.getId())
                        .filter(r -> r.getStatus() != null)
                        .map(r -> java.util.Map.of(demandeTouchee.getId(), r.getStatus().name()))
                        .orElse(java.util.Map.of());
        return versDemandeDuMedecin(detail, etat);
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
