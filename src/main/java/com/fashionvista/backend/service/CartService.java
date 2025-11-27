package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.AddCartItemRequest;
import com.fashionvista.backend.dto.CartResponse;
import com.fashionvista.backend.dto.UpdateCartItemRequest;

public interface CartService {

    CartResponse getMyCart();

    CartResponse addItem(AddCartItemRequest request);

    CartResponse updateItem(Long itemId, UpdateCartItemRequest request);

    CartResponse removeItem(Long itemId);

    void clearCart();
}

