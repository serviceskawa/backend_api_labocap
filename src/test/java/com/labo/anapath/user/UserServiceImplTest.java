package com.labo.anapath.user;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.DuplicateResourceException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.role.Role;
import com.labo.anapath.role.RoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private final UUID USER_ID = UUID.randomUUID();
    private final UUID BRANCH_ID = UUID.randomUUID();

    private User buildUser() {
        User user = new User();
        user.setFirstname("Test");
        user.setLastname("User");
        user.setEmail("test@labo.bj");
        user.setPassword("$2a$12$encoded");
        user.setBranchId(BRANCH_ID);
        user.setActive(true);
        return user;
    }

    private UserResponseDto buildResponseDto(User user) {
        return new UserResponseDto(USER_ID, user.getFirstname(), user.getLastname(), user.getEmail(),
                user.getPhone(), null, null, user.isActive(), BRANCH_ID, LocalDateTime.now(),
                List.of(), null, null, List.of(), false);
    }

    @Test
    @DisplayName("findAll - retourne une page des utilisateurs de la branche")
    void findAll_returnsPagedResults() {
        User user = buildUser();
        UserResponseDto dto = buildResponseDto(user);
        Page<User> page = new PageImpl<>(List.of(user));

        when(userRepository.findByBranchId(eq(BRANCH_ID), any(Pageable.class))).thenReturn(page);
        when(userMapper.toResponseDto(user)).thenReturn(dto);

        PageResponse<UserResponseDto> result = userService.findAll(0, 20, BRANCH_ID);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).email()).isEqualTo("test@labo.bj");
    }

    @Test
    @DisplayName("findById - retourne le DTO quand l'utilisateur existe")
    void findById_found_returnsDto() {
        User user = buildUser();
        UserResponseDto dto = buildResponseDto(user);

        when(userRepository.findByIdAndBranchId(USER_ID, BRANCH_ID)).thenReturn(Optional.of(user));
        when(userMapper.toResponseDto(user)).thenReturn(dto);

        UserResponseDto result = userService.findById(USER_ID, BRANCH_ID);

        assertThat(result.email()).isEqualTo("test@labo.bj");
    }

    @Test
    @DisplayName("findById - lève ResourceNotFoundException si inexistant")
    void findById_notFound_throws404() {
        when(userRepository.findByIdAndBranchId(USER_ID, BRANCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(USER_ID, BRANCH_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("create - crée l'utilisateur avec les rôles assignés")
    void create_success_withRoles() {
        UUID roleId = UUID.randomUUID();
        Role role = new Role();

        UserRequestDto dto = new UserRequestDto();
        dto.setFirstname("New");
        dto.setLastname("User");
        dto.setEmail("new@labo.bj");
        dto.setPassword("password123");
        dto.setRoleIds(List.of(roleId));

        User user = buildUser();
        UserResponseDto responseDto = buildResponseDto(user);

        when(userRepository.existsByEmail("new@labo.bj")).thenReturn(false);
        when(userMapper.toEntity(dto)).thenReturn(user);
        when(roleRepository.findAllByIdInAndBranchId(List.of(roleId), BRANCH_ID)).thenReturn(List.of(role));
        when(passwordEncoder.encode("password123")).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toResponseDto(user)).thenReturn(responseDto);

        UserResponseDto result = userService.create(dto, BRANCH_ID);

        assertThat(result).isNotNull();
        verify(userRepository).save(any(User.class));
        verify(roleRepository).findAllByIdInAndBranchId(List.of(roleId), BRANCH_ID);
    }

    @Test
    @DisplayName("create - lève DuplicateResourceException si email déjà utilisé")
    void create_duplicateEmail_throws409() {
        UserRequestDto dto = new UserRequestDto();
        dto.setEmail("existing@labo.bj");
        dto.setPassword("password123");
        dto.setFirstname("User");
        dto.setLastname("Test");

        when(userRepository.existsByEmail("existing@labo.bj")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(dto, BRANCH_ID))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("create - lève BusinessException si mot de passe absent")
    void create_missingPassword_throwsBusinessException() {
        UserRequestDto dto = new UserRequestDto();
        dto.setEmail("test@labo.bj");
        dto.setFirstname("User");
        dto.setLastname("Test");
        dto.setPassword(null);

        when(userRepository.existsByEmail("test@labo.bj")).thenReturn(false);

        assertThatThrownBy(() -> userService.create(dto, BRANCH_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mot de passe");
    }

    @Test
    @DisplayName("update - sync des rôles (remplacement complet)")
    void update_success_syncRoles() {
        UUID newRoleId = UUID.randomUUID();
        Role newRole = new Role();

        UserRequestDto dto = new UserRequestDto();
        dto.setFirstname("Updated");
        dto.setLastname("User");
        dto.setEmail("test@labo.bj");
        dto.setRoleIds(List.of(newRoleId));

        User user = buildUser();
        UserResponseDto responseDto = buildResponseDto(user);

        when(userRepository.findByIdAndBranchId(USER_ID, BRANCH_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findAllByIdInAndBranchId(List.of(newRoleId), BRANCH_ID)).thenReturn(List.of(newRole));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toResponseDto(user)).thenReturn(responseDto);

        UserResponseDto result = userService.update(USER_ID, dto, BRANCH_ID);

        assertThat(result).isNotNull();
        verify(roleRepository).findAllByIdInAndBranchId(List.of(newRoleId), BRANCH_ID);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("update - ne touche pas aux rôles si roleIds est null")
    void update_nullRoleIds_doesNotSyncRoles() {
        UserRequestDto dto = new UserRequestDto();
        dto.setFirstname("Updated");
        dto.setLastname("User");
        dto.setEmail("test@labo.bj");
        dto.setRoleIds(null);

        User user = buildUser();
        UserResponseDto responseDto = buildResponseDto(user);

        when(userRepository.findByIdAndBranchId(USER_ID, BRANCH_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toResponseDto(user)).thenReturn(responseDto);

        userService.update(USER_ID, dto, BRANCH_ID);

        // roleRepository.findAllByIdInAndBranchId ne doit PAS être appelé
        verify(roleRepository, org.mockito.Mockito.never()).findAllByIdInAndBranchId(anyList(), any());
    }

    @Test
    @DisplayName("update - lève ResourceNotFoundException si utilisateur inexistant")
    void update_notFound_throws404() {
        UserRequestDto dto = new UserRequestDto();
        dto.setEmail("test@labo.bj");
        dto.setFirstname("Updated");
        dto.setLastname("User");

        when(userRepository.findByIdAndBranchId(USER_ID, BRANCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(USER_ID, dto, BRANCH_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("toggleStatus - désactiver reset is_connect et two_factor_enabled")
    void toggleStatus_deactivate_resetsConnectAnd2fa() {
        User user = buildUser();
        user.setActive(true);
        user.setConnect(true);
        user.setTwoFactorEnabled(true);

        when(userRepository.findByIdAndBranchId(USER_ID, BRANCH_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.toggleStatus(USER_ID, BRANCH_ID);

        assertThat(user.isActive()).isFalse();
        assertThat(user.isConnect()).isFalse();
        assertThat(user.isTwoFactorEnabled()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("toggleStatus - réactiver met uniquement is_active=true")
    void toggleStatus_activate_onlySetsActive() {
        User user = buildUser();
        user.setActive(false);
        user.setConnect(false);
        user.setTwoFactorEnabled(false);

        when(userRepository.findByIdAndBranchId(USER_ID, BRANCH_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.toggleStatus(USER_ID, BRANCH_ID);

        assertThat(user.isActive()).isTrue();
        assertThat(user.isConnect()).isFalse();
        assertThat(user.isTwoFactorEnabled()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("toggleStatus - lève ResourceNotFoundException si utilisateur inexistant")
    void toggleStatus_notFound_throws404() {
        when(userRepository.findByIdAndBranchId(USER_ID, BRANCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.toggleStatus(USER_ID, BRANCH_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updatePassword - met à jour le mot de passe si le mot de passe actuel est correct")
    void updatePassword_success() {
        User user = buildUser();
        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setCurrentPassword("oldPass");
        request.setNewPassword("newPass123");

        when(userRepository.findByIdAndBranchId(USER_ID, BRANCH_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass", user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("newPass123")).thenReturn("$2a$12$newHashed");
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.updatePassword(USER_ID, request, BRANCH_ID);

        assertThat(user.getPassword()).isEqualTo("$2a$12$newHashed");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updatePassword - lève BusinessException si mot de passe actuel incorrect")
    void updatePassword_wrongCurrentPassword_throws422() {
        User user = buildUser();
        UpdatePasswordRequest request = new UpdatePasswordRequest();
        request.setCurrentPassword("wrongPass");
        request.setNewPassword("newPass123");

        when(userRepository.findByIdAndBranchId(USER_ID, BRANCH_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> userService.updatePassword(USER_ID, request, BRANCH_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("incorrect");
    }

    @Test
    @DisplayName("delete - effectue un soft delete (Hibernate SQLDelete)")
    void delete_success_softDelete() {
        User user = buildUser();

        when(userRepository.findByIdAndBranchId(USER_ID, BRANCH_ID)).thenReturn(Optional.of(user));

        userService.delete(USER_ID, BRANCH_ID);

        verify(userRepository).delete(user);
    }
}
