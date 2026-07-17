package com.labo.anapath.report;

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
                        l.getUser() != null ? l.getUser().getFirstname() + " " + l.getUser().getLastname() : null,
                        l.getCreatedAt()))
                .toList();

        String patientName = null;
        if (report.getTestOrder() != null && report.getTestOrder().getPatient() != null) {
            var p = report.getTestOrder().getPatient();
            patientName = p.getFirstname() + " " + p.getLastname();
        }

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
                report.getSignatureDate(), report.getDeliveryDate(), report.getCallDate(),
                report.getSignatory1() != null ? report.getSignatory1().getId() : null,
                report.getSignatory1() != null ? report.getSignatory1().getFirstname() + " " + report.getSignatory1().getLastname() : null,
                report.getSignatory2() != null ? report.getSignatory2().getId() : null,
                report.getSignatory2() != null ? report.getSignatory2().getFirstname() + " " + report.getSignatory2().getLastname() : null,
                report.getSignatory3() != null ? report.getSignatory3().getId() : null,
                report.getSignatory3() != null ? report.getSignatory3().getFirstname() + " " + report.getSignatory3().getLastname() : null,
                report.getReviewedBy() != null ? report.getReviewedBy().getId() : null,
                report.getReviewedBy() != null ? report.getReviewedBy().getFirstname() + " " + report.getReviewedBy().getLastname() : null,
                report.getTags().stream().map(Tag::getName).toList(),
                report.getTags().stream().map(Tag::getId).toList(),
                logDtos,
                report.getCreatedAt(), report.getUpdatedAt());
    }

    @Override
    @Transactional
    public ReportResponseDto createOrUpdate(ReportRequestDto dto, UUID branchId) {
        Report report;
        boolean isCreate = dto.getReportId() == null;

        if (!isCreate) {
            report = reportRepository.findById(dto.getReportId())
                    .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", dto.getReportId()));
            if (report.getStatus() == ReportStatus.DELIVERED) {
                throw new InvalidOperationException("Impossible de modifier un rapport livré.");
            }
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
                                    ? (user.getFirstname() + " " + user.getLastname()).trim()
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
        String reviewerName = (reviewer.getFirstname() + " " + reviewer.getLastname()).trim();
        String reportTitle = report.getTitleReport() != null
                ? report.getTitleReport().getName()
                : report.getCode();
        String orderCode = report.getTestOrder() != null ? report.getTestOrder().getCode() : "";
        String labName = notificationSettings.labName(report.getBranchId());
        emailService.sendAssignedReview(reviewer.getEmail(), reviewerName, reportTitle, orderCode, labName);
    }

    /**
     * Met à jour le contenu textuel et le commentaire d'un compte-rendu.
     * Un CR au statut DELIVERED ne peut plus être modifié.
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
        if (report.getStatus() == ReportStatus.DELIVERED) {
            throw new InvalidOperationException("Impossible de modifier un compte-rendu déjà livré.");
        }

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

        return reportMapper.toResponseDto(saved);
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
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", id));
        if (report.getStatus() == ReportStatus.VALIDATED || report.getStatus() == ReportStatus.DELIVERED) {
            throw new InvalidOperationException("Le rapport est déjà validé ou livré.");
        }
        report.setStatus(ReportStatus.VALIDATED);
        report.setSignatureDate(LocalDateTime.now());
        report.setDeliveryDate(LocalDateTime.now());
        Report saved = reportRepository.save(report);
        logAction(id, "Validé", userId);
        return reportMapper.toResponseDto(saved);
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
        report.setRetrieverSignature(dto.getSignature());
        Report saved = reportRepository.save(report);
        logAction(id, "Signature enregistrée", userId);
        return reportMapper.toResponseDto(saved);
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
