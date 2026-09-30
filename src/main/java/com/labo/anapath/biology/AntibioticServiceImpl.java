package com.labo.anapath.biology;

import com.labo.anapath.common.exception.DuplicateResourceException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implémentation de {@link AntibioticService}.
 *
 * <p>Nom et code sont uniques parmi les antibiotiques actifs de la succursale, sans
 * tenir compte de la casse ; la base le garantit aussi par index partiels.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AntibioticServiceImpl implements AntibioticService {

    private final AntibioticRepository repository;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AntibioticResponseDto> findAll(UUID branchId) {
        return repository.findByBranchIdOrderByPositionAscNameAsc(branchId).stream()
                .map(AntibioticResponseDto::of).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AntibioticResponseDto create(AntibioticRequestDto dto, UUID branchId) {
        String nom = dto.getName().trim();
        String code = vide(dto.getCode());
        if (repository.existsByNameIgnoreCaseAndBranchId(nom, branchId)) {
            throw new DuplicateResourceException("Un antibiotique « " + nom + " » existe déjà.");
        }
        if (code != null && repository.existsByCodeIgnoreCaseAndBranchId(code, branchId)) {
            throw new DuplicateResourceException("Le code « " + code + " » est déjà utilisé par un autre antibiotique.");
        }
        Antibiotic a = new Antibiotic();
        a.setBranchId(branchId);
        appliquer(a, dto, nom, code);
        Antibiotic enregistre = repository.save(a);
        log.info("Antibiotique créé : {}", enregistre.getId());
        return AntibioticResponseDto.of(enregistre);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AntibioticResponseDto update(UUID id, AntibioticRequestDto dto, UUID branchId) {
        Antibiotic a = repository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Antibiotique", id));
        String nom = dto.getName().trim();
        String code = vide(dto.getCode());
        if (repository.existsByNameIgnoreCaseAndBranchIdAndIdNot(nom, branchId, id)) {
            throw new DuplicateResourceException("Un antibiotique « " + nom + " » existe déjà.");
        }
        if (code != null && repository.existsByCodeIgnoreCaseAndBranchIdAndIdNot(code, branchId, id)) {
            throw new DuplicateResourceException("Le code « " + code + " » est déjà utilisé par un autre antibiotique.");
        }
        appliquer(a, dto, nom, code);
        return AntibioticResponseDto.of(repository.save(a));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(UUID id, UUID branchId) {
        Antibiotic a = repository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Antibiotique", id));
        repository.delete(a);
    }

    private static void appliquer(Antibiotic a, AntibioticRequestDto dto, String nom, String code) {
        a.setName(nom);
        a.setCommercialName(vide(dto.getCommercialName()));
        a.setFamily(vide(dto.getFamily()));
        a.setCode(code);
        a.setPosition(dto.getPosition() == null ? 0 : dto.getPosition());
    }

    /** Chaîne rognée, ou {@code null} si vide. */
    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
