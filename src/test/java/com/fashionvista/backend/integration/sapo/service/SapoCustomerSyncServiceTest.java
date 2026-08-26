package com.fashionvista.backend.integration.sapo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.CustomerTier;
import com.fashionvista.backend.entity.Gender;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoCustomerPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoCustomerPushResponse;
import com.fashionvista.backend.repository.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SapoCustomerSyncServiceTest {

    @Mock
    private SapoApiClient sapoApiClient;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SapoCustomerSyncService sapoCustomerSyncService;

    private static SapoCustomerPushResponse customerResponse(String id) {
        SapoCustomerPushResponse.Customer customer = new SapoCustomerPushResponse.Customer();
        customer.setId(id);
        SapoCustomerPushResponse response = new SapoCustomerPushResponse();
        response.setCustomer(customer);
        return response;
    }

    private static User customerUser() {
        return User.builder()
                .id(1L)
                .email("anh@example.com")
                .fullName("Nguyen Anh")
                .phoneNumber("0900000000")
                .role(UserRole.CUSTOMER)
                .gender(Gender.FEMALE)
                .dateOfBirth(LocalDate.of(1995, 3, 20))
                .tier(CustomerTier.GOLD)
                .sapoSyncStatus(SapoSyncStatus.PENDING)
                .build();
    }

    @Test
    void pushCustomer_NeverSynced_CreatesCustomerAndStoresId() {
        User user = customerUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sapoApiClient.createCustomer(any(SapoCustomerPushRequest.class))).thenAnswer(invocation -> {
            SapoCustomerPushRequest request = invocation.getArgument(0);
            assertThat(request.getCustomer().getFirstName()).isEqualTo("Nguyen");
            assertThat(request.getCustomer().getLastName()).isEqualTo("Anh");
            assertThat(request.getCustomer().getEmail()).isEqualTo("anh@example.com");
            assertThat(request.getCustomer().getPhone()).isEqualTo("0900000000");
            assertThat(request.getCustomer().getGender()).isEqualTo("Female");
            assertThat(request.getCustomer().getDob()).isEqualTo("1995-03-20");
            assertThat(request.getCustomer().getTags()).isEqualTo("fashionvista_tier_gold");
            return customerResponse("501");
        });
        when(userRepository.save(user)).thenReturn(user);

        sapoCustomerSyncService.pushCustomer(1L);

        assertThat(user.getSapoCustomerId()).isEqualTo(501L);
        assertThat(user.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        verify(sapoApiClient, never()).updateCustomer(any(), any());
    }

    @Test
    void pushCustomer_AlreadySynced_CallsUpdateNotCreate() {
        User user = customerUser();
        user.setSapoCustomerId(501L);
        user.setSapoSyncStatus(SapoSyncStatus.SYNCED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sapoApiClient.updateCustomer(eq(501L), any(SapoCustomerPushRequest.class)))
                .thenReturn(customerResponse("501"));
        when(userRepository.save(user)).thenReturn(user);

        sapoCustomerSyncService.pushCustomer(1L);

        assertThat(user.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.SYNCED);
        verify(sapoApiClient, never()).createCustomer(any());
        verify(sapoApiClient).updateCustomer(eq(501L), any(SapoCustomerPushRequest.class));
    }

    @Test
    void pushCustomer_UserNotFound_DoesNothing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        sapoCustomerSyncService.pushCustomer(99L);

        verify(userRepository, never()).save(any());
        verify(sapoApiClient, never()).createCustomer(any());
    }

    @Test
    void pushCustomer_NonCustomerRole_DoesNothing() {
        User admin = customerUser();
        admin.setRole(UserRole.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        sapoCustomerSyncService.pushCustomer(1L);

        verify(userRepository, never()).save(any());
        verify(sapoApiClient, never()).createCustomer(any());
    }

    @Test
    void pushCustomer_ApiThrows_MarksFailedAndDoesNotRethrow() {
        User user = customerUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sapoApiClient.createCustomer(any(SapoCustomerPushRequest.class)))
                .thenThrow(new RuntimeException("Sapo down"));
        when(userRepository.save(user)).thenReturn(user);

        sapoCustomerSyncService.pushCustomer(1L);

        assertThat(user.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
    }

    @Test
    void pushCustomer_NullResponse_MarksFailed() {
        User user = customerUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sapoApiClient.createCustomer(any(SapoCustomerPushRequest.class))).thenReturn(null);
        when(userRepository.save(user)).thenReturn(user);

        sapoCustomerSyncService.pushCustomer(1L);

        assertThat(user.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
        assertThat(user.getSapoCustomerId()).isNull();
    }

    @Test
    void pushCustomer_IncompleteResponseMissingId_MarksFailed() {
        User user = customerUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sapoApiClient.createCustomer(any(SapoCustomerPushRequest.class))).thenReturn(customerResponse(null));
        when(userRepository.save(user)).thenReturn(user);

        sapoCustomerSyncService.pushCustomer(1L);

        assertThat(user.getSapoSyncStatus()).isEqualTo(SapoSyncStatus.FAILED);
        assertThat(user.getSapoCustomerId()).isNull();
    }
}
