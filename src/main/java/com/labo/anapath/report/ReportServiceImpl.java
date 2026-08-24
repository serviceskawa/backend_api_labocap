package com.labo.anapath.report;

import com.labo.anapath.common.NomComplet;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.email.EmailService;
import com.labo.anapath.common.email.NotificationSettings;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.setting.SettingReportTemplate;
import com.labo.anapath.setting.SettingReportTemplateRepository;
import com.labo.anapath.testorder.TestOrderRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import com.labo.anapath.mobile.MobileDevice;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implémentation du service de gestion des comptes-rendus anatomopathologiques.
 *
 * <p>Gère le cycle de vie complet des CRs : création, consultation, mise à jour,
 * validation, livraison et journalisation obligatoire dans {@link LogReport}.
 * Chaque action significative est tracée conformément aux exigences réglementaires
 * de traçabilité en anatomopathologie.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final LogReportRepository logReportRepository;
    private final TagRepository tagRepository;
    private final TitleReportRepository titleReportRepository;
    private final TestOrderRepository testOrderRepository;
    private final UserRepository userRepository;
    private final SettingReportTemplateRepository templateRepository;
    private final ReportMapper reportMapper;
    private final EmailService emailService;
    private final NotificationSettings notificationSettings;
    private final com.labo.anapath.mobile.MobileDeviceRepository mobileDeviceRepository;
    private final com.labo.anapath.mobile.SignatureAppareil signatureAppareil;
    private final com.labo.anapath.mobile.ProvenanceRequete provenanceRequete;
    private final com.labo.anapath.testorder.TestOrderAssignmentDetailRepository assignmentDetailRepository;
    /** Pour relire les étiquettes, rangées en tableau JSON sur la ligne d'affectation. */
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportResponseDto> findAll(int page, int size, UUID branchId) {
        return PageResponse.of(reportRepository.findByBranchId(branchId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(reportMapper::toResponseDto));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportResponseDto> findAll(int page, int size, UUID branchId, Integer month, Integer year, UUID doctorId) {
        return PageResponse.of(reportRepository.findFiltered(branchId, month, year, doctorId,
                PageRequest.of(page, size))
                .map(reportMapper::toResponseDto));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportResponseDto> findAll(int page, int size, UUID branchId, Integer month, Integer year, UUID doctorId, String status, String search) {
        String statusParam = null;
        if (status != null && !status.isBlank()) {
            try {
                ReportStatus.valueOf(status.toUpperCase());
                statusParam = status.toUpperCase();
            } catch (IllegalArgumentException e) {
                log.warn("Statut de rapport invalide ignoré : '{}'", status);
            }
        }
        String searchParam = (search != null && !search.isBlank()) ? search.trim() : null;
        return PageResponse.of(reportRepository.findFilteredWithSearch(
                branchId, month, year, doctorId, statusParam, searchParam,
                PageRequest.of(page, size))
                .map(reportMapper::toResponseDto));
    }

    @Override
    @Transactional(readOnly = true)
    public ReportResponseDto findById(UUID id) {
        return reportMapper.toResponseDto(reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public ReportDetailDto findDetailByTestOrderCode(String code, UUID branchId) {
        String recherche = code == null ? "" : code.trim();
        if (recherche.isEmpty()) {
            throw new ResourceNotFoundException("Aucun code de demande d'examen fourni.");
        }
        // On délègue à findDetailById : le cloisonnement par branche, le journal
        // et le nom du patient y sont déjà traités. Le code ne fait donc que
        // désigner le dossier, sans ouvrir un second chemin de lecture qui
        // divergerait du premier.
        Report report = reportRepository.findByTestOrder_CodeIgnoreCase(recherche)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun compte-rendu ne correspond au code « " + recherche + " »."));
        return findDetailById(report.getId(), branchId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public DossierResumeDto findResumeByTestOrderCode(String code, UUID branchId) {
        // On réutilise la lecture complète plutôt que d'écrire une seconde
        // requête : le cloisonnement par branche, le nom du patient et le
        // journal y sont déjà traités, et deux chemins de lecture finiraient
        // par diverger. Seule la restitution est réduite.
        ReportDetailDto detail = findDetailByTestOrderCode(code, branchId);
        return new DossierResumeDto(
                detail.id(),
                detail.code(),
                detail.testOrderId(),
                detail.testOrderCode(),
                detail.patientName(),
                detail.titleName(),
                detail.status(),
                detail.isDelivered(),
                detail.retrieverName(),
                detail.retrieverRelation(),
                detail.retrieverSignature(),
                detail.demandeCreatedAt(),
                detail.deliveryDate(),
                detail.assignedToName(),
                detail.assignmentCode(),
                detail.assignmentDate(),
                detail.assignmentLabels(),
                detail.assignmentNote(),
                detail.assignmentLotNote());
    }

    @Override
    @Transactional(readOnly = true)
    public ReportDetailDto findDetailById(UUID id, UUID branchId) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", id));
        if (!report.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Compte-rendu", id);
        }

        List<LogReport> logs = logReportRepository.findByReportIdOrderByCreatedAtDesc(id);
        List<ReportDetailDto.LogReportDto> logDtos = logs.stream()
                .map(l -> new ReportDetailDto.LogReportDto(
                        l.getAction(),
                        l.getDescription(),
                        l.getUser() != null ? NomComplet.de(l.getUser().getLastname(), l.getUser().getFirstname()) : null,
                        l.getCreatedAt()))
                .toList();

        String patientName = null;
        if (report.getTestOrder() != null && report.getTestOrder().getPatient() != null) {
            var p = report.getTestOrder().getPatient();
            patientName = NomComplet.de(p.getLastname(), p.getFirstname());
        }

        Affectation affectation = affectationDe(report);

        return new ReportDetailDto(
                report.getId(), report.getCode(),
                report.getTestOrder() != null ? report.getTestOrder().getId() : null,
                report.getTestOrder() != null ? report.getTestOrder().getCode() : null,
                patientName,
                report.getTitleReport() != null ? report.getTitleReport().getId() : null,
                report.getTitleReport() != null ? report.getTitleReport().getName() : null,
                report.getContent(), report.getContentMicro(),
                report.getComment(), report.getCommentSup(),
                report.getDescriptionSupplementaire(), report.getDescriptionSupplementaireMicro(),
                report.getStatus(),
                report.isDelivered(), report.isCalled(),
                report.getReceiverName(),
                report.getRetrieverName(),
                report.getRetrieverRelation(),
                report.getRetrieverSignature(),
                report.getTestOrder() != null ? report.getTestOrder().getCreatedAt() : null,
                report.getSignatureDate(), report.getDeliveryDate(), report.getCallDate(),
                report.getSignatory1() != null ? report.getSignatory1().getId() : null,
                report.getSignatory1() != null ? NomComplet.de(report.getSignatory1().getLastname(), report.getSignatory1().getFirstname()) : null,
                report.getSignatory2() != null ? report.getSignatory2().getId() : null,
                report.getSignatory2() != null ? NomComplet.de(report.getSignatory2().getLastname(), report.getSignatory2().getFirstname()) : null,
                report.getSignatory3() != null ? report.getSignatory3().getId() : null,
                report.getSignatory3() != null ? NomComplet.de(report.getSignatory3().getLastname(), report.getSignatory3().getFirstname()) : null,
                report.getReviewedBy() != null ? report.getReviewedBy().getId() : null,
                report.getReviewedBy() != null ? NomComplet.de(report.getReviewedBy().getLastname(), report.getReviewedBy().getFirstname()) : null,
                report.getTags().stream().map(Tag::getName).toList(),
                report.getTags().stream().map(Tag::getId).toList(),
                logDtos,
                affectation.nom(), affectation.code(), affectation.date(),
                affectation.etiquettes(), affectation.note(), affectation.noteDuLot(),
                report.getCreatedAt(), report.getUpdatedAt());
    }

    /**
     * Ce que le suivi montre d'une affectation.
     *
     * <p>Chez qui, sous quel code, quand — et ce qui accompagne le prélèvement :
     * ses étiquettes, la note écrite pour cette demande, et celle du lot
     * entier. Les deux notes sont distinctes et le restent : l'une vise un
     * dossier, l'autre une série.</p>
     */
    private record Affectation(String nom, String code, java.time.LocalDate date,
                               java.util.List<String> etiquettes,
                               String note, String noteDuLot) {
        static final Affectation AUCUNE =
                new Affectation(null, null, null, java.util.List.of(), null, null);
    }

    /**
     * L'affectation d'une demande, si elle en a une.
     *
     * <p>Lecture tolérante : une demande non encore affectée est le cas normal
     * au comptoir, pas une anomalie. Elle rend des nuls, que l'écran traduit en
     * « pas encore affectée » plutôt qu'en erreur.</p>
     */
    private Affectation affectationDe(Report report) {
        if (report.getTestOrder() == null) return Affectation.AUCUNE;
        return assignmentDetailRepository.findByTestOrderId(report.getTestOrder().getId())
                .filter(d -> d.getTestOrderAssignment() != null)
                .map(d -> {
                    var a = d.getTestOrderAssignment();
                    return new Affectation(
                            a.getUser() == null ? null
                                    : NomComplet.de(a.getUser().getLastname(),
                                                    a.getUser().getFirstname()),
                            a.getCode(),
                            a.getDate(),
                            com.labo.anapath.testorder.Etiquettes.decoder(
                                    objectMapper, d.getLabels()),
                            d.getNote(),
                            a.getNote());
                })
                .orElse(Affectation.AUCUNE);
    }

    @Override
    @Transactional
    public ReportResponseDto createOrUpdate(ReportRequestDto dto, UUID branchId) {
        Report report;
        boolean isCreate = dto.getReportId() == null;

        if (!isCreate) {
            report = reportRepository.findById(dto.getReportId())
                    .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", dto.getReportId()));
            // Un compte-rendu livré reste modifiable : voir la note sur
            // `update` ci-dessous — la livraison est un fait matériel, pas un
            // scellé éditorial, et les compléments arrivent après la remise.
        } else {
            report = new Report();
            report.setBranchId(branchId);
            if (dto.getTestOrderId() == null) {
                throw new InvalidOperationException("Le bon d'examen est obligatoire à la création.");
            }
            report.setTestOrder(testOrderRepository.findById(dto.getTestOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", dto.getTestOrderId())));
        }

        report.setContent(dto.getContent());
        report.setContentMicro(dto.getContentMicro());
        report.setComment(dto.getComment());
        report.setCommentSup(dto.getCommentSup());
        report.setDescriptionSupplementaire(dto.getDescriptionSupplementaire());
        report.setDescriptionSupplementaireMicro(dto.getDescriptionSupplementaireMicro());

        if (dto.getTitleId() != null) {
            report.setTitleReport(titleReportRepository.findById(dto.getTitleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Titre", dto.getTitleId())));
        }
        if (dto.getReviewedById() != null) {
            UUID previousReviewerId = report.getReviewedBy() != null ? report.getReviewedBy().getId() : null;
            User reviewer = userRepository.findById(dto.getReviewedById())
                    .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", dto.getReviewedById()));
            report.setReviewedBy(reviewer);
            if (!reviewer.getId().equals(previousReviewerId)) {
                notifyAssignedReviewer(report, reviewer);
            }
        }
        if (dto.getSignatory1Id() != null) {
            report.setSignatory1(userRepository.findById(dto.getSignatory1Id())
                    .orElseThrow(() -> new ResourceNotFoundException("Signataire 1", dto.getSignatory1Id())));
        }
        if (dto.getSignatory2Id() != null) {
            report.setSignatory2(userRepository.findById(dto.getSignatory2Id())
                    .orElseThrow(() -> new ResourceNotFoundException("Signataire 2", dto.getSignatory2Id())));
        }
        if (dto.getSignatory3Id() != null) {
            report.setSignatory3(userRepository.findById(dto.getSignatory3Id())
                    .orElseThrow(() -> new ResourceNotFoundException("Signataire 3", dto.getSignatory3Id())));
        }

        if ("VALIDATED".equalsIgnoreCase(dto.getStatus())) {
            exigerLeDroitDeValider(report.getStatus());
            report.setStatus(ReportStatus.VALIDATED);
            report.setSignatureDate(LocalDateTime.now());
            report.setDeliveryDate(LocalDateTime.now());
            if (dto.getSignatory1Id() != null && report.getTestOrder() != null) {
                report.getTestOrder().setAssignedToUserId(dto.getSignatory1Id());
                testOrderRepository.save(report.getTestOrder());
            }
        } else {
            report.setStatus(ReportStatus.DRAFT);
        }

        // Sync COMPLET des tags : delete + re-insert
        report.getTags().clear();
        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            report.setTags(tagRepository.findAllById(dto.getTagIds()));
        }

        Report saved = reportRepository.save(report);
        logAction(saved.getId(), isCreate ? "CREATE" : "UPDATE", branchId);
        return reportMapper.toResponseDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportSuiviRowDto> getSuiviList(
            UUID branchId, int page, int size,
            String search, String typeOrderId,
            String dateBegin, String dateEnd,
            Boolean isUrgent, Integer statusFilter, Boolean isLate) {
        var pageRequest = PageRequest.of(page, size);
        var resultPage = reportRepository.findSuiviRows(
                branchId,
                (search != null && !search.isBlank()) ? search.trim() : null,
                (typeOrderId != null && !typeOrderId.isBlank()) ? typeOrderId : null,
                (dateBegin != null && !dateBegin.isBlank()) ? dateBegin : null,
                (dateEnd != null && !dateEnd.isBlank()) ? dateEnd : null,
                isUrgent, statusFilter,
                Boolean.TRUE.equals(isLate) ? Boolean.TRUE : null,
                pageRequest);

        return PageResponse.of(resultPage.map(p -> new ReportSuiviRowDto(
                p.getReportId() != null ? UUID.fromString(p.getReportId()) : null,
                p.getTestOrderId() != null ? UUID.fromString(p.getTestOrderId()) : null,
                p.getTestOrderCode(),
                p.getTypeOrderTitle(),
                p.getPatientFirstname(), p.getPatientLastname(),
                p.getPatientPhone(),
                p.getIsUrgent(),
                p.getCreatedAt(),
                p.getReportStatus() != null ? ReportStatus.valueOf(p.getReportStatus()) : null,
                Boolean.TRUE.equals(p.getHasMacro()),
                p.getAssignedDoctorId() != null ? UUID.fromString(p.getAssignedDoctorId()) : null,
                p.getAssignedDoctorName(),
                Boolean.TRUE.equals(p.getIsCalled()),
                Boolean.TRUE.equals(p.getIsDelivered()),
                p.getRetrieverName(),
                p.getRetrieverSignature(),
                p.getDeliveryDate()
        )));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LogReportResponseDto> getReportLogs(UUID branchId, int page, int size) {
        var pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(
                logReportRepository.findByBranchId(branchId, pageRequest).map(l -> {
                    var report = l.getReport();
                    var user = l.getUser();
                    return new LogReportResponseDto(
                            l.getId(),
                            l.getAction(),
                            l.getDescription(),
                            l.getCreatedAt(),
                            user != null
                                    ? NomComplet.de(user.getLastname(), user.getFirstname())
                                    : null,
                            report != null ? report.getId() : null,
                            report != null ? report.getCode() : null,
                            (report != null && report.getTestOrder() != null)
                                    ? report.getTestOrder().getCode()
                                    : null);
                }));
    }

    @Override
    @Transactional(readOnly = true)
    public ReportSuiviDto getSuivi(UUID branchId, Integer month, Integer year) {
        Object[] examenRaw = reportRepository.getExamenStats(branchId, month, year);
        Object[] rapportRaw = reportRepository.getRapportStats(branchId);
        Object[] calledRaw = reportRepository.getPatientCalledStats(branchId);
        List<Integer> years = reportRepository.findAvailableYears(branchId);
        Long macroCount = reportRepository.countMacrosWithOrders(branchId);

        Object[] ex = safeRow(examenRaw, 5);
        Object[] rp = safeRow(rapportRaw, 3);
        Object[] ca = safeRow(calledRaw, 4);

        ReportSuiviDto.ExamenStats examens = new ReportSuiviDto.ExamenStats(
                toLong(ex[0]), toLong(ex[1]), toLong(ex[2]), toLong(ex[3]), toLong(ex[4]));
        ReportSuiviDto.RapportStats rapports = new ReportSuiviDto.RapportStats(
                toLong(rp[0]), toLong(rp[1]), toLong(rp[2]));
        ReportSuiviDto.PatientCalledStats calledStats = new ReportSuiviDto.PatientCalledStats(
                toLong(ca[0]), toLong(ca[1]), toLong(ca[2]), toLong(ca[3]));

        return new ReportSuiviDto(examens, rapports,
                new ReportSuiviDto.MacroStats(macroCount != null ? macroCount : 0L), calledStats, years);
    }

    private Object[] safeRow(Object[] raw, int expectedCols) {
        if (raw == null || raw.length == 0) return new Object[expectedCols];
        // Native query single-row: raw is the row itself
        if (raw[0] instanceof Object[] nested) return nested;
        return raw;
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        return Long.parseLong(val.toString());
    }

    @Override
    @Transactional
    public ReportResponseDto create(ReportRequestDto dto, UUID branchId) {
        Report report = new Report();
        report.setBranchId(branchId);
        report.setContent(dto.getContent());
        report.setComment(dto.getComment());
        report.setReceiverName(dto.getReceiverName());
        report.setStatus(ReportStatus.DRAFT);

        report.setTestOrder(testOrderRepository.findById(dto.getTestOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", dto.getTestOrderId())));

        if (dto.getTitleId() != null) {
            report.setTitleReport(titleReportRepository.findById(dto.getTitleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Titre", dto.getTitleId())));
        }
        if (dto.getReviewedById() != null) {
            User reviewer = userRepository.findById(dto.getReviewedById())
                    .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", dto.getReviewedById()));
            report.setReviewedBy(reviewer);
            notifyAssignedReviewer(report, reviewer);
        }

        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            List<Tag> tags = tagRepository.findAllById(dto.getTagIds());
            report.setTags(tags);
        }

        Report saved = reportRepository.save(report);
        logAction(saved.getId(), "CREATE", branchId);
        return reportMapper.toResponseDto(saved);
    }

    /**
     * Notifie par email un utilisateur fraîchement assigné comme relecteur d'un compte-rendu.
     * (Réplique Laravel : {@code AssignedReviewMail}.)
     */
    private void notifyAssignedReviewer(Report report, User reviewer) {
        if (reviewer.getEmail() == null || reviewer.getEmail().isBlank()) {
            return;
        }
        String reviewerName = NomComplet.de(reviewer.getLastname(), reviewer.getFirstname());
        String reportTitle = report.getTitleReport() != null
                ? report.getTitleReport().getName()
                : report.getCode();
        String orderCode = report.getTestOrder() != null ? report.getTestOrder().getCode() : "";
        String labName = notificationSettings.labName(report.getBranchId());
        emailService.sendAssignedReview(reviewer.getEmail(), reviewerName, reportTitle, orderCode, labName);
    }

    /**
     * Met à jour le contenu textuel et le commentaire d'un compte-rendu.
     *
     * <p>Un compte-rendu livré <b>reste modifiable</b>. Le verrou posé ici
     * n'existait pas dans Laravel, où la remise du résultat était un simple
     * drapeau {@code is_delivered} — orthogonal au statut, qui ne connaissait
     * que 0 (en attente) et 1 (terminé). La réécriture a fait de DELIVERED une
     * valeur du statut, fusionnant deux notions distinctes : dès lors, livrer
     * un résultat le scellait définitivement.</p>
     *
     * <p>Or un complément arrive par nature <i>après</i> la remise — c'est
     * précisément ce que servent {@code description_supplementaire} et la case
     * « Complémentaire ». Le verrou rendait cette fonction inatteignable au
     * moment même où elle devient utile, et bloquait la correction de dossiers
     * déjà sortis.</p>
     *
     * @param id  identifiant UUID du CR
     * @param dto nouvelles données
     * @return le CR mis à jour
     */
    @Override
    @Transactional
    public ReportResponseDto update(UUID id, ReportRequestDto dto, UUID userId, UUID branchId) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", id));

        // Un compte-rendu est signé dès qu'un médecin y est apposé et que la
        // validation a posé la date. L'empreinte est prise AVANT toute écriture :
        // au-delà, l'état d'origine est perdu et la comparaison impossible.
        boolean etaitSigne = report.getSignatureDate() != null && report.getSignatory1() != null;
        EmpreinteCompteRendu empreinte = etaitSigne ? EmpreinteCompteRendu.de(report) : null;

        // -------------------------------------------------------------------
        // Réplique EXACTE de ReportController@store (Laravel) : un unique
        // enregistrement pilote le contenu ET le statut du compte-rendu.
        // Le select « État du compte rendu » envoie :
        //   - "VALIDATED" (Terminé)           → statut VALIDATED
        //   - "DRAFT"     (En attente relecture) → statut DRAFT
        // -------------------------------------------------------------------

        // Relecteur nouvellement (ré)assigné → notification (Laravel : AssignedReviewer)
        UUID currentReviewerId = report.getReviewedBy() != null ? report.getReviewedBy().getId() : null;
        boolean reviewerChanged = dto.getReviewedById() != null
                && !dto.getReviewedById().equals(currentReviewerId);

        // Contenu (macro = content/description, micro = contentMicro, compléments)
        report.setContent(dto.getContent());
        report.setContentMicro(dto.getContentMicro());
        report.setComment(dto.getComment());
        report.setCommentSup(dto.getCommentSup());
        report.setDescriptionSupplementaire(
                dto.getDescriptionSupplementaire() != null ? dto.getDescriptionSupplementaire() : "");
        report.setDescriptionSupplementaireMicro(
                dto.getDescriptionSupplementaireMicro() != null ? dto.getDescriptionSupplementaireMicro() : "");
        report.setReceiverName(dto.getReceiverName());

        // Titre
        if (dto.getTitleId() != null) {
            report.setTitleReport(titleReportRepository.findById(dto.getTitleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Titre", dto.getTitleId())));
        } else {
            report.setTitleReport(null);
        }

        // Signataires (1 = « Signé par », 2/3 optionnels)
        report.setSignatory1(dto.getSignatory1Id() != null
                ? userRepository.findById(dto.getSignatory1Id())
                    .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", dto.getSignatory1Id()))
                : null);
        report.setSignatory2(dto.getSignatory2Id() != null
                ? userRepository.findById(dto.getSignatory2Id())
                    .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", dto.getSignatory2Id()))
                : null);
        report.setSignatory3(dto.getSignatory3Id() != null
                ? userRepository.findById(dto.getSignatory3Id())
                    .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", dto.getSignatory3Id()))
                : null);

        // Relecteur (avis de relecture)
        User reviewer = dto.getReviewedById() != null
                ? userRepository.findById(dto.getReviewedById())
                    .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", dto.getReviewedById()))
                : null;
        report.setReviewedBy(reviewer);

        // Statut piloté par le select (Laravel : $request->status == 1 / == 0)
        if ("VALIDATED".equalsIgnoreCase(dto.getStatus())) {
            exigerLeDroitDeValider(report.getStatus());
            report.setStatus(ReportStatus.VALIDATED);
            report.setDeliveryDate(LocalDateTime.now());
        } else if ("DRAFT".equalsIgnoreCase(dto.getStatus())) {
            report.setStatus(ReportStatus.DRAFT);
        }

        // La demande liée pointe vers le signataire 1 (Laravel : order.assigned_to_user_id)
        if (report.getTestOrder() != null) {
            report.getTestOrder().setAssignedToUserId(dto.getSignatory1Id());
            testOrderRepository.save(report.getTestOrder());
        }

        // Date de signature quand validé/livré (Laravel : signature_date = now())
        if (report.getStatus() == ReportStatus.VALIDATED || report.getStatus() == ReportStatus.DELIVERED) {
            report.setSignatureDate(LocalDateTime.now());
        }

        // Tags : synchronisation (remplace l'ensemble, comme tags()->sync()/attach())
        List<Tag> tags = (dto.getTagIds() != null && !dto.getTagIds().isEmpty())
                ? tagRepository.findAllById(dto.getTagIds())
                : new ArrayList<>();
        report.setTags(tags);

        Report saved = reportRepository.save(report);

        if (reviewerChanged && reviewer != null) {
            notifyAssignedReviewer(saved, reviewer);
        }
        logAction(saved.getId(), "Mettre à jour", userId);

        if (empreinte != null) {
            List<String> champsModifies = empreinte.champsModifies(saved);
            if (!champsModifies.isEmpty()) {
                tracerModificationApresSignature(saved, champsModifies, userId);
            }
        }

        return reportMapper.toResponseDto(saved);
    }

    /** Libellé de l'action portée au journal ; sert aussi de critère de relecture. */
    static final String ACTION_APRES_SIGNATURE = "Modification après signature";

    /** Sépare le récit de la liste des champs dans la description journalisée. */
    private static final String MARQUEUR_CHAMPS = "Champs : ";

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<ModificationApresSignatureDto> getModificationsApresSignature(UUID reportId) {
        return logReportRepository
                .findByReportIdAndActionOrderByCreatedAtAsc(reportId, ACTION_APRES_SIGNATURE)
                .stream()
                .map(trace -> new ModificationApresSignatureDto(
                        trace.getUser() != null ? trace.getUser().getId() : null,
                        trace.getUser() != null
                                ? NomComplet.de(trace.getUser().getLastname(), trace.getUser().getFirstname())
                                : "Utilisateur supprimé",
                        trace.getCreatedAt(),
                        champsDepuisDescription(trace.getDescription())))
                .toList();
    }

    /**
     * Extrait la liste des champs de la description journalisée.
     *
     * <p>Le marqueur peut manquer sur une entrée écrite par une version
     * antérieure : on rend alors la description entière plutôt que rien, quitte
     * à être verbeux — une trace lisible vaut mieux qu'une case vide.</p>
     */
    private static String champsDepuisDescription(String description) {
        if (description == null) {
            return "";
        }
        int position = description.indexOf(MARQUEUR_CHAMPS);
        return position >= 0
                ? description.substring(position + MARQUEUR_CHAMPS.length())
                : description;
    }

    /**
     * Consigne une modification survenue après signature et en avertit les
     * administrateurs.
     *
     * <p>Un compte-rendu signé engage le médecin qui l'a signé. Le modifier
     * ensuite reste possible — les compléments arrivent après la remise du
     * résultat — mais ne doit pas passer inaperçu : l'action est portée au
     * journal avec le détail des champs touchés et l'auteur, puis signalée aux
     * adresses configurées sous {@code admin_mails}.</p>
     *
     * <p>L'échec d'un envoi n'annule pas la modification : la trace en base est
     * la garantie, le courriel n'en est que le rappel. {@code EmailServiceImpl}
     * absorbe déjà ses propres erreurs ; on protège ici la résolution des
     * destinataires, qui dépend d'un paramétrage pouvant manquer.</p>
     */
    private void tracerModificationApresSignature(Report report, List<String> champs, UUID userId) {
        User auteur = userRepository.findById(userId).orElse(null);
        String nomAuteur = auteur != null
                ? NomComplet.de(auteur.getLastname(), auteur.getFirstname())
                : "Utilisateur inconnu";
        String listeChamps = String.join(", ", champs);

        LogReport trace = new LogReport();
        trace.setBranchId(report.getBranchId());
        trace.setReport(report);
        trace.setUser(auteur);
        trace.setAction(ACTION_APRES_SIGNATURE);
        trace.setDescription("Modifié par " + nomAuteur + " après signature. "
                + MARQUEUR_CHAMPS + listeChamps);
        logReportRepository.save(trace);

        log.warn("Compte-rendu {} modifié après signature par {} — champs: {}",
                report.getCode(), nomAuteur, listeChamps);

        try {
            String nomLabo = notificationSettings.labName(report.getBranchId());
            String codeDemande = report.getTestOrder() != null ? report.getTestOrder().getCode() : "";
            String signataire = report.getSignatory1() != null
                    ? NomComplet.de(report.getSignatory1().getLastname(), report.getSignatory1().getFirstname())
                    : "";
            for (String destinataire : notificationSettings.adminEmails(report.getBranchId())) {
                emailService.sendPostSignatureChangeAlert(destinataire, report.getCode(),
                        codeDemande, signataire, nomAuteur, listeChamps, nomLabo);
            }
        } catch (Exception e) {
            log.error("Alerte de modification après signature non envoyée pour {}: {}",
                    report.getCode(), e.getMessage());
        }
    }

    /**
     * Supprime (soft delete) un compte-rendu.
     *
     * @param id identifiant UUID du CR à supprimer
     */
    @Override
    @Transactional
    public void delete(UUID id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", id));
        reportRepository.delete(report);
    }

    /**
     * Valide un compte-rendu en passant son statut à VALIDATED et journalise l'action.
     *
     * @param id     identifiant UUID du CR
     * @param userId identifiant de l'utilisateur validant le CR
     * @return le CR validé
     */
    @Override
    @Transactional
    public ReportResponseDto validate(UUID id, UUID userId) {
        return validate(id, userId, null);
    }

    @Override
    @Transactional
    public ReportResponseDto validate(UUID id, UUID userId, ValidationSigneeDto preuve) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", id));
        if (report.getStatus() == ReportStatus.VALIDATED || report.getStatus() == ReportStatus.DELIVERED) {
            throw new InvalidOperationException("Le rapport est déjà validé ou livré.");
        }
        report.setStatus(ReportStatus.VALIDATED);
        report.setSignatureDate(LocalDateTime.now());
        report.setDeliveryDate(LocalDateTime.now());

        // Une session ouverte depuis un téléphone enrôlé DOIT signer. Sans cette
        // exigence, il suffirait à l'application d'omettre la preuve pour
        // retomber au niveau de garantie du web, et le dispositif ne tiendrait
        // que par la bonne volonté du client. Le web, lui, n'a pas de clé et
        // continue de valider sur la seule foi de sa session.
        UUID appareilDeLaSession = provenanceRequete.appareilCourant();
        if (appareilDeLaSession != null && preuve == null) {
            throw new AccessDeniedException(
                    "Une validation depuis l'application mobile doit être signée par l'appareil.");
        }
        if (preuve != null && appareilDeLaSession != null
                && !appareilDeLaSession.equals(preuve.deviceId())) {
            throw new AccessDeniedException(
                    "La preuve ne provient pas de l'appareil ayant ouvert la session.");
        }

        if (preuve != null) {
            verifierEtAttacherLaPreuve(report, userId, preuve);
        }

        Report saved = reportRepository.save(report);
        logAction(id, preuve != null ? "Validé (signé par appareil)" : "Validé", userId);
        return reportMapper.toResponseDto(saved);
    }

    /**
     * Contrôle la preuve d'appareil, puis l'attache au compte-rendu.
     *
     * <p>Quatre conditions, toutes nécessaires : l'appareil existe et n'est pas
     * révoqué ; il appartient bien à l'auteur de l'acte ; l'horodatage signé est
     * frais ; et la signature vérifie contre la clé publique déposée à
     * l'enrôlement. Le condensé est <strong>recomposé par le serveur</strong> et
     * jamais repris du client — sans quoi il suffirait d'envoyer un message
     * quelconque avec sa propre signature.</p>
     *
     * <p>Une preuve invalide fait échouer la validation. La traiter comme
     * absente reviendrait à offrir un moyen simple de contourner l'exigence,
     * puisqu'il suffirait d'envoyer n'importe quoi.</p>
     */
    private void verifierEtAttacherLaPreuve(Report report, UUID userId, ValidationSigneeDto preuve) {
        MobileDevice appareil = mobileDeviceRepository.findByIdAndRevokedAtIsNull(preuve.deviceId())
                .orElseThrow(() -> new AccessDeniedException("Appareil inconnu ou révoqué."));

        if (!appareil.getUserId().equals(userId)) {
            throw new AccessDeniedException("Cet appareil n'appartient pas à l'auteur de la validation.");
        }
        // Horloge décalée : ce n'est pas un refus de droit mais une condition
        // qu'on peut corriger, et le dire ne renseigne personne d'utile.
        // AccessDeniedException aurait donné « Accès refusé » — le porteur du
        // téléphone aurait cherché du côté de ses permissions pendant des heures.
        if (!signatureAppareil.horodatageAcceptable(preuve.signedAt())) {
            throw new InvalidOperationException(
                    "L'heure de l'appareil s'écarte trop de celle du serveur. "
                            + "Activez la mise à l'heure automatique, puis réessayez.");
        }

        String condense = signatureAppareil.condense(report.getId(), userId, preuve.signedAt());
        if (!signatureAppareil.verifier(appareil.getPublicKey(), condense, preuve.signature())) {
            throw new AccessDeniedException("Signature d'appareil invalide.");
        }

        report.setSigningDeviceId(appareil.getId());
        report.setDeviceSignature(preuve.signature());
        report.setDeviceSignedAt(preuve.signedAt());
    }

    @Override
    @Transactional
    public ReportResponseDto deliver(UUID id, String receiverName, UUID userId) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", id));
        if (report.getStatus() != ReportStatus.VALIDATED) {
            throw new InvalidOperationException("Le compte-rendu doit être validé avant d'être livré.");
        }
        report.setStatus(ReportStatus.DELIVERED);
        report.setDelivered(true);
        report.setReceiverName(receiverName);
        // La demande d'examen associée passe aussi à DELIVERED : sinon la liste et
        // les détails de la demande (qui affichent order.status) restent « Validé »
        // alors que le compte-rendu est livré.
        if (report.getTestOrder() != null) {
            report.getTestOrder().setStatus(com.labo.anapath.testorder.TestOrderStatus.DELIVERED);
        }
        Report saved = reportRepository.save(report);
        logAction(id, "Livré", userId);
        return reportMapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public ReportResponseDto markDelivered(UUID id, UUID userId) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", id));
        report.setDelivered(true);
        report.setDeliveryDate(LocalDateTime.now());
        // Cohérence : statut du rapport ET de la demande passent à DELIVERED.
        report.setStatus(ReportStatus.DELIVERED);
        if (report.getTestOrder() != null) {
            report.getTestOrder().setStatus(com.labo.anapath.testorder.TestOrderStatus.DELIVERED);
        }
        Report saved = reportRepository.save(report);
        logAction(id, "Livré", userId);
        return reportMapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public ReportResponseDto markInformed(UUID id, UUID userId) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", id));
        report.setCalled(true);
        report.setCallDate(LocalDateTime.now());
        Report saved = reportRepository.save(report);
        logAction(id, "Informé", userId);
        return reportMapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public ReportResponseDto storeSignature(UUID id, StoreSignatureRequestDto dto, UUID userId) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", id));
        // RÈGLE R5 : isDelivered ET isCalled positionnés SIMULTANÉMENT dans la même transaction
        report.setDelivered(true);
        report.setDeliveryDate(LocalDateTime.now());
        report.setCalled(true);
        report.setCallDate(LocalDateTime.now());
        report.setRetrieverName(dto.getSignatorName());
        report.setRetrieverRelation(dto.getRelation());
        report.setRetrieverSignature(dto.getSignature());
        // Cohérence de statut, comme dans deliver() et markDelivered().
        //
        // Cette méthode posait `isDelivered` sans toucher au statut : le compte-rendu
        // se disait remis tout en restant VALIDATED, et la demande d'examen ne bougeait
        // pas du tout. Les listes, qui affichent `order.status`, montraient donc encore
        // « Validé » pour un dossier signé et emporté. Recueillir la signature du
        // récupérateur EST la remise — c'est le geste qui la constate.
        report.setStatus(ReportStatus.DELIVERED);
        if (report.getTestOrder() != null) {
            report.getTestOrder().setStatus(com.labo.anapath.testorder.TestOrderStatus.DELIVERED);
        }
        Report saved = reportRepository.save(report);
        logAction(id, "Signature enregistrée", userId);
        return reportMapper.toResponseDto(saved);
    }

    /**
     * Exige le droit {@code validate-reports} pour faire passer un compte-rendu
     * à l'état validé.
     *
     * <h4>Pourquoi la garde est ici et pas seulement sur {@code /validate}</h4>
     *
     * <p>Le point d'entrée {@code POST /reports/{id}/validate} n'est appelé par
     * personne : l'interface web fait passer le statut à VALIDATED en
     * <strong>enregistrant</strong> le compte-rendu, avec {@code status} dans le
     * corps. Poser la permission sur le seul point d'entrée dédié laissait donc
     * la vraie porte grande ouverte, et la séparation n'aurait vécu que sur le
     * papier.</p>
     *
     * <p>La vérification ne se déclenche qu'à la <em>transition</em> : rouvrir et
     * réenregistrer un compte-rendu déjà validé ne la rejoue pas. Sans cela, un
     * agent d'accueil ne pourrait plus corriger une coquille sur un dossier
     * validé — ce qu'il a le droit de faire, et qui n'est pas un acte médical.</p>
     *
     * <p>La lecture passe par le contexte de sécurité plutôt que par un
     * paramètre : la signature de ces méthodes est partagée avec le reste du
     * service, et l'y faire entrer imposerait de la modifier partout pour un
     * besoin qui ne concerne que cette branche.</p>
     */
    private void exigerLeDroitDeValider(ReportStatus statutActuel) {
        if (statutActuel == ReportStatus.VALIDATED || statutActuel == ReportStatus.DELIVERED) {
            return;
        }
        var authentification = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        boolean autorise = authentification != null && authentification.getAuthorities().stream()
                .anyMatch(a -> "validate-reports".equals(a.getAuthority()));
        if (!autorise) {
            // AccessDeniedException et non UnauthorizedException : l'utilisateur est
            // bien authentifié, il lui manque un droit. C'est un 403, et c'est ce que
            // lève @PreAuthorize ailleurs — un 401 ferait tenter au client un
            // rafraîchissement de jeton, voire une déconnexion, pour rien.
            throw new org.springframework.security.access.AccessDeniedException(
                    "Valider un compte-rendu engage un diagnostic et requiert le droit « validate-reports ».");
        }
    }

    /**
     * Enregistre une entrée dans le journal de traçabilité {@link LogReport}.
     *
     * <p>Si le CR ou l'utilisateur n'existe pas, l'action est silencieusement ignorée
     * (usage de {@code ifPresent}) pour ne pas bloquer les opérations principales.
     *
     * @param reportId identifiant du CR concerné
     * @param action   libellé de l'action (ex. {@code "CREATE"}, {@code "VALIDATE"}, {@code "DELIVER"})
     * @param userId   identifiant de l'auteur de l'action
     */
    @Override
    @Transactional(readOnly = true)
    public SettingReportTemplate getTemplate(UUID reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", reportId));
        if (report.getTemplateId() == null) {
            throw new ResourceNotFoundException("Template", reportId);
        }
        return templateRepository.findById(report.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Template", report.getTemplateId()));
    }

    @Override
    @Transactional
    public ReportResponseDto setTemplate(UUID reportId, UUID templateId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", reportId));
        templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template", templateId));
        report.setTemplateId(templateId);
        return reportMapper.toResponseDto(reportRepository.save(report));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportGlobalSearchRowDto> globalSearch(
            UUID branchId, int page, int size,
            List<String> typeOrderIds, List<String> contratIds,
            List<String> patientIds, List<String> doctorIds,
            List<String> hospitalIds, String referenceHospital,
            String dateBegin, String dateEnd,
            String content, Boolean isUrgent) {

        var pageRequest = PageRequest.of(page, size);
        String typeOrderIdsCsv = (typeOrderIds == null || typeOrderIds.isEmpty()) ? null : String.join(",", typeOrderIds);
        String contratIdsCsv = (contratIds == null || contratIds.isEmpty()) ? null : String.join(",", contratIds);
        String patientIdsCsv = (patientIds == null || patientIds.isEmpty()) ? null : String.join(",", patientIds);
        String doctorIdsCsv = (doctorIds == null || doctorIds.isEmpty()) ? null : String.join(",", doctorIds);
        String hospitalIdsCsv = (hospitalIds == null || hospitalIds.isEmpty()) ? null : String.join(",", hospitalIds);

        var result = reportRepository.globalSearch(
                branchId, typeOrderIdsCsv, contratIdsCsv, patientIdsCsv, doctorIdsCsv, hospitalIdsCsv,
                (referenceHospital != null && !referenceHospital.isBlank()) ? referenceHospital : null,
                (dateBegin != null && !dateBegin.isBlank()) ? dateBegin : null,
                (dateEnd != null && !dateEnd.isBlank()) ? dateEnd : null,
                (content != null && !content.isBlank()) ? content : null,
                isUrgent, pageRequest);

        var rows = result.stream().map(p -> new ReportGlobalSearchRowDto(
                p.getReportId() != null ? UUID.fromString(p.getReportId()) : null,
                p.getCodeReport(),
                p.getTestOrderId() != null ? UUID.fromString(p.getTestOrderId()) : null,
                p.getCodeExamen(), p.getTypeExamen(), p.getContractName(),
                p.getPatientId() != null ? UUID.fromString(p.getPatientId()) : null,
                p.getPatientFirstname(), p.getPatientLastname(),
                p.getDoctorId() != null ? UUID.fromString(p.getDoctorId()) : null,
                p.getDoctorName(),
                p.getHospitalId() != null ? UUID.fromString(p.getHospitalId()) : null,
                p.getHospitalName(), p.getReferenceHospital(),
                p.getDateCreation(), p.getIsUrgent())).toList();

        return PageResponse.of(new org.springframework.data.domain.PageImpl<>(rows, pageRequest, result.getTotalElements()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportListDto> getList(
            UUID branchId, int page, int size,
            String search, String statusFilter,
            String dateBegin, String dateEnd) {
        var pageRequest = PageRequest.of(page, size);
        var result = reportRepository.findListRows(
                branchId,
                (search != null && !search.isBlank()) ? search.trim() : null,
                (statusFilter != null && !statusFilter.isBlank()) ? statusFilter : null,
                (dateBegin != null && !dateBegin.isBlank()) ? dateBegin : null,
                (dateEnd != null && !dateEnd.isBlank()) ? dateEnd : null,
                pageRequest);

        var content = result.stream().map(p -> new ReportListDto(
                p.getId() != null ? UUID.fromString(p.getId()) : null,
                p.getReportCode(),
                p.getTestOrderId() != null ? UUID.fromString(p.getTestOrderId()) : null,
                p.getTestOrderCode(),
                p.getPatientId() != null ? UUID.fromString(p.getPatientId()) : null,
                p.getPatientCode(),
                p.getPatientFirstname(),
                p.getPatientLastname(),
                p.getPatientPhone(),
                p.getTypeOrderTitle(),
                p.getStatus() != null ? ReportStatus.valueOf(p.getStatus()) : null,
                p.getIsDelivered(),
                p.getIsCalled(),
                p.getSignatureDate(),
                p.getCreatedAt()
        )).toList();

        return PageResponse.of(new org.springframework.data.domain.PageImpl<>(
                content, pageRequest, result.getTotalElements()));
    }

    @Override
    @Transactional(readOnly = true)
    public ReportPerformanceDto getPerformanceStats(
            UUID branchId, String doctorId, Integer month, Integer year) {
        var stats = reportRepository.getReportPerformanceStats(
                branchId,
                (doctorId != null && !doctorId.isBlank()) ? doctorId : null,
                month, year);
        long total = stats.get("totalReports") != null ? ((Number) stats.get("totalReports")).longValue() : 0L;
        long within = stats.get("withinDeadline") != null ? ((Number) stats.get("withinDeadline")).longValue() : 0L;
        long beyond = stats.get("beyondDeadline") != null ? ((Number) stats.get("beyondDeadline")).longValue() : 0L;
        long denom = within + beyond;
        double pctWithin = denom > 0 ? Math.round((within * 10000.0) / denom) / 100.0 : 0.0;
        double pctBeyond = denom > 0 ? Math.round((beyond * 10000.0) / denom) / 100.0 : 0.0;
        return new ReportPerformanceDto(total, within, beyond, pctWithin, pctBeyond);
    }

    @Override
    @Transactional
    public void logAction(UUID reportId, String action, UUID userId) {
        reportRepository.findById(reportId).ifPresent(report -> {
            LogReport logReport = new LogReport();
            logReport.setBranchId(report.getBranchId());
            logReport.setReport(report);
            logReport.setAction(action);
            logReport.setDescription("Action: " + action + " on report: " + reportId);
            userRepository.findById(userId).ifPresent(logReport::setUser);
            logReportRepository.save(logReport);
        });
    }
}
