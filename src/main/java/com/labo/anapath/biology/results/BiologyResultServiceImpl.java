package com.labo.anapath.biology.results;

import com.labo.anapath.biology.Antibiotic;
import com.labo.anapath.biology.AntibioticRepository;
import com.labo.anapath.biology.BiologyCultureOption;
import com.labo.anapath.biology.BiologyCultureOptionRepository;
import com.labo.anapath.biology.BiologyKind;
import com.labo.anapath.biology.BiologyParameter;
import com.labo.anapath.biology.BiologyParameterRepository;
import com.labo.anapath.biology.BiologyReferenceRange;
import com.labo.anapath.biology.BiologyReferenceRangeRepository;
import com.labo.anapath.biology.BiologySection;
import com.labo.anapath.biology.BiologySectionRepository;
import com.labo.anapath.biology.LabTestCultureOption;
import com.labo.anapath.biology.LabTestCultureOptionRepository;
import com.labo.anapath.biology.ReferenceRangeResolver;
import com.labo.anapath.biology.ResultType;
import com.labo.anapath.common.Discipline;
import com.labo.anapath.common.NomComplet;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.patient.Patient;
import com.labo.anapath.report.Report;
import com.labo.anapath.report.ReportRepository;
import com.labo.anapath.report.ReportStatus;
import com.labo.anapath.test.LabTest;
import com.labo.anapath.test.LabTestRepository;
import com.labo.anapath.test.UnitMeasurement;
import com.labo.anapath.testorder.DetailTestOrder;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.testorder.TestOrderRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implémentation de {@link BiologyResultService}.
 *
 * <h2>Verrous</h2>
 * Une analyse ne se modifie pas :
 * <ul>
 *   <li>quand elle est validée techniquement — il faut d'abord annuler la validation ;</li>
 *   <li>quand le compte-rendu du bon est validé ou livré — la réouverture relève de la
 *       validation biologique (B6).</li>
 * </ul>
 *
 * <h2>État d'une analyse après enregistrement</h2>
 * {@code ENTERED} dès qu'au moins une valeur est enregistrée (paramètre, option de
 * culture ou germe) ; {@code PENDING} si tout a été effacé. La complétude de la fiche
 * n'est pas exigée : aucun paramètre n'est obligatoire au catalogue, et c'est la
 * validation technique qui atteste que l'analyse est complète. La feuille donne
 * {@code expectedValues}/{@code enteredValues} pour que l'écran le signale.
 *
 * <h2>Copies à la saisie</h2>
 * Chaque enregistrement d'une valeur reprend l'unité, les bornes de la plage
 * applicable et le texte de référence du moment ({@code *_snapshot}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BiologyResultServiceImpl implements BiologyResultService {

    static final String ACTION_SAISIE = "Saisie des résultats";
    static final String ACTION_SAISIE_CULTURE = "Saisie de la culture";
    static final String ACTION_VALIDATION_TECHNIQUE = "Validation technique";
    static final String ACTION_ANNULATION_VALIDATION = "Validation technique annulée";

    private static final int TAILLE_MAX_PAGE = 100;
    private static final LocalDateTime DEBUT_DES_TEMPS = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime FIN_DES_TEMPS = LocalDateTime.of(3000, 1, 1, 0, 0);

    private final TestOrderRepository testOrderRepository;
    private final ReportRepository reportRepository;
    private final LabTestRepository labTestRepository;
    private final UserRepository userRepository;
    private final BiologySectionRepository sectionRepository;
    private final BiologyParameterRepository parameterRepository;
    private final BiologyReferenceRangeRepository rangeRepository;
    private final LabTestCultureOptionRepository labTestCultureOptionRepository;
    private final BiologyCultureOptionRepository cultureOptionRepository;
    private final AntibioticRepository antibioticRepository;
    private final BiologyAnalysisResultRepository analysisResultRepository;
    private final BiologyParameterResultRepository parameterResultRepository;
    private final BiologyCultureResultRepository cultureResultRepository;
    private final BiologyIsolateRepository isolateRepository;
    private final BiologyAntibiogramResultRepository antibiogramRepository;
    private final ReglagesDeBiologie reglages;
    private final AvancementDuCompteRendu avancement;

    // ------------------------------------------------------------------ lecture

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<BiologyWorklistRowDto> worklist(String status, UUID categoryId, LocalDate from,
                                                        LocalDate to, int page, int size, UUID branchId) {
        String etat = null;
        if (status != null && !status.isBlank()) {
            try {
                etat = BiologyAnalysisStatus.valueOf(status.strip().toUpperCase(Locale.ROOT)).name();
            } catch (IllegalArgumentException e) {
                throw new BusinessException("État inconnu : « " + status + " » (PENDING, ENTERED ou TECH_VALIDATED).");
            }
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException("La date de début est postérieure à la date de fin.");
        }
        int taille = Math.max(1, Math.min(size, TAILLE_MAX_PAGE));
        Page<BiologyWorklistProjection> lignes = analysisResultRepository.findWorklist(branchId, etat, categoryId,
                from != null ? from.atStartOfDay() : DEBUT_DES_TEMPS,
                to != null ? to.plusDays(1).atStartOfDay() : FIN_DES_TEMPS,
                PageRequest.of(Math.max(0, page), taille));
        return PageResponse.of(lignes.map(BiologyResultServiceImpl::versLigne));
    }

    private static BiologyWorklistRowDto versLigne(BiologyWorklistProjection p) {
        return new BiologyWorklistRowDto(
                p.getId(),
                enumOuNull(BiologyAnalysisStatus.class, p.getStatus()),
                p.getTestOrderId(),
                p.getOrderCode(),
                Boolean.TRUE.equals(p.getUrgent()),
                p.getOrderCreatedAt(),
                p.getPrelevementDate(),
                p.getPatientId(),
                p.getPatientCode(),
                NomComplet.de(p.getPatientLastname(), p.getPatientFirstname()),
                p.getLabTestId(),
                p.getLabTestName(),
                p.getLabTestCode(),
                enumOuNull(BiologyKind.class, p.getBiologyKind()),
                p.getCategoryId(),
                p.getCategoryName(),
                p.getEnteredAt(),
                p.getTechValidatedAt(),
                p.getReportId(),
                enumOuNull(ReportStatus.class, p.getReportStatus()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public BiologyWorksheetDto worksheet(UUID testOrderId, UUID branchId) {
        return construireFeuille(chargerBon(testOrderId, branchId), branchId);
    }

    // ------------------------------------------------------------------ saisie d'une fiche

    /** {@inheritDoc} */
    @Override
    @Transactional
    public BiologyWorksheetDto savePanel(UUID testOrderId, UUID labTestId, BiologyPanelResultsRequestDto dto,
                                         UUID userId, UUID branchId) {
        Contexte ctx = chargerPourEcriture(testOrderId, labTestId, branchId, BiologyKind.PANEL);
        BiologyAnalysisResult ligne = ctx.ligne();

        List<BiologyParameter> parametres = parameterRepository.findByLabTest_IdOrderByPositionAsc(labTestId);
        Map<UUID, BiologyParameter> parId = parId(parametres, BiologyParameter::getId);
        Map<UUID, List<BiologyReferenceRange>> plages = plagesParParametre(parId.keySet());
        Map<UUID, BiologyParameterResult> existantes = new HashMap<>(parId(
                parameterResultRepository.findByAnalysisResult_Id(ligne.getId()), BiologyParameterResult::getParameterId));
        Patient patient = ctx.bon().getPatient();
        String sexe = patient != null ? patient.getGenre() : null;
        Integer age = ageEnJours(ctx.bon());

        Set<UUID> vus = new HashSet<>();
        List<BiologyPanelResultsRequestDto.Value> valeurs = dto.getValues() != null ? dto.getValues() : List.of();
        for (BiologyPanelResultsRequestDto.Value v : valeurs) {
            if (v == null || v.getParameterId() == null) {
                throw new BusinessException("Chaque valeur doit désigner son paramètre.");
            }
            if (!vus.add(v.getParameterId())) {
                throw new BusinessException("Un même paramètre est envoyé deux fois.");
            }
            BiologyParameter p = parId.get(v.getParameterId());
            if (p == null) {
                throw new BusinessException("Ce paramètre n'appartient pas à l'analyse « "
                        + ctx.analyse().getName() + " ».");
            }
            if (v.getValue() == null || v.getValue().isBlank()) {
                BiologyParameterResult effacee = existantes.remove(p.getId());
                if (effacee != null) {
                    parameterResultRepository.delete(effacee);
                }
                continue;
            }
            BiologyParameterResult r = existantes.get(p.getId());
            if (r == null) {
                r = new BiologyParameterResult();
                r.setBranchId(branchId);
                r.setAnalysisResult(ligne);
                r.setParameterId(p.getId());
            }
            BiologyReferenceRange plage = p.getResultType() == ResultType.NUMERIC
                    ? ReferenceRangeResolver.choose(plages.getOrDefault(p.getId(), List.of()), sexe, age).orElse(null)
                    : null;
            remplir(r, p, v.getValue(), v.getFlagOverride(), plage);
            existantes.put(p.getId(), parameterResultRepository.save(r));
        }

        ligne.setComment(texteOuNull(dto.getComment()));
        marquerSaisie(ligne, !existantes.isEmpty(), userId);
        analysisResultRepository.save(ligne);
        avancement.journaliser(ctx.report(), userId, ACTION_SAISIE,
                "Analyse « " + ctx.analyse().getName() + " » : " + existantes.size() + " valeur(s).");
        avancement.recalculer(testOrderId, branchId, userId);
        return construireFeuille(ctx.bon(), branchId);
    }

    /**
     * Valeur, indicateur et copies d'une valeur de paramètre.
     *
     * @throws BusinessException valeur illisible, hors des choix, ou indicateur manuel non permis
     */
    static void remplir(BiologyParameterResult r, BiologyParameter p, String brute, String indicateurManuel,
                        BiologyReferenceRange plage) {
        String saisie = brute.strip();
        ResultType type = p.getResultType() != null ? p.getResultType() : ResultType.NUMERIC;
        switch (type) {
            case NUMERIC -> {
                BigDecimal lu = BiologyNumbers.lire(saisie);
                if (lu == null) {
                    throw new BusinessException("« " + p.getName() + " » : « " + saisie + " » n'est pas un nombre.");
                }
                BigDecimal arrondi = BiologyNumbers.arrondir(lu, p.getDecimals());
                if (!BiologyNumbers.tientEnBase(arrondi)) {
                    throw new BusinessException("« " + p.getName() + " » : « " + saisie + " » est hors limites.");
                }
                r.setValueNumeric(arrondi);
                r.setValueText(arrondi.toPlainString());
            }
            case CHOICE -> {
                r.setValueNumeric(null);
                r.setValueText(parmiLesChoix(saisie, p.getChoices(), p.getName()));
            }
            default -> {
                r.setValueNumeric(null);
                r.setValueText(saisie);
            }
        }
        BiologyFlagCalculator.Indicateur indicateur = BiologyFlagCalculator.determiner(type, p.isFlaggable(),
                r.getValueNumeric(), BiologyFlagCalculator.Bornes.de(plage), indicateurManuel, p.getName());
        r.setFlag(indicateur.flag());
        r.setFlagOverridden(indicateur.overridden());

        r.setUnitSnapshot(unite(p.getUnitMeasurement()));
        r.setLowSnapshot(plage != null ? plage.getLow() : null);
        r.setHighSnapshot(plage != null ? plage.getHigh() : null);
        r.setCriticalLowSnapshot(plage != null ? plage.getCriticalLow() : null);
        r.setCriticalHighSnapshot(plage != null ? plage.getCriticalHigh() : null);
        String intervalle = plage != null ? BiologyNumbers.intervalle(plage.getLow(), plage.getHigh(), p.getDecimals()) : null;
        r.setReferenceSnapshot(intervalle != null ? intervalle : texteOuNull(p.getReferenceText()));
    }

    // ------------------------------------------------------------------ saisie d'une culture

    /** {@inheritDoc} */
    @Override
    @Transactional
    public BiologyWorksheetDto saveCulture(UUID testOrderId, UUID labTestId, BiologyCultureResultsRequestDto dto,
                                           UUID userId, UUID branchId) {
        Contexte ctx = chargerPourEcriture(testOrderId, labTestId, branchId, BiologyKind.CULTURE);
        BiologyAnalysisResult ligne = ctx.ligne();

        // --- Options de culture -------------------------------------------------------
        Map<UUID, BiologyCultureOption> retenues = new LinkedHashMap<>();
        for (LabTestCultureOption l : labTestCultureOptionRepository.findByLabTest_IdOrderByPositionAsc(labTestId)) {
            if (l.getCultureOption() != null) {
                retenues.put(l.getCultureOption().getId(), l.getCultureOption());
            }
        }
        Map<UUID, BiologyCultureResult> valeurs = new HashMap<>(parId(
                cultureResultRepository.findByAnalysisResult_Id(ligne.getId()), BiologyCultureResult::getCultureOptionId));
        if (dto.getOptions() != null) {
            Set<UUID> vues = new HashSet<>();
            for (BiologyCultureResultsRequestDto.OptionValue o : dto.getOptions()) {
                if (o == null || o.getCultureOptionId() == null) {
                    throw new BusinessException("Chaque valeur doit désigner son option de culture.");
                }
                if (!vues.add(o.getCultureOptionId())) {
                    throw new BusinessException("Une même option de culture est envoyée deux fois.");
                }
                BiologyCultureOption option = retenues.get(o.getCultureOptionId());
                if (option == null) {
                    throw new BusinessException("Cette option de culture n'est pas retenue par l'analyse « "
                            + ctx.analyse().getName() + " ».");
                }
                if (o.getValue() == null || o.getValue().isBlank()) {
                    BiologyCultureResult effacee = valeurs.remove(option.getId());
                    if (effacee != null) {
                        cultureResultRepository.delete(effacee);
                    }
                    continue;
                }
                BiologyCultureResult r = valeurs.get(option.getId());
                if (r == null) {
                    r = new BiologyCultureResult();
                    r.setBranchId(branchId);
                    r.setAnalysisResult(ligne);
                    r.setCultureOptionId(option.getId());
                }
                r.setValue(parmiLesChoix(o.getValue().strip(), option.getChoices(), option.getName()));
                valeurs.put(option.getId(), cultureResultRepository.save(r));
            }
        }

        // --- Germes et antibiogrammes ---------------------------------------------------
        List<BiologyIsolate> germes = isolateRepository.findByAnalysisResult_IdOrderByPositionAsc(ligne.getId());
        int nombreDeGermes = germes.size();
        if (dto.getIsolates() != null) {
            nombreDeGermes = enregistrerGermes(ligne, germes, dto.getIsolates(), branchId);
        }

        ligne.setComment(texteOuNull(dto.getComment()));
        marquerSaisie(ligne, !valeurs.isEmpty() || nombreDeGermes > 0, userId);
        analysisResultRepository.save(ligne);
        avancement.journaliser(ctx.report(), userId, ACTION_SAISIE_CULTURE,
                "Analyse « " + ctx.analyse().getName() + " » : " + valeurs.size() + " option(s), "
                        + nombreDeGermes + " germe(s).");
        avancement.recalculer(testOrderId, branchId, userId);
        return construireFeuille(ctx.bon(), branchId);
    }

    /**
     * Remplace les germes d'une culture par la liste demandée.
     *
     * @return le nombre de germes conservés
     */
    private int enregistrerGermes(BiologyAnalysisResult ligne, List<BiologyIsolate> existants,
                                  List<BiologyCultureResultsRequestDto.Isolate> demandes, UUID branchId) {
        Map<UUID, BiologyIsolate> parIdentifiant = parId(existants, BiologyIsolate::getId);
        Map<UUID, List<BiologyAntibiogramResult>> antibiogrammes = parIdentifiant.isEmpty() ? Map.of()
                : antibiogramRepository.findByIsolate_IdIn(parIdentifiant.keySet()).stream()
                        .collect(Collectors.groupingBy(a -> a.getIsolate().getId()));

        // Antibiotiques cités : tous doivent exister dans la succursale.
        Set<UUID> cites = new LinkedHashSet<>();
        for (BiologyCultureResultsRequestDto.Isolate d : demandes) {
            if (d == null) {
                throw new BusinessException("Germe vide.");
            }
            if (d.getAntibiogram() != null) {
                for (BiologyCultureResultsRequestDto.Antibiogram a : d.getAntibiogram()) {
                    if (a == null || a.getAntibioticId() == null) {
                        throw new BusinessException("Chaque ligne d'antibiogramme doit désigner son antibiotique.");
                    }
                    cites.add(a.getAntibioticId());
                }
            }
        }
        Set<UUID> connus = cites.isEmpty() ? Set.of() : antibioticRepository.findAllById(cites).stream()
                .filter(a -> Objects.equals(a.getBranchId(), branchId))
                .map(Antibiotic::getId).collect(Collectors.toSet());
        if (!connus.containsAll(cites)) {
            throw new BusinessException("Un antibiotique de l'antibiogramme est introuvable dans le référentiel.");
        }

        Set<UUID> conserves = new HashSet<>();
        for (int i = 0; i < demandes.size(); i++) {
            BiologyCultureResultsRequestDto.Isolate d = demandes.get(i);
            if (d.getOrganism() == null || d.getOrganism().isBlank()) {
                throw new BusinessException("Le germe isolé est obligatoire.");
            }
            BiologyIsolate germe;
            if (d.getId() != null) {
                germe = parIdentifiant.get(d.getId());
                if (germe == null) {
                    throw new BusinessException("Ce germe n'appartient pas à cette culture.");
                }
                if (!conserves.add(germe.getId())) {
                    throw new BusinessException("Un même germe est envoyé deux fois.");
                }
            } else {
                germe = new BiologyIsolate();
                germe.setBranchId(branchId);
                germe.setAnalysisResult(ligne);
            }
            germe.setOrganism(d.getOrganism().strip());
            germe.setQuantity(texteOuNull(d.getQuantity()));
            germe.setPosition(i);
            germe = isolateRepository.save(germe);
            if (d.getId() == null) {
                conserves.add(germe.getId());
            }
            // Antibiogramme absent : inchangé pour un germe existant, vide pour un nouveau.
            if (d.getAntibiogram() != null) {
                enregistrerAntibiogramme(germe,
                        d.getId() != null ? antibiogrammes.getOrDefault(germe.getId(), List.of()) : List.of(),
                        d.getAntibiogram(), branchId);
            }
        }

        for (BiologyIsolate ancien : existants) {
            if (!conserves.contains(ancien.getId())) {
                antibiogrammes.getOrDefault(ancien.getId(), List.of()).forEach(antibiogramRepository::delete);
                isolateRepository.delete(ancien);
            }
        }
        return demandes.size();
    }

    private void enregistrerAntibiogramme(BiologyIsolate germe, List<BiologyAntibiogramResult> existants,
                                          List<BiologyCultureResultsRequestDto.Antibiogram> demandes, UUID branchId) {
        Map<UUID, BiologyAntibiogramResult> parAntibiotique = new HashMap<>(
                parId(existants, BiologyAntibiogramResult::getAntibioticId));
        Set<UUID> vus = new HashSet<>();
        for (BiologyCultureResultsRequestDto.Antibiogram a : demandes) {
            if (!vus.add(a.getAntibioticId())) {
                throw new BusinessException("Un même antibiotique figure deux fois dans l'antibiogramme de « "
                        + germe.getOrganism() + " ».");
            }
            BiologyAntibiogramResult r = parAntibiotique.get(a.getAntibioticId());
            if (r == null) {
                r = new BiologyAntibiogramResult();
                r.setBranchId(branchId);
                r.setIsolate(germe);
                r.setAntibioticId(a.getAntibioticId());
            }
            r.setInterpretation(interpretation(a.getInterpretation()));
            r.setMic(texteOuNull(a.getMic()));
            r.setDiameterMm(a.getDiameterMm() != null
                    ? a.getDiameterMm().setScale(1, java.math.RoundingMode.HALF_UP) : null);
            antibiogramRepository.save(r);
        }
        for (BiologyAntibiogramResult ancien : existants) {
            if (!vus.contains(ancien.getAntibioticId())) {
                antibiogramRepository.delete(ancien);
            }
        }
    }

    /** S, I ou R (casse indifférente) ; la valeur stockée ne dépend jamais des libellés du laboratoire. */
    static Character interpretation(String brute) {
        String s = brute == null ? "" : brute.strip().toUpperCase(Locale.ROOT);
        if (s.equals("S") || s.equals("I") || s.equals("R")) {
            return s.charAt(0);
        }
        throw new BusinessException("Interprétation « " + brute + " » inconnue : S, I ou R.");
    }

    // ------------------------------------------------------------------ validation technique

    /** {@inheritDoc} */
    @Override
    @Transactional
    public BiologyWorksheetDto validateTechnically(UUID testOrderId, UUID labTestId, UUID userId, UUID branchId) {
        Contexte ctx = chargerPourValidation(testOrderId, labTestId, branchId);
        BiologyAnalysisResult ligne = ctx.ligne();
        // ONE_STEP : saisir suffit, la validation technique n'existe pas ; TECH_VALIDATED : rien à refaire.
        if (reglages.mode(branchId) == BiologyValidationMode.ONE_STEP
                || ligne.getStatus() == BiologyAnalysisStatus.TECH_VALIDATED) {
            return construireFeuille(ctx.bon(), branchId);
        }
        if (ligne.getStatus() == BiologyAnalysisStatus.PENDING) {
            throw new BusinessException("Aucun résultat n'est saisi pour « " + ctx.nomAnalyse()
                    + " » : rien à valider.");
        }
        ligne.setStatus(BiologyAnalysisStatus.TECH_VALIDATED);
        ligne.setTechValidatedBy(userId);
        ligne.setTechValidatedAt(LocalDateTime.now());
        analysisResultRepository.save(ligne);
        avancement.journaliser(ctx.report(), userId, ACTION_VALIDATION_TECHNIQUE,
                "Analyse « " + ctx.nomAnalyse() + " ».");
        avancement.recalculer(testOrderId, branchId, userId);
        return construireFeuille(ctx.bon(), branchId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public BiologyWorksheetDto cancelTechnicalValidation(UUID testOrderId, UUID labTestId, UUID userId,
                                                         UUID branchId) {
        Contexte ctx = chargerPourValidation(testOrderId, labTestId, branchId);
        BiologyAnalysisResult ligne = ctx.ligne();
        // Permise dans les deux modes : une analyse validée avant un passage en ONE_STEP
        // doit rester déverrouillable.
        if (ligne.getStatus() != BiologyAnalysisStatus.TECH_VALIDATED) {
            return construireFeuille(ctx.bon(), branchId);
        }
        ligne.setStatus(BiologyAnalysisStatus.ENTERED);
        ligne.setTechValidatedBy(null);
        ligne.setTechValidatedAt(null);
        analysisResultRepository.save(ligne);
        avancement.journaliser(ctx.report(), userId, ACTION_ANNULATION_VALIDATION,
                "Analyse « " + ctx.nomAnalyse() + " ».");
        avancement.recalculer(testOrderId, branchId, userId);
        return construireFeuille(ctx.bon(), branchId);
    }

    // ------------------------------------------------------------------ chargements et verrous

    /** Ce qu'une écriture sur une analyse du bon a chargé et vérifié. */
    private record Contexte(TestOrder bon, Report report, BiologyAnalysisResult ligne, LabTest analyse,
                            String nomAnalyse) {}

    /**
     * Bon de biologie validé de la succursale.
     *
     * @throws ResourceNotFoundException bon absent de la succursale
     * @throws BusinessException         bon d'anatomie pathologique, ou pas encore validé
     */
    private TestOrder chargerBon(UUID testOrderId, UUID branchId) {
        TestOrder bon = testOrderRepository.findByIdAndBranchId(testOrderId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", testOrderId));
        if (bon.getDiscipline() != Discipline.BIOLOGY) {
            throw new BusinessException("Ce bon d'examen relève de l'anatomie pathologique : "
                    + "il n'a pas de résultats de biologie.");
        }
        if (bon.getCode() == null) {
            throw new BusinessException("Le bon n'est pas encore validé : ses analyses ne sont pas ouvertes à la saisie.");
        }
        return bon;
    }

    private Contexte chargerPourEcriture(UUID testOrderId, UUID labTestId, UUID branchId, BiologyKind nature) {
        Contexte ctx = chargerPourValidation(testOrderId, labTestId, branchId);
        if (ctx.ligne().getStatus() == BiologyAnalysisStatus.TECH_VALIDATED) {
            throw new InvalidOperationException("« " + ctx.nomAnalyse() + " » est validée techniquement : "
                    + "annulez la validation technique pour la modifier.");
        }
        if (ctx.analyse() == null) {
            throw new BusinessException("L'analyse « " + ctx.nomAnalyse() + " » a été retirée du catalogue : "
                    + "ses résultats ne se modifient plus.");
        }
        if (ctx.analyse().getBiologyKind() != nature) {
            throw new BusinessException("L'analyse « " + ctx.nomAnalyse() + " » n'est pas de nature " + nature
                    + (nature == BiologyKind.PANEL ? " : elle se saisit comme une culture."
                    : " : elle se saisit comme une fiche de paramètres."));
        }
        return ctx;
    }

    private Contexte chargerPourValidation(UUID testOrderId, UUID labTestId, UUID branchId) {
        TestOrder bon = chargerBon(testOrderId, branchId);
        BiologyAnalysisResult ligne = analysisResultRepository.findByTestOrderIdAndLabTestId(testOrderId, labTestId)
                .orElseThrow(() -> new ResourceNotFoundException("Cette analyse n'est pas sur le bon d'examen."));
        Report report = reportRepository.findByTestOrderId(testOrderId).orElse(null);
        if (report != null && (report.getStatus() == ReportStatus.VALIDATED
                || report.getStatus() == ReportStatus.DELIVERED)) {
            throw new InvalidOperationException("Le compte-rendu de ce bon est validé : "
                    + "ses résultats ne se modifient plus.");
        }
        LabTest analyse = labTestRepository.findByIdAndBranchId(labTestId, branchId).orElse(null);
        String nom = analyse != null ? analyse.getName() : nomSurLeBon(bon, labTestId);
        return new Contexte(bon, report, ligne, analyse, nom);
    }

    private static String nomSurLeBon(TestOrder bon, UUID labTestId) {
        return bon.getDetails().stream()
                .filter(d -> d.getLabTest() != null && labTestId.equals(d.getLabTest().getId()))
                .map(DetailTestOrder::getTestName).filter(Objects::nonNull).findFirst()
                .orElse("Analyse retirée du catalogue");
    }

    private static void marquerSaisie(BiologyAnalysisResult ligne, boolean saisie, UUID userId) {
        ligne.setStatus(saisie ? BiologyAnalysisStatus.ENTERED : BiologyAnalysisStatus.PENDING);
        ligne.setEnteredBy(saisie ? userId : null);
        ligne.setEnteredAt(saisie ? LocalDateTime.now() : null);
    }

    // ------------------------------------------------------------------ feuille de saisie

    private BiologyWorksheetDto construireFeuille(TestOrder bon, UUID branchId) {
        Patient patient = bon.getPatient();
        Integer ageEnJours = ageEnJours(bon);
        String sexe = patient != null ? patient.getGenre() : null;
        Report report = reportRepository.findByTestOrderId(bon.getId()).orElse(null);
        boolean compteRenduFige = report != null && (report.getStatus() == ReportStatus.VALIDATED
                || report.getStatus() == ReportStatus.DELIVERED);

        // Analyses dans l'ordre du bon, puis celles qui n'y figurent plus.
        Map<UUID, BiologyAnalysisResult> lignes = new LinkedHashMap<>();
        Map<UUID, BiologyAnalysisResult> actives = parId(analysisResultRepository.findByTestOrderId(bon.getId()),
                BiologyAnalysisResult::getLabTestId);
        Map<UUID, String> nomsSurLeBon = new HashMap<>();
        for (DetailTestOrder d : bon.getDetails()) {
            if (d.getLabTest() != null && d.getLabTest().getId() != null) {
                UUID id = d.getLabTest().getId();
                nomsSurLeBon.putIfAbsent(id, d.getTestName());
                if (actives.containsKey(id)) {
                    lignes.putIfAbsent(id, actives.get(id));
                }
            }
        }
        actives.forEach(lignes::putIfAbsent);

        Map<UUID, LabTest> analyses = parId(labTestRepository.findAllById(lignes.keySet()), LabTest::getId);
        List<UUID> idsDeLignes = lignes.values().stream().map(BiologyAnalysisResult::getId).toList();
        Map<UUID, List<BiologyParameterResult>> valeurs = idsDeLignes.isEmpty() ? Map.of()
                : parameterResultRepository.findByAnalysisResult_IdIn(idsDeLignes).stream()
                        .collect(Collectors.groupingBy(r -> r.getAnalysisResult().getId()));
        Map<UUID, List<BiologyCultureResult>> cultures = idsDeLignes.isEmpty() ? Map.of()
                : cultureResultRepository.findByAnalysisResult_IdIn(idsDeLignes).stream()
                        .collect(Collectors.groupingBy(r -> r.getAnalysisResult().getId()));
        List<BiologyIsolate> tousLesGermes = idsDeLignes.isEmpty() ? List.of()
                : isolateRepository.findByAnalysisResult_IdInOrderByPositionAsc(idsDeLignes);
        Map<UUID, List<BiologyIsolate>> germes = tousLesGermes.stream()
                .collect(Collectors.groupingBy(g -> g.getAnalysisResult().getId(), LinkedHashMap::new,
                        Collectors.toList()));
        Map<UUID, List<BiologyAntibiogramResult>> antibiogrammes = tousLesGermes.isEmpty() ? Map.of()
                : antibiogramRepository.findByIsolate_IdIn(tousLesGermes.stream().map(BiologyIsolate::getId).toList())
                        .stream().collect(Collectors.groupingBy(a -> a.getIsolate().getId()));

        boolean avecCulture = analyses.values().stream().anyMatch(a -> a.getBiologyKind() == BiologyKind.CULTURE);
        List<Antibiotic> antibiotiques = avecCulture
                ? antibioticRepository.findByBranchIdOrderByPositionAscNameAsc(branchId) : List.of();
        Map<UUID, Antibiotic> antibiotiquesParId = parId(antibiotiques, Antibiotic::getId);

        Set<UUID> auteurs = new HashSet<>();
        lignes.values().forEach(l -> {
            if (l.getEnteredBy() != null) auteurs.add(l.getEnteredBy());
            if (l.getTechValidatedBy() != null) auteurs.add(l.getTechValidatedBy());
        });
        // HashMap (et non Map.of()) : get(null) doit répondre null pour une analyse jamais saisie.
        Map<UUID, String> noms = new HashMap<>();
        if (!auteurs.isEmpty()) {
            userRepository.findAllById(auteurs).forEach(u ->
                    noms.putIfAbsent(u.getId(), NomComplet.de(u.getLastname(), u.getFirstname())));
        }

        List<BiologyWorksheetDto.Analysis> resultat = new ArrayList<>();
        for (Map.Entry<UUID, BiologyAnalysisResult> e : lignes.entrySet()) {
            BiologyAnalysisResult l = e.getValue();
            LabTest analyse = analyses.get(e.getKey());
            BiologyKind nature = analyse != null ? analyse.getBiologyKind() : null;
            List<BiologyParameterResult> sesValeurs = valeurs.getOrDefault(l.getId(), List.of());
            List<BiologyCultureResult> sesCultures = cultures.getOrDefault(l.getId(), List.of());
            List<BiologyIsolate> sesGermes = germes.getOrDefault(l.getId(), List.of());

            List<BiologyWorksheetDto.Section> sections = new ArrayList<>();
            List<BiologyWorksheetDto.ParameterRow> horsSection = new ArrayList<>();
            List<BiologyWorksheetDto.CultureOptionRow> options = new ArrayList<>();
            List<BiologyWorksheetDto.Isolate> isolats = new ArrayList<>();
            int attendues = 0;
            int renseignees;
            if (nature == BiologyKind.PANEL) {
                attendues = fiche(e.getKey(), sesValeurs, sexe, ageEnJours, sections, horsSection);
                renseignees = sesValeurs.size();
            } else {
                attendues = culture(e.getKey(), sesCultures, options);
                renseignees = sesCultures.size();
                for (BiologyIsolate g : sesGermes) {
                    isolats.add(new BiologyWorksheetDto.Isolate(g.getId(), g.getOrganism(), g.getQuantity(),
                            g.getPosition(), antibiogrammes.getOrDefault(g.getId(), List.of()).stream()
                            .map(a -> versAntibiogramme(a, antibiotiquesParId.get(a.getAntibioticId())))
                            .sorted(java.util.Comparator.comparing(
                                    (BiologyWorksheetDto.Antibiogram a) -> rangAntibiotique(antibiotiquesParId, a))
                                    .thenComparing(a -> a.antibioticName() == null ? "" : a.antibioticName()))
                            .toList()));
                }
            }

            resultat.add(new BiologyWorksheetDto.Analysis(
                    l.getId(), e.getKey(),
                    analyse != null ? analyse.getName() : nomsSurLeBon.getOrDefault(e.getKey(), "Analyse retirée du catalogue"),
                    analyse != null ? analyse.getCode() : null,
                    nature,
                    analyse != null ? analyse.getSpecimenType() : null,
                    l.getStatus(), l.getComment(),
                    l.getEnteredBy(), noms.get(l.getEnteredBy()), l.getEnteredAt(),
                    l.getTechValidatedBy(), noms.get(l.getTechValidatedBy()), l.getTechValidatedAt(),
                    !compteRenduFige && l.getStatus() != BiologyAnalysisStatus.TECH_VALIDATED && analyse != null,
                    attendues, renseignees,
                    sections, horsSection, options, isolats));
        }

        return new BiologyWorksheetDto(
                bon.getId(), bon.getCode(), Boolean.TRUE.equals(bon.getIsUrgent()), bon.getPrelevementDate(),
                versPatient(patient, ageEnJours),
                report != null ? new BiologyWorksheetDto.Report(report.getId(), report.getCode(), report.getStatus()) : null,
                new BiologyWorksheetDto.Settings(reglages.mode(branchId), reglages.libellesAntibiogramme(branchId),
                        reglages.libellesIndicateurs(branchId)),
                resultat,
                antibiotiques.stream().map(a -> new BiologyWorksheetDto.AntibioticRef(a.getId(), a.getName(),
                        a.getCode(), a.getFamily(), a.getPosition())).toList());
    }

    /**
     * Fiche d'une analyse PANEL avec ses valeurs ; les valeurs de paramètres retirés
     * du catalogue sont ajoutées aux paramètres hors section, pour rester visibles.
     *
     * @return le nombre de paramètres de la fiche
     */
    private int fiche(UUID labTestId, List<BiologyParameterResult> sesValeurs, String sexe, Integer age,
                      List<BiologyWorksheetDto.Section> sections, List<BiologyWorksheetDto.ParameterRow> horsSection) {
        List<BiologyParameter> parametres = parameterRepository.findByLabTest_IdOrderByPositionAsc(labTestId);
        Map<UUID, List<BiologyReferenceRange>> plages = plagesParParametre(
                parametres.stream().map(BiologyParameter::getId).toList());
        Map<UUID, BiologyParameterResult> valeurParParametre = parId(sesValeurs, BiologyParameterResult::getParameterId);

        Map<UUID, List<BiologyWorksheetDto.ParameterRow>> parSection = new HashMap<>();
        for (BiologyParameter p : parametres) {
            BiologyReferenceRange plage = p.getResultType() == ResultType.NUMERIC
                    ? ReferenceRangeResolver.choose(plages.getOrDefault(p.getId(), List.of()), sexe, age).orElse(null)
                    : null;
            BiologyWorksheetDto.ParameterRow ligne = versParametre(p, plage, valeurParParametre.get(p.getId()));
            if (p.getSection() != null) {
                parSection.computeIfAbsent(p.getSection().getId(), k -> new ArrayList<>()).add(ligne);
            } else {
                horsSection.add(ligne);
            }
        }
        for (BiologySection s : sectionRepository.findByLabTest_IdOrderByPositionAsc(labTestId)) {
            sections.add(new BiologyWorksheetDto.Section(s.getId(), s.getTitle(), s.getPosition(),
                    parSection.getOrDefault(s.getId(), List.of())));
        }
        Set<UUID> connus = parametres.stream().map(BiologyParameter::getId).collect(Collectors.toSet());
        for (BiologyParameterResult orpheline : sesValeurs) {
            if (!connus.contains(orpheline.getParameterId())) {
                horsSection.add(new BiologyWorksheetDto.ParameterRow(orpheline.getParameterId(), null,
                        "Paramètre retiré du catalogue", Integer.MAX_VALUE,
                        orpheline.getValueNumeric() != null ? ResultType.NUMERIC : ResultType.TEXT,
                        null, null, orpheline.getUnitSnapshot(), null, true, false, null,
                        versValeur(orpheline)));
            }
        }
        return parametres.size();
    }

    /**
     * Options de culture retenues par l'analyse, avec leurs valeurs ; les valeurs
     * d'options qui ne sont plus retenues sont ajoutées à la fin.
     *
     * @return le nombre d'options retenues
     */
    private int culture(UUID labTestId, List<BiologyCultureResult> sesValeurs,
                        List<BiologyWorksheetDto.CultureOptionRow> options) {
        Map<UUID, BiologyCultureResult> parOption = parId(sesValeurs, BiologyCultureResult::getCultureOptionId);
        List<LabTestCultureOption> retenues = labTestCultureOptionRepository.findByLabTest_IdOrderByPositionAsc(labTestId);
        Set<UUID> vues = new HashSet<>();
        for (LabTestCultureOption l : retenues) {
            BiologyCultureOption o = l.getCultureOption();
            if (o == null) continue;
            vues.add(o.getId());
            BiologyCultureResult v = parOption.get(o.getId());
            options.add(new BiologyWorksheetDto.CultureOptionRow(o.getId(), o.getName(), o.getChoices(),
                    l.getPosition(), v != null ? v.getId() : null, v != null ? v.getValue() : null));
        }
        List<BiologyCultureResult> orphelines = sesValeurs.stream()
                .filter(v -> !vues.contains(v.getCultureOptionId())).toList();
        if (!orphelines.isEmpty()) {
            Map<UUID, BiologyCultureOption> connues = parId(cultureOptionRepository.findAllById(
                    orphelines.stream().map(BiologyCultureResult::getCultureOptionId).toList()), BiologyCultureOption::getId);
            for (BiologyCultureResult v : orphelines) {
                BiologyCultureOption o = connues.get(v.getCultureOptionId());
                options.add(new BiologyWorksheetDto.CultureOptionRow(v.getCultureOptionId(),
                        o != null ? o.getName() : "Option retirée du catalogue", null, Integer.MAX_VALUE,
                        v.getId(), v.getValue()));
            }
        }
        return retenues.size();
    }

    private static BiologyWorksheetDto.ParameterRow versParametre(BiologyParameter p, BiologyReferenceRange plage,
                                                                  BiologyParameterResult valeur) {
        return new BiologyWorksheetDto.ParameterRow(
                p.getId(), p.getCode(), p.getName(), p.getPosition(), p.getResultType(), p.getChoices(),
                p.getDecimals(), unite(p.getUnitMeasurement()), p.getReferenceText(), p.isPrintable(),
                p.isFlaggable(),
                plage == null ? null : new BiologyWorksheetDto.AppliedRange(plage.getId(), plage.getLow(),
                        plage.getHigh(), plage.getCriticalLow(), plage.getCriticalHigh(), plage.getLabel(),
                        BiologyNumbers.intervalle(plage.getLow(), plage.getHigh(), p.getDecimals())),
                valeur == null ? null : versValeur(valeur));
    }

    private static BiologyWorksheetDto.ParameterValue versValeur(BiologyParameterResult v) {
        return new BiologyWorksheetDto.ParameterValue(v.getId(), v.getValueText(), v.getValueNumeric(), v.getFlag(),
                v.isFlagOverridden(), v.getUnitSnapshot(), v.getLowSnapshot(), v.getHighSnapshot(),
                v.getCriticalLowSnapshot(), v.getCriticalHighSnapshot(), v.getReferenceSnapshot());
    }

    private static BiologyWorksheetDto.Antibiogram versAntibiogramme(BiologyAntibiogramResult a, Antibiotic ab) {
        return new BiologyWorksheetDto.Antibiogram(a.getId(), a.getAntibioticId(),
                ab != null ? ab.getName() : null, ab != null ? ab.getCode() : null,
                a.getInterpretation() != null ? String.valueOf(a.getInterpretation()) : null,
                a.getMic(), a.getDiameterMm());
    }

    private static int rangAntibiotique(Map<UUID, Antibiotic> catalogue, BiologyWorksheetDto.Antibiogram a) {
        Antibiotic ab = catalogue.get(a.antibioticId());
        return ab != null ? ab.getPosition() : Integer.MAX_VALUE;
    }

    private static BiologyWorksheetDto.Patient versPatient(Patient p, Integer ageEnJours) {
        if (p == null) {
            return null;
        }
        Character sexe = ReferenceRangeResolver.normaliserSexe(p.getGenre());
        return new BiologyWorksheetDto.Patient(p.getId(), p.getCode(), p.getFirstname(), p.getLastname(),
                NomComplet.de(p.getLastname(), p.getFirstname()), p.getGenre(),
                sexe != null ? String.valueOf(sexe) : null, p.getBirthday(), p.getAge(),
                p.getAge() == null ? null : Boolean.FALSE.equals(p.getYearOrMonth()) ? "MONTHS" : "YEARS",
                ageEnJours);
    }

    // ------------------------------------------------------------------ utilitaires

    private static Integer ageEnJours(TestOrder bon) {
        Patient p = bon.getPatient();
        return p == null ? null
                : AgeDuPatient.enJours(p.getBirthday(), p.getAge(), p.getYearOrMonth(), bon.getPrelevementDate());
    }

    private Map<UUID, List<BiologyReferenceRange>> plagesParParametre(Collection<UUID> parametres) {
        if (parametres.isEmpty()) {
            return Map.of();
        }
        return rangeRepository.findByParameter_IdInOrderByPositionAsc(parametres).stream()
                .collect(Collectors.groupingBy(r -> r.getParameter().getId()));
    }

    /**
     * Valeur admise pour une liste de choix : la forme du catalogue, casse et espaces
     * indifférents. Sans choix au catalogue, la saisie est libre.
     */
    static String parmiLesChoix(String saisie, List<String> choix, String libelle) {
        if (choix == null || choix.isEmpty()) {
            return saisie;
        }
        return choix.stream().filter(c -> c != null && c.strip().equalsIgnoreCase(saisie.strip()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("« " + libelle + " » : « " + saisie
                        + " » ne fait pas partie des choix proposés."));
    }

    private static String unite(UnitMeasurement u) {
        if (u == null) {
            return null;
        }
        return u.getAbbreviation() != null && !u.getAbbreviation().isBlank() ? u.getAbbreviation() : u.getName();
    }

    private static String texteOuNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static <E extends Enum<E>> E enumOuNull(Class<E> type, String valeur) {
        if (valeur == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, valeur);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <T> Map<UUID, T> parId(Collection<T> elements, Function<T, UUID> cle) {
        Map<UUID, T> m = new LinkedHashMap<>();
        for (T e : elements) {
            if (e != null && cle.apply(e) != null) {
                m.putIfAbsent(cle.apply(e), e);
            }
        }
        return m;
    }
}
