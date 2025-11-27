package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.AddCartItemRequest;
import com.fashionvista.backend.dto.CartItemResponse;
import com.fashionvista.backend.dto.CartResponse;
import com.fashionvista.backend.dto.UpdateCartItemRequest;
import com.fashionvista.backend.entity.Cart;
import com.fashionvista.backend.entity.CartItem;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.repository.CartRepository;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fashionvista.backend.service.CartService;
import com.fashionvista.backend.service.UserContextService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserContextService userContextService;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getMyCart() {
        Cart cart = getOrCreateCart();
        return toCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse addItem(AddCartItemRequest request) {
        Cart cart = getOrCreateCart();
        ProductVariant variant = productVariantRepository.findBySku(request.getVariantSku())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biến thể sản phẩm."));

        if (!variant.isActive() || variant.getStock() < request.getQuantity()) {
            throw new IllegalArgumentException("Sản phẩm không đủ tồn kho.");
        }

        CartItem item = cart.getItems().stream()
            .filter(ci -> ci.getVariant() != null && ci.getVariant().getId().equals(variant.getId()))
            .findFirst()
            .orElseGet(() -> {
                CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(variant.getProduct())
                    .variant(variant)
                    .price(resolveVariantPrice(variant))
                    .quantity(0)
                    .build();
                cart.getItems().add(newItem);
                return newItem;
            });

        item.setQuantity(item.getQuantity() + request.getQuantity());
        item.setPrice(resolveVariantPrice(variant));
        cartRepository.save(cart);
        return toCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateItem(Long itemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateCart();
        CartItem item = cart.getItems().stream()
            .filter(ci -> ci.getId().equals(itemId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm trong giỏ."));

        ProductVariant variant = item.getVariant();
        if (variant.getStock() < request.getQuantity()) {
            throw new IllegalArgumentException("Sản phẩm không đủ tồn kho.");
        }

        item.setQuantity(request.getQuantity());
        item.setPrice(resolveVariantPrice(variant));
        cartRepository.save(cart);
        return toCartResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long itemId) {
        Cart cart = getOrCreateCart();
        cart.getItems().removeIf(ci -> ci.getId().equals(itemId));
        cartRepository.save(cart);
        return toCartResponse(cart);
    }

    @Override
    @Transactional
    public void clearCart() {
        Cart cart = getOrCreateCart();
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    private Cart getOrCreateCart() {
        var currentUser = userContextService.getCurrentUser();
        return cartRepository.findByUser(currentUser)
            .orElseGet(() -> cartRepository.save(
                Cart.builder()
                    .user(currentUser)
                    .build()
            ));
    }

    private BigDecimal resolveVariantPrice(ProductVariant variant) {
        return variant.getPrice() != null ? variant.getPrice() : variant.getProduct().getPrice();
    }

    private CartResponse toCartResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
            .map(item -> CartItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .variantId(item.getVariant() != null ? item.getVariant().getId() : null)
                .productName(item.getProduct().getName())
                .productSlug(item.getProduct().getSlug())
                .thumbnailUrl(item.getProduct().getImages().stream()
                    .findFirst()
                    .map(image -> image.getUrl())
                    .orElse(null))
                .size(item.getVariant() != null ? item.getVariant().getSize() : null)
                .color(item.getVariant() != null ? item.getVariant().getColor() : null)
                .quantity(item.getQuantity())
                .unitPrice(item.getPrice())
                .subtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .build())
            .toList();

        BigDecimal subtotal = items.stream()
            .map(CartItemResponse::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shippingFee = subtotal.compareTo(BigDecimal.valueOf(2000000)) >= 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(30000);

        return CartResponse.builder()
            .id(cart.getId())
            .items(items)
            .subtotal(subtotal)
            .shippingFee(shippingFee)
            .total(subtotal.add(shippingFee))
            .build();
    }
}

