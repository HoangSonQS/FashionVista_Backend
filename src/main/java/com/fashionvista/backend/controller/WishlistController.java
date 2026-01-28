package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.WishlistItemResponse;
import com.fashionvista.backend.service.WishlistService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    /**
     * Test endpoint để kiểm tra controller có được load không.
     */
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("WishlistController is working!");
    }

    @PostMapping("/toggle")
    public ResponseEntity<Void> toggleWishlist(@RequestParam @NotNull Long productId) {
        boolean added = wishlistService.toggleWishlist(productId);
        return added ? ResponseEntity.status(HttpStatus.CREATED).build() : ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<Void> addToWishlist(@RequestParam @NotNull Long productId) {
        wishlistService.addToWishlist(productId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping
    public ResponseEntity<Void> removeFromWishlist(@RequestParam @NotNull Long productId) {
        wishlistService.removeFromWishlist(productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<WishlistItemResponse>> getMyWishlist() {
        return ResponseEntity.ok(wishlistService.getMyWishlist());
    }
}
