# Context
Filename: phase2-ghn-shipping.md
Created: 2025-12-05 16:30
Author: AI
Protocol: RIPER-5 + Multi-Dim + Agent + AI-Dev Guide

# Task Description
Implement dynamic GHN shipping fee calculation and integrate into checkout.

# Project Overview
FashionVista (Spring Boot + React/TS). Need live shipping fee based on address via GHN API.

---
# Analysis (Research)
TBD

# Proposed Solutions (Innovation)
## Plan A:
- Principle: Backend proxy GHN fee API; frontend uses saved addressId to request fee.
- Steps: add GHN client config (token, shopId, baseUrl), service method computeFee(address), controller endpoint `/api/shipping/fee?addressId=...&service=STANDARD|FAST|EXPRESS`.
- Risks: need GHN credentials; address must include district/ward codes.

## Plan B:
- Principle: Frontend calls GHN directly.
- Risks: expose token; CORS; not recommended.

## Recommended Plan
Plan A (backend proxy).

# Implementation Plan (Planning)
Implementation Checklist:
1. Backend: add GHN config (token, shopId, baseUrl), DTOs for fee request/response.
2. Backend: service `ShippingService.calculateFee(addressId, serviceCode)` fetch address (user), call GHN fee API, map to response.
3. Backend: controller endpoint `GET /api/shipping/fee` (auth) with addressId & service.
4. Frontend: in CheckoutPage, on address/service change call `shippingService.getFee(addressId, service)` update shippingFee and total; handle fallback if fee fails.
5. Optional: support manual address (no saved address) by sending ward/district codes directly.
6. Tests/manual: with valid GHN token, verify fee returned; fallback to static fee if GHN error.

# Current Step
Executing: "Done. Next: Wishlist polish"

# Task Progress

# Final Review
TBD

