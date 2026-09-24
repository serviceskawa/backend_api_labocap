package com.labo.anapath.biology;

import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.test.LabTest;
import com.labo.anapath.test.LabTestRepository;
import com.labo.anapath.test.UnitMeasurement;
import com.labo.anapath.test.UnitMeasurementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implémentation de {@link BiologySheetService}.
 *
 * <h2>Ordre des écritures</h2>
 * Hibernate exécute, à la purge, les insertions avant les mises à jour et les
 * suppressions. Or le code d'un paramètre est unique parmi les lignes vivantes
 * d'une analyse : recréer « HB » après avoir supprimé l'ancien « HB », ou échanger
 * les codes de deux paramètres, violerait l'index si l'on laissait faire cet ordre.
 * L'enregistrement procède donc par étapes purgées une à une :
 * <ol>
 *   <li>suppressions (plages, paramètres, sections) ;</li>
 *   <li>libération des codes qui changent (mis à {@code null}) ;</li>
 *   <li>mises à jour et créations.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BiologySheetServiceImpl implements BiologySheetService {

    private final LabTestRepository labTestRepository;
    private final BiologySectionRepository sectionRepository;
    private final BiologyParameterRepository parameterRepository;
    private final BiologyReferenceRangeRepository rangeRepository;
    private final UnitMeasurementRepository unitMeasurementRepository;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public BiologySheetResponseDto getSheet(UUID labTestId, UUID branchId) {
        LabTest analyse = AnalysesDeBiologie.charger(labTestRepository, labTestId, branchId, null);
        return construireFiche(analyse);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public BiologySheetResponseDto saveSheet(UUID labTestId, BiologySheetRequestDto fiche, UUID branchId) {
        LabTest analyse = AnalysesDeBiologie.charger(labTestRepository, labTestId, branchId, BiologyKind.PANEL);
        BiologySheetValidator.validerEtNormaliser(fiche);

        // --- État actuel ------------------------------------------------------------
        Map<UUID, BiologySection> sections = parId(sectionRepository.findByLabTest_IdOrderByPositionAsc(labTestId),
                BiologySection::getId);
        Map<UUID, BiologyParameter> parametres = parId(
                parameterRepository.findByLabTest_IdOrderByPositionAsc(labTestId), BiologyParameter::getId);
        Map<UUID, BiologyReferenceRange> plages = parametres.isEmpty() ? new HashMap<>()
                : parId(rangeRepository.findByParameter_IdInOrderByPositionAsc(parametres.keySet()),
                        BiologyReferenceRange::getId);

        // --- Ce que la requête conserve ---------------------------------------------
        List<BiologySheetRequestDto.SectionRequest> sectionsDemandees = nonNul(fiche.getSections());
        List<ParametreDemande> parametresDemandes = new ArrayList<>();
        for (BiologySheetRequestDto.SectionRequest s : sectionsDemandees) {
            exigerAppartenance(sections, s.getId(), "section");
            List<BiologySheetRequestDto.ParameterRequest> ps = nonNul(s.getParameters());
            for (int i = 0; i < ps.size(); i++) {
                parametresDemandes.add(new ParametreDemande(ps.get(i), s, i));
            }
        }
        List<BiologySheetRequestDto.ParameterRequest> horsSection = nonNul(fiche.getParameters());
        for (int i = 0; i < horsSection.size(); i++) {
            parametresDemandes.add(new ParametreDemande(horsSection.get(i), null, i));
        }
        for (ParametreDemande pd : parametresDemandes) {
            exigerAppartenance(parametres, pd.requete().getId(), "paramètre « " + pd.requete().getName() + " »");
            for (BiologySheetRequestDto.RangeRequest r : nonNul(pd.requete().getRanges())) {
                exigerAppartenance(plages, r.getId(), "plage de référence");
                BiologyReferenceRange existante = r.getId() == null ? null : plages.get(r.getId());
                if (existante != null && !Objects.equals(existante.getParameter().getId(), pd.requete().getId())) {
                    throw new BusinessException("Une plage de référence ne peut pas changer de paramètre.");
                }
            }
        }

        // --- 1. Suppressions ----------------------------------------------------------
        List<UUID> plagesGardees = parametresDemandes.stream()
                .flatMap(pd -> nonNul(pd.requete().getRanges()).stream())
                .map(BiologySheetRequestDto.RangeRequest::getId).filter(Objects::nonNull).toList();
        List<UUID> parametresGardes = parametresDemandes.stream()
                .map(pd -> pd.requete().getId()).filter(Objects::nonNull).toList();
        List<UUID> sectionsGardees = sectionsDemandees.stream()
                .map(BiologySheetRequestDto.SectionRequest::getId).filter(Objects::nonNull).toList();

        List<BiologyReferenceRange> plagesRetirees = retirees(plages, plagesGardees);
        List<BiologyParameter> parametresRetires = retirees(parametres, parametresGardes);
        List<BiologySection> sectionsRetirees = retirees(sections, sectionsGardees);
        rangeRepository.deleteAll(plagesRetirees);
        parameterRepository.deleteAll(parametresRetires);
        sectionRepository.deleteAll(sectionsRetirees);
        parametresRetires.forEach(p -> parametres.remove(p.getId()));

        // --- 2. Libération des codes qui changent ------------------------------------
        boolean codesLiberes = false;
        for (ParametreDemande pd : parametresDemandes) {
            BiologyParameter existant = pd.requete().getId() == null ? null : parametres.get(pd.requete().getId());
            if (existant != null && existant.getCode() != null
                    && !existant.getCode().equalsIgnoreCase(Objects.requireNonNullElse(pd.requete().getCode(), ""))) {
                existant.setCode(null);
                codesLiberes = true;
            }
        }
        if (!plagesRetirees.isEmpty() || !parametresRetires.isEmpty() || !sectionsRetirees.isEmpty() || codesLiberes) {
            parameterRepository.flush();
        }

        // --- 3. Sections ---------------------------------------------------------------
        Map<BiologySheetRequestDto.SectionRequest, BiologySection> sectionParRequete = new HashMap<>();
        for (int i = 0; i < sectionsDemandees.size(); i++) {
            BiologySheetRequestDto.SectionRequest s = sectionsDemandees.get(i);
            BiologySection section = s.getId() != null ? sections.get(s.getId()) : nouvelleSection(analyse, branchId);
            section.setTitle(s.getTitle().trim());
            section.setPosition(i);
            sectionParRequete.put(s, sectionRepository.save(section));
        }

        // --- 4. Paramètres (existants d'abord, puis nouveaux) et leurs plages --------
        Map<UUID, UnitMeasurement> unites = new HashMap<>();
        List<ParametreDemande> existants = parametresDemandes.stream().filter(pd -> pd.requete().getId() != null).toList();
        List<ParametreDemande> nouveaux = parametresDemandes.stream().filter(pd -> pd.requete().getId() == null).toList();
        for (ParametreDemande pd : existants) {
            BiologyParameter p = parametres.get(pd.requete().getId());
            appliquer(p, pd, sectionParRequete, unites);
        }
        if (!existants.isEmpty() && !nouveaux.isEmpty()) {
            parameterRepository.flush();
        }
        List<ParametreDemande> dansLOrdre = new ArrayList<>(existants);
        dansLOrdre.addAll(nouveaux);
        for (ParametreDemande pd : nouveaux) {
            BiologyParameter p = new BiologyParameter();
            p.setLabTest(analyse);
            p.setBranchId(branchId);
            appliquer(p, pd, sectionParRequete, unites);
        }
        for (ParametreDemande pd : dansLOrdre) {
            enregistrerPlages(pd.entite(), nonNul(pd.requete().getRanges()), plages, branchId);
        }

        log.info("Fiche de paramètres enregistrée pour l'analyse {} : {} section(s), {} paramètre(s)",
                labTestId, sectionsDemandees.size(), parametresDemandes.size());
        return construireFiche(analyse);
    }

    // ------------------------------------------------------------------ écriture

    private void appliquer(BiologyParameter p, ParametreDemande pd,
                           Map<BiologySheetRequestDto.SectionRequest, BiologySection> sectionParRequete,
                           Map<UUID, UnitMeasurement> unites) {
        BiologySheetRequestDto.ParameterRequest r = pd.requete();
        p.setSection(pd.section() == null ? null : sectionParRequete.get(pd.section()));
        p.setCode(r.getCode());
        p.setName(r.getName().trim());
        p.setPosition(pd.rang());
        p.setResultType(r.getResultType());
        p.setChoices(r.getChoices());
        p.setDecimals(r.getResultType() == ResultType.NUMERIC ? r.getDecimals() : null);
        p.setUnitMeasurement(r.getUnitMeasurementId() == null ? null
                : unites.computeIfAbsent(r.getUnitMeasurementId(), id -> unitMeasurementRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Unité", id))));
        p.setReferenceText(r.getReferenceText() == null || r.getReferenceText().isBlank()
                ? null : r.getReferenceText().trim());
        p.setPrintable(r.getPrintable() == null || r.getPrintable());
        p.setFlaggable(r.getFlaggable() == null || r.getFlaggable());
        pd.entite(parameterRepository.save(p));
    }

    private void enregistrerPlages(BiologyParameter parametre, List<BiologySheetRequestDto.RangeRequest> demandes,
                                   Map<UUID, BiologyReferenceRange> existantes, UUID branchId) {
        for (int i = 0; i < demandes.size(); i++) {
            BiologySheetRequestDto.RangeRequest r = demandes.get(i);
            BiologyReferenceRange plage;
            if (r.getId() != null) {
                plage = existantes.get(r.getId());
            } else {
                plage = new BiologyReferenceRange();
                plage.setParameter(parametre);
                plage.setBranchId(branchId);
            }
            plage.setSex(r.getSex() == null ? null : r.getSex().charAt(0));
            plage.setAgeMinDays(r.getAgeMinDays());
            plage.setAgeMaxDays(r.getAgeMaxDays());
            plage.setLow(r.getLow());
            plage.setHigh(r.getHigh());
            plage.setCriticalLow(r.getCriticalLow());
            plage.setCriticalHigh(r.getCriticalHigh());
            plage.setLabel(r.getLabel() == null || r.getLabel().isBlank() ? null : r.getLabel().trim());
            plage.setPosition(i);
            rangeRepository.save(plage);
        }
    }

    private static BiologySection nouvelleSection(LabTest analyse, UUID branchId) {
        BiologySection section = new BiologySection();
        section.setLabTest(analyse);
        section.setBranchId(branchId);
        return section;
    }

    // ------------------------------------------------------------------ lecture

    private BiologySheetResponseDto construireFiche(LabTest analyse) {
        List<BiologySection> sections = sectionRepository.findByLabTest_IdOrderByPositionAsc(analyse.getId());
        List<BiologyParameter> parametres = parameterRepository.findByLabTest_IdOrderByPositionAsc(analyse.getId());
        Map<UUID, List<BiologySheetResponseDto.Range>> plagesParParametre = parametres.isEmpty() ? Map.of()
                : rangeRepository.findByParameter_IdInOrderByPositionAsc(
                                parametres.stream().map(BiologyParameter::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(r -> r.getParameter().getId(), LinkedHashMap::new,
                                Collectors.mapping(BiologySheetServiceImpl::versDto, Collectors.toList())));

        Map<UUID, List<BiologySheetResponseDto.Parameter>> parSection = new LinkedHashMap<>();
        sections.forEach(s -> parSection.put(s.getId(), new ArrayList<>()));
        List<BiologySheetResponseDto.Parameter> horsSection = new ArrayList<>();
        for (BiologyParameter p : parametres) {
            BiologySheetResponseDto.Parameter dto = versDto(p, plagesParParametre.getOrDefault(p.getId(), List.of()));
            List<BiologySheetResponseDto.Parameter> cible = dto.sectionId() == null ? null : parSection.get(dto.sectionId());
            // Un paramètre dont la section a été supprimée retombe hors section plutôt que de disparaître.
            (cible != null ? cible : horsSection).add(dto);
        }
        List<BiologySheetResponseDto.Section> sectionsDto = sections.stream()
                .map(s -> new BiologySheetResponseDto.Section(s.getId(), s.getTitle(), s.getPosition(),
                        parSection.get(s.getId())))
                .toList();
        return new BiologySheetResponseDto(analyse.getId(), analyse.getName(), sectionsDto, horsSection);
    }

    private static BiologySheetResponseDto.Parameter versDto(BiologyParameter p,
                                                             List<BiologySheetResponseDto.Range> plages) {
        UnitMeasurement unite = p.getUnitMeasurement();
        return new BiologySheetResponseDto.Parameter(
                p.getId(),
                p.getSection() == null ? null : p.getSection().getId(),
                p.getCode(),
                p.getName(),
                p.getPosition(),
                p.getResultType(),
                p.getChoices(),
                p.getDecimals(),
                unite == null ? null : unite.getId(),
                unite == null ? null : unite.getName(),
                unite == null ? null : unite.getAbbreviation(),
                p.getReferenceText(),
                p.isPrintable(),
                p.isFlaggable(),
                plages);
    }

    private static BiologySheetResponseDto.Range versDto(BiologyReferenceRange r) {
        return new BiologySheetResponseDto.Range(
                r.getId(),
                r.getSex() == null ? null : String.valueOf(r.getSex()),
                r.getAgeMinDays(),
                r.getAgeMaxDays(),
                r.getLow(),
                r.getHigh(),
                r.getCriticalLow(),
                r.getCriticalHigh(),
                r.getLabel(),
                r.getPosition());
    }

    // ------------------------------------------------------------------ outils

    private static <T> Map<UUID, T> parId(Collection<T> elements, Function<T, UUID> id) {
        Map<UUID, T> map = new LinkedHashMap<>();
        elements.forEach(e -> map.put(id.apply(e), e));
        return map;
    }

    private static void exigerAppartenance(Map<UUID, ?> existants, UUID id, String quoi) {
        if (id != null && !existants.containsKey(id)) {
            throw new BusinessException("L'élément " + quoi + " (" + id + ") n'appartient pas à cette analyse.");
        }
    }

    private static <T> List<T> retirees(Map<UUID, T> existants, Collection<UUID> gardes) {
        return existants.entrySet().stream()
                .filter(e -> !gardes.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }

    private static <T> List<T> nonNul(List<T> liste) {
        return Objects.requireNonNullElse(liste, List.of());
    }

    /** Paramètre demandé, avec sa section d'origine dans la requête et son rang. */
    private static final class ParametreDemande {
        private final BiologySheetRequestDto.ParameterRequest requete;
        private final BiologySheetRequestDto.SectionRequest section;
        private final int rang;
        private BiologyParameter entite;

        ParametreDemande(BiologySheetRequestDto.ParameterRequest requete,
                         BiologySheetRequestDto.SectionRequest section, int rang) {
            this.requete = requete;
            this.section = section;
            this.rang = rang;
        }

        BiologySheetRequestDto.ParameterRequest requete() { return requete; }
        BiologySheetRequestDto.SectionRequest section() { return section; }
        int rang() { return rang; }
        BiologyParameter entite() { return entite; }
        void entite(BiologyParameter e) { this.entite = e; }
    }
}
