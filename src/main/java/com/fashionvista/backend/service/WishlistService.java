package com.fashionvista.backend.service;

import com.fashionvista.backend.dto.WishlistItemResponse;
import java.util.List;

public interface WishlistService {

    /**
     * Thêm sản phẩm vào wishlist của current user.
     */
    void addToWishlist(Long productId);

    /**
     * Xóa sản phẩm khỏi wishlist của current user.
     */
    void removeFromWishlist(Long productId);

    /**
     * Toggle trạng thái wishlist: nếu đã có thì xóa, chưa có thì thêm.
     */
    boolean toggleWishlist(Long productId);

    /**
     * Lấy danh sách wishlist của current user.
     */
    List<WishlistItemResponse> getMyWishlist();
}


