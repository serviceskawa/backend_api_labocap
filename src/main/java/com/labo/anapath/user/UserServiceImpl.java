package com.labo.anapath.user;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.DuplicateResourceException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.role.Permission;
import com.labo.anapath.role.PermissionMapper;
import com.labo.anapath.role.PermissionRepository;
import com.labo.anapath.role.PermissionResponseDto;
import com.labo.anapath.role.Role;
import com.labo.anapath.role.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implémentation de {@link UserService} gérant la logique métier des utilisateurs.
 *
 * <p>Responsabilités principales :
 * <ul>
 *   <li>Vérification de l'unicité de l'e-mail avant création ou mise à jour</li>
 *   <li>Hachage BCrypt du mot de passe avant persistance</li>
 *   <li>Résolution des entités {@link Role} à partir de leurs identifiants</li>
 *   <li>Gestion du statut actif/inactif avec réinitialisation de session</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    /**
     * {@inheritDoc}
     * Les résultats sont triés par date de création décroissante.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponseDto> findAll(int page, int size, UUID branchId) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<UserResponseDto> result = userRepository.findByBranchId(branchId, pageRequest)
                .map(userMapper::toResponseDto);
        return PageResponse.of(result);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public UserResponseDto findById(UUID id, UUID branchId) {
        User user = userRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
        return userMapper.toResponseDto(user);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Le mot de passe est obligatoire à la création. Le branchId est injecté
     * depuis le contexte de sécurité du principal connecté, pas depuis le DTO.</p>
     */
    @Override
    @Transactional
    public UserResponseDto create(UserRequestDto dto, UUID branchId) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException("Un utilisateur avec l'email '" + dto.getEmail() + "' existe déjà.");
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BusinessException("Le mot de passe est obligatoire pour la création d'un utilisateur.");
        }
        User user = userMapper.toEntity(dto);
        user.setBranchId(branchId);
        // Statut actif par défaut si non précisé
        user.setActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            List<Role> roles = roleRepository.findAllByIdInAndBranchId(dto.getRoleIds(), branchId);
            user.setRoles(roles);
        }
        User saved = userRepository.save(user);
        log.info("Utilisateur créé: {}", saved.getId());
        return userMapper.toResponseDto(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>L'e-mail peut être modifié à condition qu'il ne soit pas déjà utilisé
     * par un autre compte. Le mot de passe n'est pas modifiable via cette méthode.</p>
     */
    @Override
    @Transactional
    public UserResponseDto update(UUID id, UserRequestDto dto, UUID branchId) {
        User user = userRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
        if (!user.getEmail().equals(dto.getEmail()) && userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException("Un utilisateur avec l'email '" + dto.getEmail() + "' existe déjà.");
        }
        userMapper.updateEntityFromDto(dto, user);
        if (dto.getRoleIds() != null) {
            List<Role> roles = roleRepository.findAllByIdInAndBranchId(dto.getRoleIds(), branchId);
            user.setRoles(roles);
        }
        User updated = userRepository.save(user);
        return userMapper.toResponseDto(updated);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(UUID id, UUID branchId) {
        User user = userRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
        userRepository.delete(user);
        log.info("Utilisateur supprimé (soft): {}", id);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Lors de la désactivation, la session de connexion et le 2FA sont
     * réinitialisés pour invalider immédiatement tout accès en cours.</p>
     */
    @Override
    @Transactional
    public void toggleStatus(UUID id, UUID branchId) {
        User user = userRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
        if (user.isActive()) {
            user.setActive(false);
            // Invalider la session et désactiver le 2FA lors de la désactivation du compte
            user.setConnect(false);
            user.setTwoFactorEnabled(false);
        } else {
            user.setActive(true);
        }
        userRepository.save(user);
        log.info("Statut utilisateur basculé: {} → active={}", id, user.isActive());
    }

    /**
     * {@inheritDoc}
     *
     * <p>L'ancien mot de passe est vérifié via BCrypt avant d'autoriser le changement.</p>
     */
    @Override
    @Transactional
    public void updatePassword(UUID id, UpdatePasswordRequest request, UUID branchId) {
        User user = userRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("Le mot de passe actuel est incorrect.");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Mot de passe mis à jour pour l'utilisateur: {}", id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public UserResponseDto updateMyProfile(UUID id, UpdateProfileRequest request, UUID branchId) {
        User user = userRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));

        user.setFirstname(request.getFirstname());
        user.setLastname(request.getLastname());
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        // Signature absente du corps = signature inchangée (l'écran ne la renvoie
        // que lorsqu'un nouveau fichier a été choisi).
        if (request.getSignature() != null) {
            user.setSignature(request.getSignature());
        }

        User updated = userRepository.save(user);
        log.info("Profil mis à jour par l'utilisateur lui-même: {}", id);
        return userMapper.toResponseDto(updated);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public UserResponseDto updateMyEmail(UUID id, UpdateEmailRequest request, UUID branchId) {
        User user = userRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));

        // L'e-mail est l'identifiant de connexion : on exige le mot de passe actuel
        // pour qu'une session détournée ne suffise pas à s'approprier le compte.
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("Le mot de passe actuel est incorrect.");
        }

        String newEmail = request.getNewEmail().trim();
        if (!user.getEmail().equalsIgnoreCase(newEmail) && userRepository.existsByEmail(newEmail)) {
            throw new DuplicateResourceException("Un utilisateur avec l'email '" + newEmail + "' existe déjà.");
        }

        user.setEmail(newEmail);
        User updated = userRepository.save(user);
        log.info("Adresse e-mail de connexion modifiée pour l'utilisateur: {}", id);
        return userMapper.toResponseDto(updated);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<PermissionResponseDto> getUserPermissions(UUID userId, UUID branchId) {
        User user = userRepository.findByIdAndBranchId(userId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));
        return user.getDirectPermissions().stream()
                .map(permissionMapper::toResponseDto)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void setUserPermissions(UUID userId, List<UUID> permissionIds, UUID branchId) {
        User user = userRepository.findByIdAndBranchId(userId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));
        List<Permission> permissions = permissionRepository.findAllById(permissionIds);
        user.getDirectPermissions().clear();
        user.getDirectPermissions().addAll(permissions);
        userRepository.save(user);
        log.info("Permissions directes mises à jour pour l'utilisateur: {} ({} permissions)", userId, permissions.size());
    }
}
