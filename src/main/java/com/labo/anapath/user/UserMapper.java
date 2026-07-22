package com.labo.anapath.user;

import com.labo.anapath.branch.BranchMapper;
import com.labo.anapath.role.PermissionMapper;
import com.labo.anapath.role.RoleMapper;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper MapStruct pour la conversion entre l'entité {@link User} et ses DTOs.
 *
 * <p>Utilise {@link RoleMapper} pour mapper les rôles imbriqués.
 * Les champs sensibles (mot de passe, rôles) sont exclus des mappings
 * afin de ne jamais être écrasés accidentellement par une mise à jour partielle.</p>
 */
@Mapper(componentModel = "spring", uses = {RoleMapper.class, PermissionMapper.class, BranchMapper.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    /**
     * Convertit une entité {@link User} en {@link UserResponseDto}.
     * Le champ {@code isActive} est mappé depuis le booléen {@code active} de l'entité.
     *
     * @param user entité source
     * @return DTO de réponse
     */
    @Mapping(target = "isActive", source = "active")
    @Mapping(target = "roles", source = "roles")
    @Mapping(target = "branches", source = "branches")
    @Mapping(target = "directPermissions", source = "directPermissions")
    UserResponseDto toResponseDto(User user);

    /**
     * Convertit un {@link UserRequestDto} en entité {@link User}.
     * Le mot de passe et les rôles sont ignorés ici ; ils sont gérés manuellement
     * dans le service pour appliquer le hachage BCrypt et la résolution des entités.
     *
     * @param dto DTO source
     * @return entité partiellement initialisée
     */
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "branches", ignore = true)
    @Mapping(target = "active", ignore = true)
    User toEntity(UserRequestDto dto);

    /**
     * Met à jour une entité {@link User} existante à partir d'un {@link UserRequestDto}.
     * Les propriétés nulles du DTO sont ignorées (mise à jour partielle).
     * Les champs d'audit et les identifiants ne sont jamais modifiés.
     *
     * @param dto    DTO contenant les nouvelles valeurs
     * @param user   entité cible à modifier
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "branches", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "branchId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void updateEntityFromDto(UserRequestDto dto, @MappingTarget User user);
}
