package com.fashionvista.backend.controller.sapo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fashionvista.backend.config.SapoApiKeyFilter;
import com.fashionvista.backend.config.SapoProperties;
import com.fashionvista.backend.dto.sapo.SapoCustomerRequest;
import com.fashionvista.backend.entity.AccountStatus;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.exception.SapoExceptionHandler;
import com.fashionvista.backend.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SapoCustomerControllerTest {

    private static final String VALID_KEY = "test-sapo-key";
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private MockMvc mockMvc;

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SapoCustomerController controller;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        SapoProperties props = new SapoProperties();
        props.setApiKey(VALID_KEY);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .addFilter(new SapoApiKeyFilter(props))
            .setControllerAdvice(new SapoExceptionHandler())
            .build();

        sampleUser = User.builder()
            .id(1L).email("a@example.com").fullName("Nguyen A")
            .phoneNumber("0901234567").role(UserRole.CUSTOMER)
            .status(AccountStatus.ACTIVE)
            .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
            .build();
    }

    @Test
    void getCustomers_returnsPageResponse() throws Exception {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(sampleUser)));

        mockMvc.perform(get("/api/sapo/v1/customers").header("X-Api-Key", VALID_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].email").value("a@example.com"));
    }

    @Test
    void getCustomer_found_returnsCustomer() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        mockMvc.perform(get("/api/sapo/v1/customers/1").header("X-Api-Key", VALID_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getCustomer_notFound_returns404() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sapo/v1/customers/99").header("X-Api-Key", VALID_KEY))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createCustomer_newEmail_returns201() throws Exception {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(sampleUser);

        SapoCustomerRequest req = new SapoCustomerRequest();
        req.setEmail("new@example.com");
        req.setFullName("New User");
        req.setPhoneNumber("0912345678");

        mockMvc.perform(post("/api/sapo/v1/customers")
                .header("X-Api-Key", VALID_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createCustomer_duplicateEmail_returns409() throws Exception {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(true);

        SapoCustomerRequest req = new SapoCustomerRequest();
        req.setEmail("a@example.com");
        req.setFullName("Dup");
        req.setPhoneNumber("0999999999");

        mockMvc.perform(post("/api/sapo/v1/customers")
                .header("X-Api-Key", VALID_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict());
    }
}
