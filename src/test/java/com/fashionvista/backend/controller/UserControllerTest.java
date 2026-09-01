package com.fashionvista.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.dto.UpdateProfileRequest;
import com.fashionvista.backend.dto.UserProfileResponse;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.integration.sapo.service.SapoCustomerSyncService;
import com.fashionvista.backend.repository.AddressRepository;
import com.fashionvista.backend.repository.UserRepository;
import com.fashionvista.backend.service.RefreshTokenService;
import com.fashionvista.backend.service.UserContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserContextService userContextService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private SapoCustomerSyncService sapoCustomerSyncService;

    @InjectMocks
    private UserController userController;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .fullName("Old Name")
                .phoneNumber("0900000000")
                .role(UserRole.CUSTOMER)
                .active(true)
                .build();
    }

    @Test
    void updateProfile_ValidRequest_SavesAndTriggersSapoCustomerPushDirectly() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("New Name");
        request.setPhoneNumber("0911111111");

        when(userContextService.getCurrentUser()).thenReturn(user);
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserProfileResponse response = userController.updateProfile(request);

        assertEquals("New Name", response.getFullName());
        verify(sapoCustomerSyncService).pushCustomer(1L);
    }
}
