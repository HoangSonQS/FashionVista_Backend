package com.fashionvista.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionvista.backend.dto.AuthResponse;
import com.fashionvista.backend.dto.ForgotPasswordRequest;
import com.fashionvista.backend.dto.LoginRequest;
import com.fashionvista.backend.dto.RefreshTokenRequest;
import com.fashionvista.backend.dto.RefreshTokenResponse;
import com.fashionvista.backend.dto.RegisterRequest;
import com.fashionvista.backend.dto.ResetPasswordRequest;
import com.fashionvista.backend.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
public class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    void register_ValidRequest_ReturnsCreated() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");
        request.setFullName("Test User");
        request.setPhoneNumber("1234567890");

        AuthResponse response = new AuthResponse();
        response.setToken("jwt-token");

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void login_ValidRequest_ReturnsOk() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("test@example.com");
        request.setPassword("password");

        AuthResponse response = new AuthResponse();
        response.setToken("jwt-token");

        when(authService.login(any(LoginRequest.class), any(HttpServletRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));

        verify(authService).login(any(LoginRequest.class), any(HttpServletRequest.class));
    }

    @Test
    void verifyEmail_ValidToken_ReturnsOk() throws Exception {
        String token = "valid-token";
        when(authService.verifyEmail(token)).thenReturn(true);

        mockMvc.perform(post("/api/auth/verify-email").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email da duoc xac thuc thanh cong."));
    }

    @Test
    void verifyEmail_InvalidToken_ReturnsBadRequest() throws Exception {
        String token = "invalid-token";
        when(authService.verifyEmail(token)).thenReturn(false);

        mockMvc.perform(post("/api/auth/verify-email").param("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Xac thuc email that bai."));
    }

    @Test
    void resendVerificationEmail_ValidEmail_ReturnsOk() throws Exception {
        String email = "test@example.com";

        mockMvc.perform(post("/api/auth/resend-verification").param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Da gui lai email xac thuc."));

        verify(authService).resendVerificationEmail(email);
    }

    @Test
    void forgotPassword_ValidEmail_ReturnsOk() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("test@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Da gui email dat lai mat khau neu email ton tai."));

        verify(authService).forgotPassword(request.getEmail());
    }

    @Test
    void resetPassword_ValidToken_ReturnsOk() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("valid-token");
        request.setNewPassword("new-password");

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Dat lai mat khau thanh cong."));

        verify(authService).resetPassword(request.getToken(), request.getNewPassword());
    }

    @Test
    void refreshToken_ValidRequest_ReturnsOk() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh-token");

        RefreshTokenResponse response = new RefreshTokenResponse();
        response.setAccessToken("new-access-token");
        response.setRefreshToken("new-refresh-token");

        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"));

        verify(authService).refreshToken(any(RefreshTokenRequest.class));
    }

    @Test
    void logout_ValidRequest_ReturnsOk() throws Exception {
        String refreshToken = "valid-refresh-token";

        mockMvc.perform(post("/api/auth/logout").param("refreshToken", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Dang xuat thanh cong."));

        verify(authService).logout(refreshToken);
    }
}
