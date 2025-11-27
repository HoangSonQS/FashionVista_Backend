package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.AddressRequest;
import com.fashionvista.backend.dto.AddressResponse;
import com.fashionvista.backend.dto.UpdateProfileRequest;
import com.fashionvista.backend.dto.UserProfileResponse;
import com.fashionvista.backend.entity.Address;
import com.fashionvista.backend.repository.AddressRepository;
import com.fashionvista.backend.repository.UserRepository;
import com.fashionvista.backend.service.UserContextService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserContextService userContextService;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    @GetMapping
    public UserProfileResponse getProfile() {
        var user = userContextService.getCurrentUser();
        return UserProfileResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .phoneNumber(user.getPhoneNumber())
            .role(user.getRole().name())
            .active(user.isActive())
            .build();
    }

    @PutMapping
    public UserProfileResponse updateProfile(@RequestBody @Valid UpdateProfileRequest request) {
        var user = userContextService.getCurrentUser();
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        var saved = userRepository.save(user);
        return UserProfileResponse.builder()
            .id(saved.getId())
            .email(saved.getEmail())
            .fullName(saved.getFullName())
            .phoneNumber(saved.getPhoneNumber())
            .role(saved.getRole().name())
            .active(saved.isActive())
            .build();
    }

    @GetMapping("/addresses")
    public List<AddressResponse> getAddresses() {
        return addressRepository.findByUserOrderByIsDefaultDescCreatedAtDesc(userContextService.getCurrentUser())
            .stream()
            .map(this::toAddressResponse)
            .toList();
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse createAddress(@RequestBody @Valid AddressRequest request) {
        var address = Address.builder()
            .user(userContextService.getCurrentUser())
            .fullName(request.getFullName())
            .phone(request.getPhone())
            .address(request.getAddress())
            .ward(request.getWard())
            .district(request.getDistrict())
            .city(request.getCity())
            .isDefault(request.isDefault())
            .build();
        if (request.isDefault()) {
            unsetDefaultAddresses();
        }
        return toAddressResponse(addressRepository.save(address));
    }

    @PutMapping("/addresses/{id}")
    public AddressResponse updateAddress(@PathVariable Long id, @RequestBody @Valid AddressRequest request) {
        Address address = addressRepository.findByIdAndUser(id, userContextService.getCurrentUser())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ."));
        address.setFullName(request.getFullName());
        address.setPhone(request.getPhone());
        address.setAddress(request.getAddress());
        address.setWard(request.getWard());
        address.setDistrict(request.getDistrict());
        address.setCity(request.getCity());
        address.setDefault(request.isDefault());
        if (request.isDefault()) {
            unsetDefaultAddresses();
        }
        return toAddressResponse(addressRepository.save(address));
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@PathVariable Long id) {
        Address address = addressRepository.findByIdAndUser(id, userContextService.getCurrentUser())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ."));
        addressRepository.delete(address);
    }

    private void unsetDefaultAddresses() {
        var currentUser = userContextService.getCurrentUser();
        addressRepository.findByUserOrderByIsDefaultDescCreatedAtDesc(currentUser)
            .forEach(addr -> {
                if (addr.isDefault()) {
                    addr.setDefault(false);
                    addressRepository.save(addr);
                }
            });
    }

    private AddressResponse toAddressResponse(Address address) {
        return AddressResponse.builder()
            .id(address.getId())
            .fullName(address.getFullName())
            .phone(address.getPhone())
            .address(address.getAddress())
            .ward(address.getWard())
            .district(address.getDistrict())
            .city(address.getCity())
            .isDefault(address.isDefault())
            .build();
    }
}

