package com.fashionvista.backend.controller.sapo;

import com.fashionvista.backend.dto.sapo.SapoCustomerDto;
import com.fashionvista.backend.dto.sapo.SapoCustomerRequest;
import com.fashionvista.backend.dto.sapo.SapoPageResponse;
import com.fashionvista.backend.dto.sapo.SapoResponse;
import com.fashionvista.backend.entity.AccountStatus;
import com.fashionvista.backend.entity.Gender;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.UserRole;
import com.fashionvista.backend.exception.SapoDuplicateException;
import com.fashionvista.backend.exception.SapoNotFoundException;
import com.fashionvista.backend.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sapo/v1/customers")
@RequiredArgsConstructor
@Validated
public class SapoCustomerController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public SapoPageResponse<SapoCustomerDto> getCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") @Max(200) int size,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedAfter,
            @RequestParam(required = false) String email) {

        Specification<User> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("role"), UserRole.CUSTOMER));
            if (updatedAfter != null) {
                predicates.add(cb.greaterThan(root.get("updatedAt"), updatedAfter));
            }
            if (email != null && !email.isBlank()) {
                predicates.add(cb.equal(root.get("email"), email.trim().toLowerCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<SapoCustomerDto> result = userRepository
            .findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(this::toDto);
        return SapoPageResponse.of(result);
    }

    @GetMapping("/{id}")
    public SapoResponse<SapoCustomerDto> getCustomer(@PathVariable Long id) {
        User user = userRepository.findById(id)
            .filter(u -> u.getRole() == UserRole.CUSTOMER)
            .orElseThrow(() -> new SapoNotFoundException("Customer not found: " + id));
        return SapoResponse.ok(toDto(user));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SapoResponse<SapoCustomerDto> createCustomer(
            @RequestBody @Valid SapoCustomerRequest req) {

        if (userRepository.existsByEmail(req.getEmail())) {
            throw new SapoDuplicateException("Email already exists: " + req.getEmail());
        }
        if (userRepository.existsByPhoneNumber(req.getPhoneNumber())) {
            throw new SapoDuplicateException("Phone number already exists: " + req.getPhoneNumber());
        }

        User user = User.builder()
            .email(req.getEmail().trim().toLowerCase())
            .fullName(req.getFullName())
            .phoneNumber(req.getPhoneNumber())
            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
            .role(UserRole.CUSTOMER)
            .status(AccountStatus.ACTIVE)
            .active(true)
            .isEmailVerified(false)
            .gender(req.getGender() != null ? Gender.valueOf(req.getGender()) : null)
            .dateOfBirth(req.getDateOfBirth())
            .build();

        return SapoResponse.ok(toDto(userRepository.save(user)));
    }

    @PutMapping("/{id}")
    public SapoResponse<SapoCustomerDto> updateCustomer(
            @PathVariable Long id,
            @RequestBody @Valid SapoCustomerRequest req) {

        User user = userRepository.findById(id)
            .filter(u -> u.getRole() == UserRole.CUSTOMER)
            .orElseThrow(() -> new SapoNotFoundException("Customer not found: " + id));

        if (!user.getEmail().equals(req.getEmail()) && userRepository.existsByEmail(req.getEmail())) {
            throw new SapoDuplicateException("Email already exists: " + req.getEmail());
        }
        if (!user.getPhoneNumber().equals(req.getPhoneNumber())
                && userRepository.existsByPhoneNumber(req.getPhoneNumber())) {
            throw new SapoDuplicateException("Phone number already exists: " + req.getPhoneNumber());
        }

        user.setEmail(req.getEmail().trim().toLowerCase());
        user.setFullName(req.getFullName());
        user.setPhoneNumber(req.getPhoneNumber());
        if (req.getGender() != null) {
            user.setGender(Gender.valueOf(req.getGender()));
        }
        user.setDateOfBirth(req.getDateOfBirth());

        return SapoResponse.ok(toDto(userRepository.save(user)));
    }

    private SapoCustomerDto toDto(User u) {
        return SapoCustomerDto.builder()
            .id(u.getId())
            .email(u.getEmail())
            .fullName(u.getFullName())
            .phoneNumber(u.getPhoneNumber())
            .gender(u.getGender() != null ? u.getGender().name() : null)
            .dateOfBirth(u.getDateOfBirth())
            .status(u.getStatus().name())
            .createdAt(u.getCreatedAt())
            .updatedAt(u.getUpdatedAt())
            .build();
    }
}
