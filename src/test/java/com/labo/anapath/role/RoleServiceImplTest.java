package com.labo.anapath.role;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.DuplicateResourceException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RoleMapper roleMapper;

    @InjectMocks
    private RoleServiceImpl roleService;

    private final UUID ROLE_ID = UUID.randomUUID();
    private final UUID BRANCH_ID = UUID.randomUUID();

    private Role buildRole(String name, String slug) {
        Role role = new Role();
        role.setName(name);
        role.setSlug(slug);
        role.setBranchId(BRANCH_ID);
        return role;
    }

    private RoleResponseDto buildResponseDto(String name, String slug) {
        return new RoleResponseDto(ROLE_ID, name, slug, "desc", true, List.of(), null, LocalDateTime.now());
    }

    @Test
    @DisplayName("create - génère le slug depuis le nom et assigne les permissions")
    void create_success_generatesSlugAndAssignsPermissions() {
        UUID permId = UUID.randomUUID();
        Permission permission = new Permission();

        RoleRequestDto dto = new RoleRequestDto();
        dto.setName("Technicien");
        dto.setPermissionIds(List.of(permId));

        Role role = buildRole("Technicien", null);
        RoleResponseDto responseDto = buildResponseDto("Technicien", "technicien");

        when(roleRepository.existsBySlug("technicien")).thenReturn(false);
        when(roleMapper.toEntity(dto)).thenReturn(role);
        when(permissionRepository.findAllById(List.of(permId))).thenReturn(List.of(permission));
        when(roleRepository.save(any(Role.class))).thenReturn(role);
        when(roleMapper.toResponseDto(role)).thenReturn(responseDto);

        RoleResponseDto result = roleService.create(dto, BRANCH_ID);

        assertThat(result).isNotNull();
        assertThat(role.getSlug()).isEqualTo("technicien");
        verify(roleRepository).save(role);
        verify(permissionRepository).findAllById(List.of(permId));
    }

    @Test
    @DisplayName("create - doublon de slug → DuplicateResourceException")
    void create_duplicateSlug_throws409() {
        RoleRequestDto dto = new RoleRequestDto();
        dto.setName("Admin");

        when(roleRepository.existsBySlug("admin")).thenReturn(true);

        assertThatThrownBy(() -> roleService.create(dto, BRANCH_ID))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("create - slug ignoré du client, toujours auto-généré depuis le nom")
    void create_clientSlugIgnored_generatesFromName() {
        RoleRequestDto dto = new RoleRequestDto();
        dto.setName("Médecin Chef");
        dto.setSlug("custom-slug-ignored");

        Role role = buildRole("Médecin Chef", null);

        when(roleRepository.existsBySlug("medecin-chef")).thenReturn(false);
        when(roleMapper.toEntity(dto)).thenReturn(role);
        when(roleRepository.save(any(Role.class))).thenReturn(role);
        when(roleMapper.toResponseDto(role)).thenReturn(buildResponseDto("Médecin Chef", "medecin-chef"));

        roleService.create(dto, BRANCH_ID);

        assertThat(role.getSlug()).isEqualTo("medecin-chef");
    }

    @Test
    @DisplayName("findAll - retourne une liste paginée des rôles de la branche")
    void findAll_returnsPagedResults() {
        Role role = buildRole("Admin", "admin");
        RoleResponseDto dto = buildResponseDto("Admin", "admin");
        Page<Role> page = new PageImpl<>(List.of(role));

        when(roleRepository.findByBranchId(eq(BRANCH_ID), any(Pageable.class))).thenReturn(page);
        when(roleMapper.toResponseDto(role)).thenReturn(dto);

        PageResponse<RoleResponseDto> result = roleService.findAll(0, 20, BRANCH_ID);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).slug()).isEqualTo("admin");
    }

    @Test
    @DisplayName("findById - retourne le DTO quand le rôle existe")
    void findById_found_returnsDto() {
        Role role = buildRole("Admin", "admin");
        RoleResponseDto dto = buildResponseDto("Admin", "admin");

        when(roleRepository.findByIdAndBranchId(ROLE_ID, BRANCH_ID)).thenReturn(Optional.of(role));
        when(roleMapper.toResponseDto(role)).thenReturn(dto);

        RoleResponseDto result = roleService.findById(ROLE_ID, BRANCH_ID);

        assertThat(result.slug()).isEqualTo("admin");
    }

    @Test
    @DisplayName("findById - lève ResourceNotFoundException si inexistant")
    void findById_notFound_throws404() {
        when(roleRepository.findByIdAndBranchId(ROLE_ID, BRANCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.findById(ROLE_ID, BRANCH_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("update - met à jour le nom et re-génère le slug")
    void update_success_updatesNameAndSlug() {
        Role role = buildRole("Ancien Nom", "ancien-nom");
        RoleResponseDto responseDto = buildResponseDto("Nouveau Nom", "nouveau-nom");

        RoleRequestDto dto = new RoleRequestDto();
        dto.setName("Nouveau Nom");
        dto.setPermissionIds(null);

        when(roleRepository.findByIdAndBranchId(ROLE_ID, BRANCH_ID)).thenReturn(Optional.of(role));
        when(roleMapper.toResponseDto(any(Role.class))).thenReturn(responseDto);
        when(roleRepository.save(any(Role.class))).thenReturn(role);

        // simulate mapper updating the name
        org.mockito.Mockito.doAnswer(inv -> {
            role.setName("Nouveau Nom");
            return null;
        }).when(roleMapper).updateEntityFromDto(eq(dto), eq(role));

        when(roleRepository.existsBySlug("nouveau-nom")).thenReturn(false);

        RoleResponseDto result = roleService.update(ROLE_ID, dto, BRANCH_ID);

        assertThat(result).isNotNull();
        assertThat(role.getSlug()).isEqualTo("nouveau-nom");
        verify(roleRepository).save(role);
    }

    @Test
    @DisplayName("update - sync les permissions quand permissionIds est fourni")
    void update_withPermissionIds_syncsPermissions() {
        UUID permId = UUID.randomUUID();
        Permission permission = new Permission();
        Role role = buildRole("Admin", "admin");

        RoleRequestDto dto = new RoleRequestDto();
        dto.setName("Admin");
        dto.setPermissionIds(List.of(permId));

        when(roleRepository.findByIdAndBranchId(ROLE_ID, BRANCH_ID)).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(List.of(permId))).thenReturn(List.of(permission));
        when(roleRepository.save(any(Role.class))).thenReturn(role);
        when(roleMapper.toResponseDto(any(Role.class))).thenReturn(buildResponseDto("Admin", "admin"));

        roleService.update(ROLE_ID, dto, BRANCH_ID);

        verify(permissionRepository).findAllById(List.of(permId));
        assertThat(role.getPermissions()).containsExactly(permission);
    }

    @Test
    @DisplayName("update - ne touche pas aux permissions si permissionIds est null")
    void update_nullPermissionIds_doesNotTouchPermissions() {
        Role role = buildRole("Admin", "admin");
        role.setPermissions(List.of(new Permission()));

        RoleRequestDto dto = new RoleRequestDto();
        dto.setName("Admin");
        dto.setPermissionIds(null);

        when(roleRepository.findByIdAndBranchId(ROLE_ID, BRANCH_ID)).thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class))).thenReturn(role);
        when(roleMapper.toResponseDto(any(Role.class))).thenReturn(buildResponseDto("Admin", "admin"));

        roleService.update(ROLE_ID, dto, BRANCH_ID);

        verify(permissionRepository, never()).findAllById(anyList());
    }

    @Test
    @DisplayName("assignPermissions - remplace entièrement les permissions du rôle")
    void assignPermissions_replacesPermissions() {
        UUID permId = UUID.randomUUID();
        Permission permission = new Permission();
        Role role = buildRole("Admin", "admin");

        when(roleRepository.findByIdAndBranchId(ROLE_ID, BRANCH_ID)).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(List.of(permId))).thenReturn(List.of(permission));
        when(roleRepository.save(any(Role.class))).thenReturn(role);
        when(roleMapper.toResponseDto(any(Role.class))).thenReturn(buildResponseDto("Admin", "admin"));

        roleService.assignPermissions(ROLE_ID, List.of(permId), BRANCH_ID);

        assertThat(role.getPermissions()).containsExactly(permission);
        verify(roleRepository).save(role);
    }

    @Test
    @DisplayName("delete - supprime le rôle")
    void delete_success() {
        Role role = buildRole("Admin", "admin");

        when(roleRepository.findByIdAndBranchId(ROLE_ID, BRANCH_ID)).thenReturn(Optional.of(role));

        roleService.delete(ROLE_ID, BRANCH_ID);

        verify(roleRepository).delete(role);
    }
}
