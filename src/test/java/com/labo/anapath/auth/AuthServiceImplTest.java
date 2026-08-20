package com.labo.anapath.auth;

import com.labo.anapath.common.email.EmailService;
import com.labo.anapath.common.exception.InvalidCodeException;
import com.labo.anapath.common.exception.UnauthorizedException;
import com.labo.anapath.common.security.CustomUserDetailsService;
import com.labo.anapath.common.security.JwtProperties;
import com.labo.anapath.common.security.JwtTokenProvider;
import com.labo.anapath.common.security.TokenBlacklistService;
import com.labo.anapath.common.security.UserPrincipal;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserMapper;
import com.labo.anapath.user.UserRepository;
import com.labo.anapath.user.UserResponseDto;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private GoogleAuthenticator googleAuthenticator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TwoFaRepository twoFaRepository;

    @Mock
    private EmailService emailService;

    /**
     * La connexion consulte l'application d'authentification avant de se
     * rabattre sur le code envoyé par courriel. Sans ce mock, le challenge
     * échoue sur un service nul.
     */
    @Mock
    private TwoFaService twoFaService;

    @InjectMocks
    private AuthServiceImpl authService;

    private final UUID USER_ID = UUID.randomUUID();
    private final UUID BRANCH_ID = UUID.randomUUID();

    private User buildUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("admin@test.com");
        user.setPassword("$2a$12$encodedpassword");
        user.setFirstname("Admin");
        user.setLastname("Test");
        user.setBranchId(BRANCH_ID);
        return user;
    }

    private User buildUserWith2fa() {
        User user = buildUser();
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret("BASE32SECRET");
        return user;
    }

    private UserPrincipal buildPrincipal(User user) {
        return UserPrincipal.create(user, List.of("manage-users"));
    }

    @Test
    @DisplayName("login - should return LoginResponse on valid credentials (no 2FA)")
    void login_success() {
        User user = buildUser(); // twoFactorEnabled = false by default
        UserPrincipal principal = buildPrincipal(user);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(principal)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any(UUID.class))).thenReturn("refresh-token");
        when(jwtProperties.getExpirationMs()).thenReturn(86_400_000L);
        when(userMapper.toResponseDto(user)).thenReturn(
                new UserResponseDto(USER_ID, "Admin", "Test", "admin@test.com", null, null, null, true,
                        BRANCH_ID, LocalDateTime.now(), null, null, null, List.of(), false));

        LoginRequest request = new LoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("password123");

        LoginResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.expiresIn()).isEqualTo(86_400L);
        assertThat(response.user().email()).isEqualTo("admin@test.com");
        assertThat(response.requires2fa()).isNull();
    }

    @Test
    @DisplayName("login - should return requires2fa=true and tempToken when 2FA is enabled")
    void login_with2faEnabled_returnsTempToken() {
        User user = buildUserWith2fa();
        UserPrincipal principal = buildPrincipal(user);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateTempToken(any(UUID.class))).thenReturn("temp-jwt-token");

        LoginRequest request = new LoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("password123");

        LoginResponse response = authService.login(request);

        assertThat(response.requires2fa()).isTrue();
        assertThat(response.tempToken()).isEqualTo("temp-jwt-token");
        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();
    }

    @Test
    @DisplayName("login - should throw UnauthorizedException when account is disabled")
    void login_inactiveUser_throws401() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("User is disabled"));

        LoginRequest request = new LoginRequest();
        request.setEmail("disabled@test.com");
        request.setPassword("password123");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Identifiants invalides");
    }

    @Test
    @DisplayName("login - should throw UnauthorizedException on wrong password")
    void login_wrongPassword() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest request = new LoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("wrongpassword");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Identifiants invalides");
    }

    @Test
    @DisplayName("login - should throw UnauthorizedException when user not found after auth")
    void login_userNotFound() {
        User user = buildUser();
        UserPrincipal principal = buildPrincipal(user);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("password123");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("challenge - should return full JWT on valid tempToken and valid TOTP code")
    void challenge_validCredentials_returnsFullJwt() {
        User user = buildUserWith2fa();
        UserPrincipal principal = buildPrincipal(user);

        when(jwtTokenProvider.validateToken("temp-token")).thenReturn(true);
        when(jwtTokenProvider.extractType("temp-token")).thenReturn("2fa-challenge");
        when(jwtTokenProvider.extractUserId("temp-token")).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        TwoFa twoFa = new TwoFa(USER_ID, BRANCH_ID, "hashed-code");
        twoFa.setCreatedAt(LocalDateTime.now());
        when(twoFaRepository.findByUserId(USER_ID)).thenReturn(Optional.of(twoFa));
        when(passwordEncoder.matches("123456", "hashed-code")).thenReturn(true);
        when(customUserDetailsService.loadUserById(USER_ID)).thenReturn(principal);
        when(jwtTokenProvider.generateToken(principal)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(USER_ID)).thenReturn("refresh-token");
        when(jwtProperties.getExpirationMs()).thenReturn(86_400_000L);
        when(userMapper.toResponseDto(user)).thenReturn(
                new UserResponseDto(USER_ID, "Admin", "Test", "admin@test.com", null, null, null, true,
                        BRANCH_ID, LocalDateTime.now(), null, null, null, List.of(), false));

        TwoFactorVerifyRequest request = new TwoFactorVerifyRequest();
        request.setTempToken("temp-token");
        request.setCode("123456");

        LoginResponse response = authService.challenge(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.requires2fa()).isNull();
    }

    @Test
    @DisplayName("challenge - should throw UnauthorizedException on invalid tempToken")
    void challenge_invalidTempToken_throws401() {
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        TwoFactorVerifyRequest request = new TwoFactorVerifyRequest();
        request.setTempToken("bad-token");
        request.setCode("123456");

        assertThatThrownBy(() -> authService.challenge(request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("challenge - should throw InvalidCodeException on wrong TOTP code")
    void challenge_wrongCode_throws400() {
        User user = buildUserWith2fa();

        when(jwtTokenProvider.validateToken("temp-token")).thenReturn(true);
        when(jwtTokenProvider.extractType("temp-token")).thenReturn("2fa-challenge");
        when(jwtTokenProvider.extractUserId("temp-token")).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        TwoFa twoFa = new TwoFa(USER_ID, BRANCH_ID, "hashed-code");
        twoFa.setCreatedAt(LocalDateTime.now());
        when(twoFaRepository.findByUserId(USER_ID)).thenReturn(Optional.of(twoFa));
        when(passwordEncoder.matches("000000", "hashed-code")).thenReturn(false);

        TwoFactorVerifyRequest request = new TwoFactorVerifyRequest();
        request.setTempToken("temp-token");
        request.setCode("000000");

        assertThatThrownBy(() -> authService.challenge(request))
                .isInstanceOf(InvalidCodeException.class);
    }

    @Test
    @DisplayName("refresh - should throw UnauthorizedException when refresh token is blacklisted")
    void refresh_blacklistedToken_throws401() {
        String token = "blacklisted.refresh.token";
        String jti = "blacklisted-jti-uuid";

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.extractType(token)).thenReturn("refresh");
        when(jwtTokenProvider.extractJti(token)).thenReturn(jti);
        when(tokenBlacklistService.isBlacklisted(jti)).thenReturn(true);

        assertThatThrownBy(() -> authService.refresh(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("révoqué");
    }

    @Test
    @DisplayName("logout - should blacklist token jti")
    void logout_blacklistsToken() {
        String token = "some.jwt.token";
        String expectedJti = "test-jti-uuid";
        Instant expectedExpiry = Instant.now().plusSeconds(3600);
        UUID userId = UUID.randomUUID();

        when(jwtTokenProvider.extractJti(token)).thenReturn(expectedJti);
        when(jwtTokenProvider.extractExpiry(token)).thenReturn(expectedExpiry);
        when(jwtTokenProvider.extractUserId(token)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        authService.logout(token);

        verify(tokenBlacklistService).blacklist(eq(expectedJti), eq(expectedExpiry));
    }

    @Test
    @DisplayName("logout - should handle null token gracefully")
    void logout_nullToken_noException() {
        authService.logout(null);
    }
}
