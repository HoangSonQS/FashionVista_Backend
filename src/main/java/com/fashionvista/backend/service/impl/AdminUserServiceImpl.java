package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.*;
import com.fashionvista.backend.entity.*;
import com.fashionvista.backend.repository.*;
import com.fashionvista.backend.service.AdminUserService;
import com.fashionvista.backend.service.UserContextService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final UserContextService userContextService;
    private final AddressRepository addressRepository;
    private final ReviewRepository reviewRepository;
    private final WishlistRepository wishlistRepository;
    private final LoyaltyPointHistoryRepository loyaltyPointHistoryRepository;
    private final LoginActivityRepository loginActivityRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminUserListResponse> getAllUsers(
        String search,
        UserRole role,
        Boolean active,
        Pageable pageable
    ) {
        Specification<User> spec = buildSpecification(search, role, active);
        return userRepository.findAll(spec, pageable)
            .map(this::toAdminUserListResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserListResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));

        return toAdminUserListResponse(user);
    }

    @Override
    @Transactional
    public AdminUserListResponse updateUserStatus(Long userId, UpdateUserStatusRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));

        user.setActive(request.getActive());
        User saved = userRepository.save(user);

        return toAdminUserListResponse(saved);
    }

    @Override
    @Transactional
    public AdminUserListResponse updateUserRole(Long userId, UpdateUserRoleRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));

        // Validation: Admin không thể tự thay đổi role của chính mình
        User currentAdmin = userContextService.getCurrentUser();
        if (user.getId().equals(currentAdmin.getId())) {
            throw new IllegalStateException("Bạn không thể thay đổi vai trò của chính mình.");
        }

        user.setRole(request.getRole());
        User saved = userRepository.save(user);

        return toAdminUserListResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));

        List<Order> orders = orderRepository.findByUserOrderByCreatedAtDesc(user);
        long totalOrders = orders.size();
        BigDecimal totalSpent = orders.stream()
            .map(Order::getTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageOrderValue = totalOrders > 0
            ? totalSpent.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Tính days since last purchase
        Long daysSinceLastPurchase = orders.stream()
            .findFirst()
            .map(order -> ChronoUnit.DAYS.between(order.getCreatedAt().toLocalDate(), LocalDate.now()))
            .orElse(null);

        // Lấy các relationships
        List<Address> addresses = addressRepository.findByUserOrderByIsDefaultDescCreatedAtDesc(user);
        List<Order> recentOrders = orders.stream().limit(10).collect(Collectors.toList());
        List<com.fashionvista.backend.entity.Wishlist> wishlists = wishlistRepository.findByUserOrderByCreatedAtDesc(user);
        List<com.fashionvista.backend.entity.Review> reviews = reviewRepository.findByUserOrderByCreatedAtDesc(user);
        List<LoyaltyPointHistory> loyaltyHistory = loyaltyPointHistoryRepository.findByUserOrderByCreatedAtDesc(user);
        List<LoginActivity> loginHistory = loginActivityRepository.findByUserOrderByCreatedAtDesc(user).stream()
            .limit(20)
            .collect(Collectors.toList());

        return AdminUserDetailResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .phoneNumber(user.getPhoneNumber())
            .avatarUrl(user.getAvatarUrl())
            .gender(user.getGender())
            .dateOfBirth(user.getDateOfBirth())
            .role(user.getRole())
            .status(user.getStatus() != null ? user.getStatus() : (user.isActive() ? AccountStatus.ACTIVE : AccountStatus.INACTIVE))
            .active(user.isActive())
            .isEmailVerified(user.isEmailVerified())
            .bannedReason(user.getBannedReason())
            .bannedAt(user.getBannedAt())
            .createdAt(user.getCreatedAt())
            .totalOrders(totalOrders)
            .totalSpent(totalSpent)
            .averageOrderValue(averageOrderValue)
            .loyaltyPoints(user.getLoyaltyPoints() != null ? user.getLoyaltyPoints() : 0)
            .tier(user.getTier() != null ? user.getTier() : CustomerTier.BRONZE)
            .lastLoginAt(user.getLastLoginAt())
            .daysSinceLastPurchase(daysSinceLastPurchase)
            .addresses(addresses.stream().map(this::toAddressResponse).collect(Collectors.toList()))
            .recentOrders(recentOrders.stream().map(this::toOrderSummaryResponse).collect(Collectors.toList()))
            .wishlist(wishlists.stream().map(this::toWishlistItemResponse).collect(Collectors.toList()))
            .reviews(reviews.stream().map(this::toReviewSummaryResponse).collect(Collectors.toList()))
            .loyaltyHistory(loyaltyHistory.stream().map(this::toLoyaltyPointHistoryResponse).collect(Collectors.toList()))
            .loginHistory(loginHistory.stream().map(this::toLoginActivityResponse).collect(Collectors.toList()))
            .build();
    }

    @Override
    @Transactional
    public AdminUserListResponse addLoyaltyPoints(Long userId, AddLoyaltyPointsRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));

        int currentPoints = user.getLoyaltyPoints() != null ? user.getLoyaltyPoints() : 0;
        int newBalance = currentPoints + request.getPoints();

        if (newBalance < 0) {
            throw new IllegalArgumentException("Số điểm không đủ để trừ.");
        }

        user.setLoyaltyPoints(newBalance);
        User saved = userRepository.save(user);

        // Tạo lịch sử
        LoyaltyPointHistory history = LoyaltyPointHistory.builder()
            .user(saved)
            .points(request.getPoints())
            .balanceAfter(newBalance)
            .transactionType(request.getTransactionType())
            .source(request.getSource())
            .description(request.getDescription())
            .createdBy(userContextService.getCurrentUser())
            .build();
        loyaltyPointHistoryRepository.save(history);

        return toAdminUserListResponse(saved);
    }

    @Override
    @Transactional
    public String resetPassword(Long userId, ResetPasswordRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));

        // Tạo mật khẩu tạm ngẫu nhiên
        String tempPassword = generateTempPassword();

        // Hash và lưu
        user.setPassword(passwordEncoder.encode(tempPassword));
        userRepository.save(user);

        // TODO: Gửi email nếu sendEmail = true
        if (request.getSendEmail() != null && request.getSendEmail()) {
            // EmailService.sendPasswordResetEmail(user.getEmail(), tempPassword);
        }

        return tempPassword;
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private AddressResponse toAddressResponse(com.fashionvista.backend.entity.Address address) {
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

    private OrderSummaryResponse toOrderSummaryResponse(Order order) {
        return OrderSummaryResponse.builder()
            .id(order.getId())
            .orderNumber(order.getOrderNumber())
            .status(order.getStatus())
            .paymentStatus(order.getPaymentStatus())
            .total(order.getTotal())
            .itemCount(order.getItems().size())
            .createdAt(order.getCreatedAt())
            .build();
    }

    private WishlistItemResponse toWishlistItemResponse(com.fashionvista.backend.entity.Wishlist wishlist) {
        com.fashionvista.backend.entity.Product product = wishlist.getProduct();
        String imageUrl = product.getImages() != null && !product.getImages().isEmpty()
            ? product.getImages().stream()
                .findFirst()
                .map(img -> img.getUrl())
                .orElse(null)
            : null;

        return WishlistItemResponse.builder()
            .id(wishlist.getId())
            .productId(product.getId())
            .productName(product.getName())
            .productSlug(product.getSlug())
            .productImage(imageUrl)
            .price(product.getPrice())
            .addedAt(wishlist.getCreatedAt())
            .build();
    }

    private ReviewSummaryResponse toReviewSummaryResponse(com.fashionvista.backend.entity.Review review) {
        com.fashionvista.backend.entity.Product product = review.getProduct();
        return ReviewSummaryResponse.builder()
            .id(review.getId())
            .productId(product.getId())
            .productName(product.getName())
            .productSlug(product.getSlug())
            .rating(review.getRating())
            .comment(review.getComment())
            .createdAt(review.getCreatedAt())
            .build();
    }

    private LoyaltyPointHistoryResponse toLoyaltyPointHistoryResponse(LoyaltyPointHistory history) {
        return LoyaltyPointHistoryResponse.builder()
            .id(history.getId())
            .points(history.getPoints())
            .balanceAfter(history.getBalanceAfter())
            .transactionType(history.getTransactionType())
            .source(history.getSource())
            .description(history.getDescription())
            .createdAt(history.getCreatedAt())
            .createdByName(history.getCreatedBy() != null ? history.getCreatedBy().getFullName() : null)
            .build();
    }

    private LoginActivityResponse toLoginActivityResponse(LoginActivity activity) {
        return LoginActivityResponse.builder()
            .id(activity.getId())
            .ipAddress(activity.getIpAddress())
            .userAgent(activity.getUserAgent())
            .deviceType(activity.getDeviceType())
            .location(activity.getLocation())
            .loginSuccess(activity.isLoginSuccess())
            .failureReason(activity.getFailureReason())
            .createdAt(activity.getCreatedAt())
            .build();
    }

    private Specification<User> buildSpecification(String search, UserRole role, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String searchPattern = "%" + search.toLowerCase() + "%";
                Predicate emailPredicate = cb.like(cb.lower(root.get("email")), searchPattern);
                Predicate namePredicate = cb.like(cb.lower(root.get("fullName")), searchPattern);
                Predicate phonePredicate = cb.like(cb.lower(root.get("phoneNumber")), searchPattern);
                predicates.add(cb.or(emailPredicate, namePredicate, phonePredicate));
            }

            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AdminUserListResponse toAdminUserListResponse(User user) {
        long orderCount = orderRepository.countByUser(user);

        return AdminUserListResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .phoneNumber(user.getPhoneNumber())
            .role(user.getRole())
            .active(user.isActive())
            .createdAt(user.getCreatedAt())
            .orderCount(orderCount)
            .build();
    }
}

