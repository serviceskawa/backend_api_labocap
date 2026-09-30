package com.labo.anapath.biology;

import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.DuplicateResourceException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.test.LabTest;
import com.labo.anapath.test.LabTestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implémentation de {@link CultureOptionService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CultureOptionServiceImpl implements CultureOptionService {

    private final BiologyCultureOptionRepository optionRepository;
    private final LabTestCultureOptionRepository linkRepository;
    private final LabTestRepository labTestRepository;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<CultureOptionResponseDto> findAll(UUID branchId) {
        return optionRepository.findByBranchIdOrderByPositionAscNameAsc(branchId).stream()
                .map(CultureOptionResponseDto::of).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CultureOptionResponseDto create(CultureOptionRequestDto dto, UUID branchId) {
        String nom = dto.getName().trim();
        if (optionRepository.existsByNameIgnoreCaseAndBranchId(nom, branchId)) {
            throw new DuplicateResourceException("Une option de culture « " + nom + " » existe déjà.");
        }
        BiologyCultureOption o = new BiologyCultureOption();
        o.setBranchId(branchId);
        appliquer(o, dto, nom);
        BiologyCultureOption enregistree = optionRepository.save(o);
        log.info("Option de culture créée : {}", enregistree.getId());
        return CultureOptionResponseDto.of(enregistree);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CultureOptionResponseDto update(UUID id, CultureOptionRequestDto dto, UUID branchId) {
        BiologyCultureOption o = optionRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Option de culture", id));
        String nom = dto.getName().trim();
        if (optionRepository.existsByNameIgnoreCaseAndBranchIdAndIdNot(nom, branchId, id)) {
            throw new DuplicateResourceException("Une option de culture « " + nom + " » existe déjà.");
        }
        appliquer(o, dto, nom);
        return CultureOptionResponseDto.of(optionRepository.save(o));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Les rattachements sont retirés avec l'option : une analyse ne doit pas
     * continuer à proposer une rubrique qui n'existe plus au référentiel.</p>
     */
    @Override
    @Transactional
    public void delete(UUID id, UUID branchId) {
        BiologyCultureOption o = optionRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Option de culture", id));
        linkRepository.deleteAll(linkRepository.findByCultureOption_Id(id));
        optionRepository.delete(o);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<LabTestCultureOptionResponseDto> findByLabTest(UUID labTestId, UUID branchId) {
        AnalysesDeBiologie.charger(labTestRepository, labTestId, branchId, BiologyKind.CULTURE);
        return linkRepository.findByLabTest_IdOrderByPositionAsc(labTestId).stream()
                .map(LabTestCultureOptionResponseDto::of).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public List<LabTestCultureOptionResponseDto> setForLabTest(UUID labTestId, List<UUID> cultureOptionIds,
                                                               UUID branchId) {
        LabTest analyse = AnalysesDeBiologie.charger(labTestRepository, labTestId, branchId, BiologyKind.CULTURE);
        List<UUID> ids = Objects.requireNonNullElse(cultureOptionIds, List.<UUID>of());
        Set<UUID> distincts = new HashSet<>();
        for (UUID id : ids) {
            if (id == null) {
                throw new BusinessException("Une option de culture de la liste est vide.");
            }
            if (!distincts.add(id)) {
                throw new BusinessException("L'option de culture " + id + " figure deux fois dans la liste.");
            }
        }
        Map<UUID, BiologyCultureOption> options = ids.isEmpty() ? Map.of()
                : optionRepository.findByIdInAndBranchId(ids, branchId).stream()
                        .collect(Collectors.toMap(BiologyCultureOption::getId, Function.identity()));
        for (UUID id : ids) {
            if (!options.containsKey(id)) {
                throw new ResourceNotFoundException("Option de culture", id);
            }
        }

        Map<UUID, LabTestCultureOption> existants = linkRepository.findByLabTest_IdOrderByPositionAsc(labTestId)
                .stream()
                .collect(Collectors.toMap(l -> l.getCultureOption().getId(), Function.identity(), (a, b) -> a));
        List<LabTestCultureOption> retires = existants.entrySet().stream()
                .filter(e -> !distincts.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        linkRepository.deleteAll(retires);

        for (int i = 0; i < ids.size(); i++) {
            UUID id = ids.get(i);
            LabTestCultureOption lien = existants.get(id);
            if (lien == null) {
                lien = new LabTestCultureOption();
                lien.setLabTest(analyse);
                lien.setCultureOption(options.get(id));
                lien.setBranchId(branchId);
            }
            lien.setPosition(i);
            linkRepository.save(lien);
        }
        linkRepository.flush();
        return linkRepository.findByLabTest_IdOrderByPositionAsc(labTestId).stream()
                .map(LabTestCultureOptionResponseDto::of).toList();
    }

    private static void appliquer(BiologyCultureOption o, CultureOptionRequestDto dto, String nom) {
        o.setName(nom);
        List<String> choix = BiologySheetValidator.normaliserChoix(dto.getChoices());
        o.setChoices(choix.isEmpty() ? null : choix);
        o.setPosition(dto.getPosition() == null ? 0 : dto.getPosition());
    }
}
