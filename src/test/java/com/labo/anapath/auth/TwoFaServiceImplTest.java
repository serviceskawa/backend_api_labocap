package com.labo.anapath.auth;

import com.labo.anapath.common.exception.InvalidCodeException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import com.labo.anapath.common.security.Totp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwoFaServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private Totp totp;

    @InjectMocks
    private TwoFaServiceImpl twoFaService;

    private final UUID USER_ID = UUID.randomUUID();

    private User buildUser() {
        User user = new User();
        user.setEmail("test@labo.bj");
        user.setFirstname("Test");
        user.setLastname("User");
        return user;
    }

    private User buildUserWith2fa() {
        User user = buildUser();
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret("BASE32SECRET");
        return user;
    }

    @Test
    @DisplayName("setup - should generate secret and save it to user")
    void setup_generatesSecretAndSaves() {
        ReflectionTestUtils.setField(twoFaService, "appName", "Test App");
        User user = buildUser();
        user.setEmail("test@labo.bj");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(totp.nouveauSecret()).thenReturn("JBSWY3DPEHPK3PXP");
        when(userRepository.save(any(User.class))).thenReturn(user);

        TwoFaSetupResponse response = twoFaService.setup(USER_ID);

        assertThat(response.secret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(response.qrCodeBase64()).isNotBlank();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("setup - should throw ResourceNotFoundException when user not found")
    void setup_userNotFound_throws() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> twoFaService.setup(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("verifyAndEnable - should enable 2FA on valid TOTP code")
    void verifyAndEnable_validCode_enables2fa() {
        User user = buildUser();
        user.setTwoFactorSecret("BASE32SECRET");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(totp.verifier(eq("BASE32SECRET"), anyInt())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(user);

        twoFaService.verifyAndEnable(USER_ID, "123456");

        assertThat(user.isTwoFactorEnabled()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("verifyAndEnable - should throw InvalidCodeException on wrong TOTP code")
    void verifyAndEnable_invalidCode_throws400() {
        User user = buildUser();
        user.setTwoFactorSecret("BASE32SECRET");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(totp.verifier(eq("BASE32SECRET"), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> twoFaService.verifyAndEnable(USER_ID, "000000"))
                .isInstanceOf(InvalidCodeException.class)
                .hasMessageContaining("invalide");
    }

    @Test
    @DisplayName("verifyAndEnable - should throw InvalidCodeException when 2FA not setup")
    void verifyAndEnable_noSecret_throws() {
        User user = buildUser(); // twoFactorSecret = null

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> twoFaService.verifyAndEnable(USER_ID, "123456"))
                .isInstanceOf(InvalidCodeException.class)
                .hasMessageContaining("setup");
    }

    @Test
    @DisplayName("disable - should disable 2FA on valid TOTP code")
    void disable_validCode_disables2fa() {
        User user = buildUserWith2fa();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(totp.verifier(eq("BASE32SECRET"), anyInt())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(user);

        twoFaService.disable(USER_ID, "123456");

        assertThat(user.isTwoFactorEnabled()).isFalse();
        assertThat(user.getTwoFactorSecret()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("disable - should throw InvalidCodeException on wrong TOTP code")
    void disable_invalidCode_throws400() {
        User user = buildUserWith2fa();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(totp.verifier(eq("BASE32SECRET"), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> twoFaService.disable(USER_ID, "000000"))
                .isInstanceOf(InvalidCodeException.class);
    }

    @Test
    @DisplayName("disable - should throw InvalidCodeException when 2FA is not active")
    void disable_notEnabled_throws() {
        User user = buildUser(); // twoFactorEnabled = false

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> twoFaService.disable(USER_ID, "123456"))
                .isInstanceOf(InvalidCodeException.class);
    }
}
