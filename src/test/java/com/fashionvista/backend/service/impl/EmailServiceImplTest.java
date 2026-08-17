package com.fashionvista.backend.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fashionvista.backend.entity.DiscrepancyType;
import com.fashionvista.backend.entity.SyncDiscrepancy;
import com.fashionvista.backend.entity.SyncDomain;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;

class EmailServiceImplTest {

    private JavaMailSender mailSender;
    private SpringTemplateEngine emailTemplateEngine;
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        emailTemplateEngine = mock(SpringTemplateEngine.class);
        emailService = new EmailServiceImpl(mailSender, emailTemplateEngine,
                mock(com.fashionvista.backend.repository.OrderRepository.class),
                mock(com.fashionvista.backend.repository.CartRepository.class));
        ReflectionTestUtils.setField(emailService, "fromEmail", "no-reply@fashionvista.test");
        ReflectionTestUtils.setField(emailService, "fromName", "FashionVista");
        ReflectionTestUtils.setField(emailService, "appName", "FashionVista");
        ReflectionTestUtils.setField(emailService, "adminAlertEmail", "admin@fashionvista.test");
        ReflectionTestUtils.setField(emailService, "adminUrl", "http://localhost:5174");
    }

    @Test
    void sendSyncDiscrepancyAlert_AdminEmailConfigured_SendsOneEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        when(emailTemplateEngine.process(org.mockito.ArgumentMatchers.eq("sync-discrepancy-alert"), any()))
                .thenReturn("<html></html>");

        SyncDiscrepancy discrepancy = SyncDiscrepancy.builder()
                .domain(SyncDomain.INVENTORY)
                .entityId(1L)
                .entityLabel("SKU-001")
                .discrepancyType(DiscrepancyType.VALUE_MISMATCH)
                .details("DB=17, Sapo=20")
                .build();

        emailService.sendSyncDiscrepancyAlert(List.of(discrepancy));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendSyncDiscrepancyAlert_AdminEmailBlank_DoesNotSend() {
        ReflectionTestUtils.setField(emailService, "adminAlertEmail", "");

        emailService.sendSyncDiscrepancyAlert(List.of());

        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
