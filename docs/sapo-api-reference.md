# FashionVista — Sapo Integration API Reference

**Base URL:** `https://api.fashionvista.vn` (production) / `http://localhost:8085` (dev)  
**Prefix:** `/api/sapo/v1`  
**Auth:** Header `X-Api-Key: <provided_key>` on every request  
**Format:** JSON, UTF-8  
**Contact:** Provide `SAPO_API_KEY` value via secure channel (do not send by email)

---

## Response Format

### Success (single)
```json
{ "success": true, "data": { ... }, "message": null }
```

### Success (paginated list)
```json
{
  "success": true,
  "data": [...],
  "message": null,
  "pagination": { "page": 0, "size": 50, "total": 200, "totalPages": 4 }
}
```

### Error
```json
{ "success": false, "data": null, "message": "Error description" }
```

---

## HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | OK |
| 201 | Created |
| 400 | Bad request / validation error / insufficient stock |
| 401 | Missing or invalid `X-Api-Key` |
| 404 | Resource not found |
| 409 | Conflict (duplicate email, phone, or voucher code) |
| 500 | Server error |

---

## Products

### GET /api/sapo/v1/products
Get all products with variants, stock, and price for catalog sync.

**Query params:**
- `page` (int, default 0)
- `size` (int, default 50, max 200)
- `updatedAfter` (ISO-8601 datetime, optional) — only return products updated after this time

**Response `data[]`:**
```json
{
  "id": 1,
  "name": "Áo thun basic",
  "sku": "AT-BASIC",
  "slug": "ao-thun-basic",
  "status": "ACTIVE",
  "category": "Áo",
  "price": 299000,
  "compareAtPrice": 399000,
  "variants": [
    {
      "id": 10,
      "sku": "AT-BASIC-S-WHITE",
      "size": "S",
      "color": "WHITE",
      "price": 299000,
      "compareAtPrice": 399000,
      "stock": 15,
      "active": true
    }
  ],
  "updatedAt": "2026-06-18T10:30:00"
}
```

`status` values: `ACTIVE` | `INACTIVE` | `DRAFT`

---

### GET /api/sapo/v1/products/{sku}
Get product detail by product-level SKU.

**Path param:** `sku` — product SKU (e.g., `AT-BASIC`)

---

## Inventory

### GET /api/sapo/v1/inventory
Get all variant SKUs with stock counts. Use this for fast stock sync without pulling full product list.

**Query params:**
- `page` (int, default 0)
- `size` (int, default 100, max 500)
- `updatedAfter` (ISO-8601 datetime, optional)

**Response `data[]`:**
```json
{
  "sku": "AT-BASIC-S-WHITE",
  "productName": "Áo thun basic",
  "size": "S",
  "color": "WHITE",
  "stock": 15,
  "updatedAt": "2026-06-18T10:30:00"
}
```

---

### PUT /api/sapo/v1/inventory/{sku}
Update variant stock after a POS sale. **Stock is absolute value** (not delta) — send the current remaining count.

**Path param:** `sku` — variant SKU (e.g., `AT-BASIC-S-WHITE`)

**Request body:**
```json
{
  "stock": 12,
  "reason": "POS_SALE",
  "referenceId": "SAPO-ORDER-123"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| stock | int | Yes (≥0) | New absolute stock count |
| reason | string | No | e.g., `POS_SALE`, `ADJUSTMENT` |
| referenceId | string | No | Sapo order/reference ID for audit |

**Response:** Updated inventory item.

---

## Customers

### GET /api/sapo/v1/customers
Get customer list (role=CUSTOMER only, admins excluded).

**Query params:**
- `page`, `size` (max 200)
- `updatedAfter` (ISO-8601, optional)
- `email` (exact match, optional)

**Response `data[]`:**
```json
{
  "id": 5,
  "email": "nguyen@example.com",
  "fullName": "Nguyễn Văn A",
  "phoneNumber": "0901234567",
  "gender": "MALE",
  "dateOfBirth": "1995-03-15",
  "status": "ACTIVE",
  "createdAt": "2026-01-10T08:00:00",
  "updatedAt": "2026-06-10T12:00:00"
}
```

`gender` values: `MALE` | `FEMALE` | `OTHER` | `null`  
`status` values: `ACTIVE` | `INACTIVE` | `BANNED`

---

### GET /api/sapo/v1/customers/{id}
Get customer by ID.

---

### POST /api/sapo/v1/customers
Create a new customer (e.g., walk-in customer at POS who has no online account).

**Request body:**
```json
{
  "email": "moi@example.com",
  "fullName": "Trần Thị B",
  "phoneNumber": "0912345678",
  "gender": "FEMALE",
  "dateOfBirth": "1990-05-20"
}
```

| Field | Required |
|-------|----------|
| email | Yes |
| fullName | Yes |
| phoneNumber | Yes |
| gender | No |
| dateOfBirth | No |

Returns `409` if email or phoneNumber already exists.  
No welcome email is sent.

---

### PUT /api/sapo/v1/customers/{id}
Update customer info. Same request body as POST.

---

## Vouchers

### GET /api/sapo/v1/vouchers
Get voucher list.

**Query params:**
- `page`, `size` (max 200)
- `active` (boolean, default `true`)

**Response `data[]`:**
```json
{
  "id": 3,
  "code": "SALE10",
  "type": "PERCENT",
  "value": 10.00,
  "freeShipping": false,
  "minOrderTotal": 500000,
  "usageLimit": 100,
  "usedCount": 23,
  "active": true,
  "startsAt": "2026-06-01T00:00:00",
  "expiresAt": "2026-06-30T23:59:59",
  "available": true,
  "unavailableReason": null
}
```

`type` values: `PERCENT` | `FIXED_AMOUNT` | `FREESHIP`

`available: false` reasons: `Voucher is inactive`, `Voucher has not started yet`, `Voucher has expired`, `Voucher usage limit exceeded`

---

### GET /api/sapo/v1/vouchers/{code}
Get voucher by code. Case-insensitive.

---

### POST /api/sapo/v1/vouchers
Create a new voucher (e.g., from a POS promotion).

**Request body:**
```json
{
  "code": "POS-SUMMER",
  "type": "FIXED_AMOUNT",
  "value": 50000,
  "freeShipping": false,
  "minOrderTotal": 300000,
  "usageLimit": 50,
  "startsAt": "2026-07-01T00:00:00",
  "expiresAt": "2026-07-31T23:59:59"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| code | Yes | Stored as UPPERCASE |
| type | Yes | `PERCENT`, `FIXED_AMOUNT`, or `FREESHIP` |
| value | No | Required for PERCENT and FIXED_AMOUNT |
| freeShipping | No | Default false |
| minOrderTotal | No | Minimum subtotal required |
| usageLimit | No | Null = unlimited |
| startsAt | No | Null = active immediately |
| expiresAt | No | Null = never expires |

Returns `409` if code already exists.

---

### PUT /api/sapo/v1/vouchers/{code}/use
Mark voucher as used (increments `usedCount` by 1). Call this after a successful POS sale using the voucher.

**Request body (optional):**
```json
{ "referenceId": "SAPO-ORDER-456" }
```

Returns `409` if voucher is expired, inactive, or usage limit exceeded.

---

## Orders & Invoices

### GET /api/sapo/v1/orders
Get order list.

**Query params:**
- `page`, `size` (max 100)
- `status` (optional) — filter by status
- `createdAfter` (ISO-8601, optional)
- `createdBefore` (ISO-8601, optional)

`status` values: `PENDING` | `CONFIRMED` | `PROCESSING` | `SHIPPING` | `DELIVERED` | `RETURN_REQUESTED` | `RETURN_APPROVED` | `CANCELLED` | `REFUNDED`

---

### GET /api/sapo/v1/orders/{orderNumber}
Get full order detail. Use this to generate e-invoices — all required data is included.

**Response `data`:**
```json
{
  "orderNumber": "ORD-20260615-ABCD1234",
  "status": "DELIVERED",
  "paymentMethod": "COD",
  "paymentStatus": "PAID",
  "transactionId": null,
  "customer": {
    "id": 5,
    "fullName": "Nguyễn Văn A",
    "email": "nguyen@example.com",
    "phoneNumber": "0901234567"
  },
  "shippingAddress": "{\"type\":\"POS\",\"note\":\"Bán tại quầy Sapo\"}",
  "items": [
    {
      "sku": "AT-BASIC-S-WHITE",
      "productName": "Áo thun basic",
      "variantLabel": "S / WHITE",
      "quantity": 2,
      "unitPrice": 299000,
      "subtotal": 598000
    }
  ],
  "subtotal": 598000,
  "shippingFee": 0,
  "discount": 59800,
  "voucherCode": "SALE10",
  "total": 538200,
  "trackingNumber": null,
  "source": "SAPO_POS",
  "createdAt": "2026-06-15T14:30:00",
  "updatedAt": "2026-06-18T09:00:00"
}
```

`paymentMethod` values: `COD` | `BANK_TRANSFER` | `VNPAY` | `MOMO`  
`paymentStatus` values: `PENDING` | `PAID` | `FAILED` | `REFUNDED`  
`source`: `SAPO_POS` for POS orders, `null` for online orders

---

### POST /api/sapo/v1/orders
Create a POS order in FashionVista. Stock is automatically decremented.

**Request body:**
```json
{
  "customerId": 5,
  "items": [
    { "sku": "AT-BASIC-S-WHITE", "quantity": 1, "unitPrice": 299000 }
  ],
  "paymentMethod": "COD",
  "voucherCode": "SALE10",
  "shippingFee": 0,
  "note": "Bán tại quầy - POS"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| customerId | Yes | Must exist in system |
| items | Yes (≥1) | Each item: sku, quantity (≥1), unitPrice |
| paymentMethod | Yes | `COD`, `BANK_TRANSFER`, `VNPAY`, `MOMO` |
| voucherCode | No | Validated and applied if provided |
| shippingFee | No | Default 0 for POS |
| note | No | Internal note |

**Behavior:**
- Order `source` is automatically set to `"SAPO_POS"`
- `paymentStatus` is set to `PAID` immediately (POS = payment confirmed)
- `status` starts as `CONFIRMED`
- Stock is decremented atomically; returns `400` if any SKU has insufficient stock
- If voucher is provided, `usedCount` is incremented

Returns `400` if insufficient stock, `404` if customer or SKU not found.

---

### PUT /api/sapo/v1/orders/{orderNumber}/status
Update order status (e.g., mark as DELIVERED after POS handoff).

**Request body:**
```json
{
  "status": "DELIVERED",
  "note": "Giao hàng thành công tại quầy"
}
```

Valid `status` transitions follow FashionVista business rules. Any `OrderStatus` value is accepted by the API but ensure transitions make logical sense.
