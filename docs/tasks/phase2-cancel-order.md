# Context
Filename: phase2-cancel-order.md
Created: 2025-12-05 15:50
Author: AI
Protocol: RIPER-5 + Multi-Dim + Agent + AI-Dev Guide

# Task Description
Implement user hủy đơn (cancel order) API + UI, with trạng thái rules.

# Project Overview
FashionVista (Spring Boot + React/TS). User orders, wishlist, VNPay, voucher, reviews, etc.

---
# Analysis (Research)
TBD

# Proposed Solutions (Innovation)
## Plan A:
- Principle: REST endpoint for cancel with status guard; update payment/stock if needed.
- Steps: add service method, controller endpoint, status check allowed (e.g., PENDING/PROCESSING), record status, optionally restock.
- Risks: status transition conflicts; payments partial.

## Plan B:
- Principle: Soft cancel request (flag) requiring admin approval.
- Steps: add cancel_request flag; admin approves.
- Risks: more UI; slower delivery.

## Recommended Plan
Plan A (faster, matches requirement).

# Implementation Plan (Planning)
Implementation Checklist:
1. Backend: define allowed cancel statuses and validation.
2. Backend: service method cancelOrder(user, orderId) with status update, optional restock, payment handling note.
3. Backend: controller endpoint POST /api/orders/{id}/cancel (auth).
4. Frontend: user orders list/detail add "Hủy đơn" button when status allowed; confirm dialog; call API; refresh.
5. Tests/manual: create order, cancel allowed status; ensure forbidden status returns error.

# Current Step
Executing: "Done. Next: GHN phí ship động"

# Task Progress
* 2025-12-05 15:55
  * Step: 1. Backend: define allowed cancel statuses and validation
  * Changes: Đọc OrderStatus enum; xác định trạng thái hiện có: PENDING, CONFIRMED, PROCESSING, SHIPPING, DELIVERED, CANCELLED, REFUNDED.
  * Summary: Sẵn sàng thêm logic huỷ với guard theo trạng thái.
  * Status: completed
* 2025-12-05 16:25
  * Step: Hủy đơn hàng
  * Changes: Thêm cancelMyOrder (backend), endpoint /orders/{orderNumber}/cancel, trả tồn kho; frontend UserOrderDetail nút hủy đơn.
  * Summary: Người dùng có thể hủy đơn ở trạng thái PENDING/CONFIRMED/PROCESSING.
  * Status: completed

# Final Review
TBD

