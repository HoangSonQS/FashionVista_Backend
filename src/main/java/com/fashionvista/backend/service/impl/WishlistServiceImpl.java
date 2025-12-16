package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.WishlistItemResponse;
import com.fashionvista.backend.entity.Product;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.entity.Wishlist;
import com.fashionvista.backend.repository.ProductRepository;
import com.fashionvista.backend.repository.WishlistRepository;
import com.fashionvista.backend.service.UserContextService;
import com.fashionvista.backend.service.WishlistService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final UserContextService userContextService;

    @Override
    @Transactional
    public void addToWishlist(Long productId) {
        User user = userContextService.getCurrentUser();
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));

        boolean exists = wishlistRepository.findByUserOrderByCreatedAtDesc(user).stream()
            .anyMatch(item -> item.getProduct().getId().equals(productId));
        if (exists) {
            return;
        }

        Wishlist wishlist = Wishlist.builder()
            .user(user)
            .product(product)
            .build();
        wishlistRepository.save(wishlist);
    }

    @Override
    @Transactional
    public void removeFromWishlist(Long productId) {
        User user = userContextService.getCurrentUser();
        List<Wishlist> items = wishlistRepository.findByUserOrderByCreatedAtDesc(user);
        items.stream()
            .filter(item -> item.getProduct().getId().equals(productId))
            .forEach(wishlistRepository::delete);
    }

    @Override
    @Transactional
    public boolean toggleWishlist(Long productId) {
        User user = userContextService.getCurrentUser();
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm."));

        List<Wishlist> items = wishlistRepository.findByUserOrderByCreatedAtDesc(user);
        Wishlist existing = items.stream()
            .filter(item -> item.getProduct().getId().equals(productId))
            .findFirst()
            .orElse(null);

        if (existing != null) {
            wishlistRepository.delete(existing);
            return false;
        } else {
            Wishlist wishlist = Wishlist.builder()
                .user(user)
                .product(product)
                .build();
            wishlistRepository.save(wishlist);
            return true;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<WishlistItemResponse> getMyWishlist() {
        User user = userContextService.getCurrentUser();
        return wishlistRepository.findByUserOrderByCreatedAtDesc(user).stream()
            .map(item -> {
                Product product = item.getProduct();
                String thumbnailUrl = product.getImages().stream()
                    .filter(image -> image.isPrimary() && image.getUrl() != null)
                    .map(image -> image.getUrl())
                    .findFirst()
                    .orElseGet(() -> product.getImages().stream()
                        .map(image -> image.getUrl())
                        .findFirst()
                        .orElse(null));

                return WishlistItemResponse.builder()
                    .id(item.getId())
                    .productId(product.getId())
                    .productName(product.getName())
                    .productSlug(product.getSlug())
                    .thumbnailUrl(thumbnailUrl)
                    .price(product.getPrice())
                    .compareAtPrice(product.getCompareAtPrice())
                    .build();
            })
            .toList();
    }
}


