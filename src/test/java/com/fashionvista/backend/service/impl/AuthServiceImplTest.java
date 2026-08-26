package com.fashionvista.backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fashionvista.backend.dto.AuthResponse;
import com.fashionvista.backend.dto.LoginRequest;
import com.fashionvista.backend.dto.RegisterRequest;
import com.fashionvista.backend.entity.EmailVerificationToken;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.integration.sapo.service.SapoCustomerSyncService;
import com.fashionvista.backend.repository.EmailVerificationTokenRepository;
import com.fashionvista.backend.repository.PasswordResetTokenRepository;
import com.fashionvista.backend.repository.RefreshTokenRepository;
import com.fashionvista.backend.repository.UserRepository;
import com.fashionvista.backend.service.EmailService;
import com.fashionvista.backend.service.JwtService;

@ExtendWith(MockitoExtension.class)
public class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private EmailService emailService;
    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private SapoCustomerSyncService sapoCustomerSyncService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("encodedPassword")
                .fullName("Test User")
                .role(UserRole.CUSTOMER)
                .build();
    }

    @Test
    void register_NewEmail_ReturnsAuthResponse() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");
        request.setFullName("Test User");
        request.setPhoneNumber("1234567890");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("1234567890")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-token");
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.empty());

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        verify(emailService).sendVerificationEmail(any(User.class), any(String.class));
    }

    @Test
    void register_ExistingEmail_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> authService.register(request));

        assertEquals("Email da duoc su dung.", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_NewEmail_TriggersSapoCustomerPush() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");
        request.setFullName("Test User");
        request.setPhoneNumber("1234567890");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("1234567890")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-token");
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.empty());

        authService.register(request);

        verify(sapoCustomerSyncService).pushCustomer(1L);
    }

    @Test
    void register_WithinActiveTransaction_DefersCustomerPushUntilAfterCommit() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");
        request.setFullName("Test User");
        request.setPhoneNumber("1234567890");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("1234567890")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-token");
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.empty());

        TransactionSynchronizationManager.initSynchronization();
        try {
            authService.register(request);
            verify(sapoCustomerSyncService, never()).pushCustomer(anyLong());
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(sapoCustomerSyncService).pushCustomer(1L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void login_ValidCredentials_ReturnsAuthResponse() {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("test@example.com");
        request.setPassword("password");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encodedPassword")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-token");
        when(refreshTokenRepository.findByUser(user)).thenReturn(Optional.empty());

        AuthResponse response = authService.login(request, null);

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
    }

    @Test
    void login_InvalidCredentials_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("test@example.com");
        request.setPassword("wrongpassword");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpassword", "encodedPassword")).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> authService.login(request, null));

        assertEquals("Email/so dien thoai hoac mat khau khong dung.", exception.getMessage());
    }

    @Test
    void verifyEmail_ValidToken_ReturnsTrue() {
        String token = "valid-token";
        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setToken(token);
        verificationToken.setUser(user);
        verificationToken.setExpiresAt(LocalDateTime.now().plusHours(24));

        when(emailVerificationTokenRepository.findByToken(token)).thenReturn(Optional.of(verificationToken));

        boolean result = authService.verifyEmail(token);

        assertTrue(result);
        assertTrue(user.isEmailVerified());
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmail_InvalidToken_ThrowsException() {
        String token = "invalid-token";
        when(emailVerificationTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> authService.verifyEmail(token));
    }

    @Test
    void forgotPassword_ExistingEmail_SendsEmail() {
        String email = "test@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findByUser(user)).thenReturn(Optional.empty());

        authService.forgotPassword(email);

        verify(emailService).sendPasswordResetEmail(any(User.class), any(String.class));
    }

    @Test
    void forgotPassword_NormalizesEmailBeforeLookup() {
        String normalizedEmail = "test@example.com";
        when(userRepository.findByEmail(normalizedEmail)).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findByUser(user)).thenReturn(Optional.empty());

        authService.forgotPassword("  Test@Example.com  ");

        verify(userRepository).findByEmail(normalizedEmail);
        verify(emailService).sendPasswordResetEmail(any(User.class), any(String.class));
    }

    @Test
    void forgotPassword_NonExistingEmail_DoNothing() {
        String email = "nonexistent@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        authService.forgotPassword(email);

        verify(emailService, never()).sendPasswordResetEmail(any(User.class), any(String.class));
    }
}
