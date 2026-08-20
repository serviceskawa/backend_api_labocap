package com.labo.anapath.role;

import com.labo.anapath.common.NomComplet;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.DuplicateResourceException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implémentation de {@link RoleService} gérant la logique métier des rôles RBAC.
 *
 * <p>Responsabilités principales :
 * <ul>
 *   <li>Génération et validation de l'unicité des slugs</li>
 *   <li>Résolution des entités {@link Permission} à partir de leurs identifiants</li>
 *   <li>Gestion des associations rôle ↔ permissions</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleMapper roleMapper;
    private final UserRepository userRepository;

    /**
     * {@inheritDoc}
     * Les résultats sont triés par date de création décroissante.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoleResponseDto> findAll(int page, int size, UUID branchId) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<RoleResponseDto> result = roleRepository.findByBranchId(branchId, pageRequest)
                .map(this::toDto);
        return PageResponse.of(result);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public RoleResponseDto findById(UUID id, UUID branchId) {
        Role role = roleRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Rôle", id));
        return toDto(role);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Le slug est dérivé du nom via {@link #generateSlug(String)} et doit
     * être unique dans le système (toutes succursales confondues).</p>
     */
    @Override
    @Transactional
    public RoleResponseDto create(RoleRequestDto dto, UUID branchId) {
        String slug = generateSlug(dto.getName());
        if (roleRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException("Un rôle avec le slug '" + slug + "' existe déjà.");
        }
        Role role = roleMapper.toEntity(dto);
        role.setBranchId(branchId);
        role.setSlug(slug);
        if (dto.getPermissionIds() != null && !dto.getPermissionIds().isEmpty()) {
            List<Permission> permissions = permissionRepository.findAllById(dto.getPermissionIds());
            role.setPermissions(permissions);
        }
        Role saved = roleRepository.save(role);
        log.info("Rôle créé: {}", saved.getId());
        return toDto(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Si le nom change, le slug est recalculé. L'unicité du nouveau slug
     * est vérifiée uniquement s'il diffère de l'ancien.</p>
     */
    @Override
    @Transactional
    public RoleResponseDto update(UUID id, RoleRequestDto dto, UUID branchId) {
        Role role = roleRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Rôle", id));
        String oldSlug = role.getSlug();
        roleMapper.updateEntityFromDto(dto, role);
        String newSlug = generateSlug(role.getName());
        if (!newSlug.equals(oldSlug) && roleRepository.existsBySlug(newSlug)) {
            throw new DuplicateResourceException("Un rôle avec le slug '" + newSlug + "' existe déjà.");
        }
        role.setSlug(newSlug);
        if (dto.getPermissionIds() != null) {
            List<Permission> permissions = permissionRepository.findAllById(dto.getPermissionIds());
            role.setPermissions(permissions);
        }
        Role updated = roleRepository.save(role);
        return toDto(updated);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(UUID id, UUID branchId) {
        Role role = roleRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Rôle", id));
        // Un rôle structurant ne se supprime pas, même vide. La règle qui suit
        // ne protège que les rôles encore attribués : un laboratoire qui
        // réorganise ses comptes passe par un instant où le rôle n'a plus
        // personne, et c'est précisément là qu'il était effaçable.
        if (Boolean.TRUE.equals(role.getIsProtected())) {
            throw new BusinessException(
                    "Le rôle « " + role.getName() + " » structure l'organisation du "
                    + "laboratoire et ne peut pas être supprimé. Ses permissions "
                    + "restent modifiables.");
        }
        // Règle métier : un rôle encore attribué à des utilisateurs ne peut pas être supprimé.
        if (userRepository.existsByRoleId(id)) {
            throw new BusinessException(
                    "Ce rôle est attribué à un ou plusieurs utilisateurs et ne peut pas être supprimé.");
        }
        roleRepository.delete(role);
        log.info("Rôle supprimé: {}", id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public RoleResponseDto assignPermissions(UUID roleId, List<UUID> permissionIds, UUID branchId) {
        Role role = roleRepository.findByIdAndBranchId(roleId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Rôle", roleId));
        List<Permission> permissions = permissionRepository.findAllById(permissionIds);
        role.setPermissions(permissions);
        Role saved = roleRepository.save(role);
        log.info("Permissions assignées au rôle {}: {}", roleId, permissionIds);
        return toDto(saved);
    }

    /**
     * Génère un slug URL-friendly à partir d'un nom de rôle.
     *
     * <p>Les accents sont supprimés, les caractères non alphanumériques
     * remplacés par des tirets, et les tirets en début/fin supprimés.</p>
     *
     * @param name nom source du rôle
     * @return slug normalisé (ex. : "Super Administrateur" → "super-administrateur")
     */
    private String generateSlug(String name) {
        return name.trim()
                   .toLowerCase()
                   .replaceAll("[àâä]", "a")
                   .replaceAll("[éèêë]", "e")
                   .replaceAll("[îï]", "i")
                   .replaceAll("[ôö]", "o")
                   .replaceAll("[ùûü]", "u")
                   .replaceAll("[^a-z0-9]+", "-")
                   .replaceAll("^-|-$", "");
    }

    /**
     * Mappe un rôle vers son DTO en y ajoutant le nom du créateur (colonne
     * « Créé par » de la liste des rôles Laravel), résolu depuis {@code createdBy}.
     */
    private RoleResponseDto toDto(Role role) {
        RoleResponseDto base = roleMapper.toResponseDto(role);
        String creator = resolveCreatorName(role.getCreatedBy());
        return new RoleResponseDto(
                base.id(), base.name(), base.slug(), base.description(),
                base.isAssignable(), base.isProtected(), base.permissions(),
                creator, base.createdAt());
    }

    private String resolveCreatorName(UUID createdBy) {
        if (createdBy == null) {
            return null;
        }
        return userRepository.findById(createdBy)
                .map(u -> NomComplet.de(u.getLastname(), u.getFirstname()))
                .orElse(null);
    }
}
