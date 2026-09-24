package com.labo.anapath.biology.report;

import com.labo.anapath.biology.results.AvancementDuCompteRendu;
import com.labo.anapath.biology.results.BiologyAnalysisResult;
import com.labo.anapath.biology.results.BiologyAnalysisResultRepository;
import com.labo.anapath.biology.results.BiologyAnalysisStatus;
import com.labo.anapath.biology.results.BiologyValidationMode;
import com.labo.anapath.biology.results.ReglagesDeBiologie;
import com.labo.anapath.common.Discipline;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.report.Report;
import com.labo.anapath.report.ReportMapper;
import com.labo.anapath.report.ReportRepository;
import com.labo.anapath.report.ReportResponseDto;
import com.labo.anapath.report.ReportService;
import com.labo.anapath.report.ReportStatus;
import com.labo.anapath.report.ValidationSigneeDto;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implémentation de {@link BiologyReportService}.
 *
 * <h2>Validation</h2>
 * Contrôles propres à la biologie (discipline, succursale, compte-rendu prêt), puis
 * le cœur commun {@code ReportService.validerCompteRendu} : c'est lui qui pose
 * VALIDATED et la date de signature, vérifie la preuve d'appareil, journalise et
 * publie {@code ReportValidatedEvent} — une seule fois, à la transition. Rien ici
 * ne publie d'événement.
 *
 * <p>Le biologiste qui valide devient {@code signatory1} : c'est sa signature qui
 * figure sur le PDF, comme celle du signataire d'un compte-rendu d'anatomie
 * pathologique. Le bon n'est en revanche pas « affecté » au biologiste
 * ({@code assigned_to_user_id}) : ce champ alimente la file du pathologiste.</p>
 *
 * <h2>Réouverture</h2>
 * VALIDATED → DRAFT, jamais depuis DELIVERED (le document est entre les mains du
 * patient). La signature est effacée — signataire, date de signature, date posée
 * par la validation dans {@code delivery_date}, preuve d'appareil — puisqu'elle
 * ne vaut plus pour le contenu qui va changer. Les analyses <b>gardent leur
 * état</b> : la réouverture lève le verrou du compte-rendu, pas la validation
 * technique ; corriger une valeur validée techniquement passe, comme avant, par
 * l'annulation de cette validation. L'état est aussitôt recalculé : un bon
 * toujours prêt revient en {@code PENDING_REVIEW}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BiologyReportServiceImpl implements BiologyReportService {

    static final String ACTION_REOUVERTURE = "Réouverture du compte-rendu de biologie";
    static final String ACTION_CONCLUSION = "Conclusion du compte-rendu de biologie";

    private final ReportRepository reportRepository;
    private final ReportService reportService;
    private final ReportMapper reportMapper;
    private final UserRepository userRepository;
    private final BiologyAnalysisResultRepository analysisResultRepository;
    private final ReglagesDeBiologie reglages;
    private final AvancementDuCompteRendu avancement;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReportResponseDto validate(UUID reportId, UUID userId, UUID branchId, ValidationSigneeDto preuve) {
        Report report = charger(reportId, branchId);
        if (estValideOuLivre(report)) {
            throw new InvalidOperationException("Le compte-rendu est déjà validé ou livré.");
        }
        if (report.getStatus() != ReportStatus.PENDING_REVIEW) {
            throw new BusinessException("Le compte-rendu n'est pas prêt pour la validation biologique : "
                    + "toutes les analyses du bon doivent d'abord être prêtes.");
        }
        // Contrôle refait ici plutôt que cru sur le statut : le mode de validation a
        // pu changer depuis le passage en relecture (ONE_STEP → TWO_STEP).
        BiologyValidationMode mode = reglages.mode(report.getBranchId());
        List<BiologyAnalysisStatus> statuts = analysisResultRepository
                .findByTestOrderId(report.getTestOrder().getId()).stream()
                .map(BiologyAnalysisResult::getStatus).toList();
        if (!AvancementDuCompteRendu.pret(statuts, mode)) {
            throw new BusinessException(mode == BiologyValidationMode.TWO_STEP
                    ? "Toutes les analyses du bon doivent être validées techniquement avant la validation biologique."
                    : "Toutes les analyses du bon doivent être saisies avant la validation biologique.");
        }

        User biologiste = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));
        report.setSignatory1(biologiste);
        return reportService.validerCompteRendu(report, userId, preuve);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReportResponseDto reopen(UUID reportId, UUID userId, UUID branchId) {
        Report report = charger(reportId, branchId);
        if (report.getStatus() == ReportStatus.DELIVERED || report.isDelivered()) {
            throw new BusinessException("Le compte-rendu a été remis au patient : il ne peut plus être rouvert.");
        }
        if (report.getStatus() != ReportStatus.VALIDATED) {
            throw new BusinessException("Seul un compte-rendu validé peut être rouvert.");
        }

        String signataire = report.getSignatory1() != null
                ? com.labo.anapath.common.NomComplet.de(report.getSignatory1().getLastname(),
                        report.getSignatory1().getFirstname())
                : null;
        report.setStatus(ReportStatus.DRAFT);
        report.setSignatory1(null);
        report.setSignatureDate(null);
        // Posée par la validation (cœur commun) et non par une remise : un
        // compte-rendu rouvert n'a été ni signé ni remis.
        report.setDeliveryDate(null);
        report.setSigningDeviceId(null);
        report.setDeviceSignature(null);
        report.setDeviceSignedAt(null);
        reportRepository.save(report);

        avancement.journaliser(report, userId, ACTION_REOUVERTURE,
                signataire != null ? "Validation de " + signataire + " annulée." : "Validation annulée.");
        log.info("Compte-rendu de biologie {} rouvert", report.getId());

        // Les analyses gardent leur état : un bon toujours prêt revient en relecture.
        avancement.recalculer(report.getTestOrder().getId(), report.getBranchId(), userId);
        return reportMapper.toResponseDto(report);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ReportResponseDto updateConclusion(UUID reportId, String conclusion, UUID userId, UUID branchId) {
        Report report = charger(reportId, branchId);
        if (estValideOuLivre(report)) {
            throw new InvalidOperationException("Le compte-rendu est validé : rouvrez-le pour modifier sa conclusion.");
        }
        String texte = conclusion == null || conclusion.isBlank() ? null : conclusion.strip();
        report.setComment(texte);
        reportRepository.save(report);
        avancement.journaliser(report, userId, ACTION_CONCLUSION,
                texte == null ? "Conclusion effacée." : "Conclusion enregistrée.");
        return reportMapper.toResponseDto(report);
    }

    /**
     * Compte-rendu de biologie de la succursale.
     *
     * @throws ResourceNotFoundException absent, ou d'une autre succursale
     * @throws BusinessException         compte-rendu d'anatomie pathologique
     */
    private Report charger(UUID reportId, UUID branchId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", reportId));
        if (branchId != null && !branchId.equals(report.getBranchId())) {
            throw new ResourceNotFoundException("Compte-rendu", reportId);
        }
        if (report.getDiscipline() != Discipline.BIOLOGY) {
            throw new BusinessException("Ce compte-rendu relève de l'anatomie pathologique : "
                    + "il se valide depuis ses propres écrans.");
        }
        if (report.getTestOrder() == null) {
            throw new BusinessException("Ce compte-rendu n'est rattaché à aucun bon d'examen.");
        }
        return report;
    }

    private static boolean estValideOuLivre(Report report) {
        return report.getStatus() == ReportStatus.VALIDATED || report.getStatus() == ReportStatus.DELIVERED;
    }
}
