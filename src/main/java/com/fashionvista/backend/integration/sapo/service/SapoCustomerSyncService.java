package com.fashionvista.backend.integration.sapo.service;

import com.fashionvista.backend.entity.Gender;
import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.integration.sapo.client.SapoApiClient;
import com.fashionvista.backend.integration.sapo.dto.SapoCustomerPushRequest;
import com.fashionvista.backend.integration.sapo.dto.SapoCustomerPushResponse;
import com.fashionvista.backend.integration.sapo.util.SapoNameSplitter;
import com.fashionvista.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SapoCustomerSyncService {

    private static final Logger log = LoggerFactory.getLogger(SapoCustomerSyncService.class);

    private final SapoApiClient sapoApiClient;
    private final UserRepository userRepository;

    @Async("sapoCustomerTaskExecutor")
    @Transactional
    public void pushCustomer(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Sapo customer sync: user id={} not found, skipping.", userId);
            return;
        }
        if (user.getRole() != UserRole.CUSTOMER) {
            log.warn("Sapo customer sync: user id={} has role={}, skipping.", userId, user.getRole());
            return;
        }
        doPush(user);
        userRepository.save(user);
    }

    private void doPush(User user) {
        try {
            SapoCustomerPushRequest request = buildCustomerRequest(user);
            SapoCustomerPushResponse response = user.getSapoCustomerId() == null
                    ? sapoApiClient.createCustomer(request)
                    : sapoApiClient.updateCustomer(user.getSapoCustomerId(), request);
            if (response == null || response.getCustomer() == null || response.getCustomer().getId() == null) {
                user.setSapoSyncStatus(SapoSyncStatus.FAILED);
                return;
            }
            user.setSapoCustomerId(Long.valueOf(response.getCustomer().getId()));
            user.setSapoSyncStatus(SapoSyncStatus.SYNCED);
        } catch (RuntimeException ex) {
            log.error("Sapo customer sync failed for user id={}: {}", user.getId(), ex.getMessage(), ex);
            user.setSapoSyncStatus(SapoSyncStatus.FAILED);
        }
    }

    private SapoCustomerPushRequest buildCustomerRequest(User user) {
        SapoNameSplitter.Split name = SapoNameSplitter.splitLastName(user.getFullName());
        SapoCustomerPushRequest.Customer.CustomerBuilder customer = SapoCustomerPushRequest.Customer.builder()
                .firstName(name.getFirstName())
                .lastName(name.getLastName())
                .email(user.getEmail())
                .phone(user.getPhoneNumber())
                .gender(mapGender(user.getGender()))
                .dob(user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null)
                .tags(user.getTier() != null ? "fashionvista_tier_" + user.getTier().name().toLowerCase() : null);

        return SapoCustomerPushRequest.builder().customer(customer.build()).build();
    }

    private String mapGender(Gender gender) {
        if (gender == null) {
            return null;
        }
        return switch (gender) {
            case MALE -> "Male";
            case FEMALE -> "Female";
            case OTHER -> "Other";
        };
    }
}
