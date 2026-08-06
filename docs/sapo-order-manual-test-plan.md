# Test Plan Thủ Công: Luồng Order ↔ Sapo

## Bối cảnh

Yêu cầu ban đầu: kiểm tra xem server có hoạt động tốt với Sapo ở phần order hay không.

Đã hoàn thành bằng automated test (Mockito, không cần DB thật):
- `src/test/java/com/fashionvista/backend/service/impl/AdminOrderServiceImplTest.java`
  - Fallback khi không có auth context (webhook gọi vào không có JWT) → không throw exception, log ghi nhận `"Admin"` (không có email).
  - `sapoOrderSyncService.pushOrder(orderId)` chỉ được gọi đúng 1 lần khi đơn chuyển **sang** `CONFIRMED` (không gọi lại nếu đơn đã `CONFIRMED` từ trước).
- `src/test/java/com/fashionvista/backend/integration/sapo/webhook/SapoWebhookControllerTest.java`
  - Verify HMAC hợp lệ/không hợp lệ cho webhook `order-fulfilled`/`order-cancelled`.

**Phát hiện quan trọng trong lúc test**: DB test H2 hiện tại (`src/test/resources/application-test.properties`, `ddl-auto=create-drop`, dialect H2 mặc định) **không tạo được** bảng `orders`, `products`, `return_requests` (cột `jsonb` chỉ Postgres hỗ trợ), `reviews` (cú pháp mảng), `vouchers` (từ khóa `value` bị H2 coi là reserved word). Hibernate chỉ log `WARN`, không fail context, nên lỗi này tồn tại từ trước, chưa từng bị phát hiện vì test `@SpringBootTest` duy nhất trong repo không đụng tới các bảng này. → **Không có test tự động nào trong repo từng thực sự ghi/đọc bảng `orders` qua Spring context thật.** Đây là lý do các mục dưới đây phải test thủ công trên môi trường có Postgres thật (staging/production).

Các mục dưới đây **không thể verify bằng automated test hiện có** và cần bạn tự chạy tay.

---

## 1. Push order lên Sapo khi xác nhận đơn (CONFIRMED)

**Code liên quan**: `AdminOrderServiceImpl.updateOrderStatus()` → `SapoOrderSyncService.pushOrder()` (chạy `@Async`) → `SapoApiClient.createOrder()` gọi `POST /admin/orders.json`.

**Các bước**:
1. Trên staging/production, tạo 1 đơn hàng test (PENDING), có ít nhất 1 sản phẩm đã có `sapoVariantId` (đã sync với Sapo trước đó).
2. Qua admin dashboard hoặc API, cập nhật status đơn → `CONFIRMED`.
3. Theo dõi log server, tìm `Sapo order sync failed for order id=...` (nếu fail) hoặc kiểm tra field `sapoSyncStatus` của đơn trong DB → phải chuyển thành `SYNCED`, có `sapoOrderId` và `sapoSyncedAt`.
4. Vào Sapo Admin (giao diện quản lý đơn hàng), xác nhận đơn mới xuất hiện với đúng: danh sách sản phẩm/số lượng/giá, `financial_status` (`paid` nếu `paymentStatus=PAID`, ngược lại `pending`), `source_name=web`, note = mã đơn FV (`orderNumber`).

**⚠️ Trước khi test, đối chiếu với tài liệu chính thức của Sapo** (`/admin/orders.json` — Sapo Order API): xác nhận field `line_items[].variant_id`, `line_items[].sku`, `financial_status`, `source_name`, `customer.email/phone` đúng tên và định dạng Sapo yêu cầu — `docs/sapo-api-reference.md` là spec inbound của FV, **không phải** spec thật của Sapo, nên không dùng để verify bước này (theo Sapo Integration Rule trong `CLAUDE.md`).

**Pass/Fail**: Pass nếu đơn xuất hiện đúng trên Sapo và `sapoSyncStatus=SYNCED`. Nếu Sapo trả lỗi 4xx/5xx, ghi lại response body để đối chiếu field nào sai so với docs Sapo.

---

## 2. Sản phẩm/variant chưa sync sẽ tự động sync trước khi push order

**Code liên quan**: `SapoOrderSyncService.syncUnsyncedVariants()`.

**Các bước**:
1. Tạo đơn hàng test với 1 sản phẩm **chưa từng sync Sapo** (`sapoVariantId = null`).
2. Xác nhận đơn (CONFIRMED) như mục 1.
3. Kiểm tra: sản phẩm phải được đẩy lên Sapo trước (log `Sapo product sync` hoặc bảng sản phẩm có `sapoVariantId` mới), sau đó đơn hàng mới được push với `variant_id` đã có.

**Pass/Fail**: Pass nếu variant có `sapoVariantId` sau bước 3 và đơn hàng trên Sapo tham chiếu đúng variant đó (không phải `null`).

---

## 3. Retry job cho các đơn sync thất bại

**Code liên quan**: `SapoOrderSyncService.retryFailedSyncs()`, chạy mỗi giờ (`@Scheduled(cron = "0 0 * * * ?")`), chỉ retry đơn có `sapoSyncStatus=FAILED` và `status=CONFIRMED`.

**Các bước**:
1. Cố tình tạo 1 đơn `sapoSyncStatus=FAILED` (ví dụ tắt tạm kết nối tới Sapo, hoặc set field trực tiếp trong DB trên staging).
2. Đợi tới đầu giờ tiếp theo (hoặc trigger job thủ công nếu có endpoint/console để gọi).
3. Kiểm tra log/DB: `sapoSyncStatus` phải chuyển `SYNCED` nếu Sapo giờ đã nhận được, hoặc vẫn `FAILED` với `sapoSyncError` cập nhật lỗi mới nhất.

**Pass/Fail**: Pass nếu job thực sự chạy đúng giờ và cập nhật lại trạng thái đơn.

---

## 4. Webhook `order-fulfilled` từ Sapo thật

**Code liên quan**: `SapoWebhookController.handleOrderFulfilled()` → `handleOrderStatusWebhook()` → tìm đơn theo `sapoOrderId` → `adminOrderService.updateOrderStatus(..., DELIVERED, ...)`.

**Các bước**:
1. Đảm bảo có 1 đơn FV đã `SYNCED` với Sapo (có `sapoOrderId`), đang ở trạng thái khác `DELIVERED`.
2. Trên Sapo, đánh dấu đơn tương ứng là "đã giao"/fulfilled (theo đúng thao tác thật trên UI Sapo, không giả lập).
3. Xác nhận Sapo gọi webhook thật tới `POST https://<domain-production>/webhook/sapo/order-fulfilled` với header `X-Sapo-Hmac-SHA256` — kiểm tra qua log server hoặc dashboard webhook delivery của Sapo (nếu có).
4. Kiểm tra đơn FV: `status` → `DELIVERED`; nếu `paymentMethod=COD` và trước đó `paymentStatus=PENDING` → tự động chuyển `PAID`, có bản ghi lịch sử "Tự động cập nhật khi đơn COD đã giao", và khách hàng được cộng điểm loyalty (`LoyaltyService.awardPointsForOrder`).
5. Kiểm tra `order.notes`: log phải ghi actor là `"Admin"` (không có email) vì webhook không có auth context — đây chính là hành vi mà `AdminOrderServiceImplTest.updateOrderStatus_UserContextThrows_FallsBackToAdminLogWithoutThrowing` đã verify bằng mock; bước này verify nó đúng khi chạy thật với DB thật.

**⚠️ Payload thật từ Sapo**: `SapoWebhookOrderPayload` hiện chỉ đọc 2 field: `id` và `order_number` (bỏ qua field lạ nhờ `@JsonIgnoreProperties(ignoreUnknown = true)`). Đối chiếu với payload thật Sapo gửi (log `rawBody` nếu cần thêm log tạm giống bên inventory webhook) để chắc chắn `id` đúng là field Sapo dùng để định danh đơn — nếu Sapo dùng field khác (ví dụ `order_id`), `orderRepository.findBySapoOrderId(payload.getId())` sẽ luôn rỗng và webhook sẽ no-op im lặng (trả `200 OK` nhưng không làm gì).

**Pass/Fail**: Pass nếu đơn FV thật sự chuyển `DELIVERED` sau khi Sapo xác nhận giao hàng, không phải chỉ trả `200 OK`.

---

## 5. Webhook `order-cancelled` từ Sapo thật

Tương tự mục 4, nhưng hủy đơn trên Sapo → xác nhận đơn FV chuyển `status=CANCELLED`, notes ghi nhận đúng, không có logic tự động thanh toán/loyalty nào chạy nhầm (chỉ áp dụng cho `DELIVERED`).

---

## 6. Chữ ký HMAC sai / thiếu trên webhook thật

**Các bước**:
1. Gửi 1 request giả tới `/webhook/sapo/order-fulfilled` với header `X-Sapo-Hmac-SHA256` sai hoặc thiếu (dùng Postman/curl từ máy ngoài, **không phải từ Sapo**).
2. Xác nhận server trả `401 Unauthorized` và đơn hàng liên quan (nếu payload trỏ tới đơn thật) **không** bị thay đổi.

**Pass/Fail**: Pass nếu request giả mạo bị chặn hoàn toàn (không lộ thêm thông tin qua response, không thay đổi DB).

---

## 7. Email thông báo khách hàng khi đổi trạng thái

**Code liên quan**: `emailService.sendOrderStatusUpdateEmail()` trong `updateOrderStatus()`, chỉ chạy khi `request.getNotifyCustomer() == true`.

**Các bước**:
1. Cập nhật status đơn qua admin dashboard với tùy chọn "thông báo khách hàng" = bật.
2. Xác nhận khách hàng nhận được email đúng nội dung (trạng thái cũ → mới).

**Pass/Fail**: Pass nếu email đến hộp thư khách hàng, không rơi vào spam, nội dung đúng trạng thái.

---

## 8. Xác nhận schema Postgres thật hỗ trợ đúng các cột jsonb

Vì gap ở H2 phát hiện được lúc viết test (mục "Phát hiện quan trọng" ở đầu file), cần xác nhận thủ công rằng trên Postgres thật (staging/production), các thao tác sau **thực sự hoạt động** (không chỉ "context load được"):
1. Tạo đơn hàng mới với `shippingAddress`/`billingAddress` (cột `jsonb`) — đọc lại từ DB, xác nhận dữ liệu JSON không bị mất/méo field.
2. Tạo/sửa sản phẩm với `attributes` (cột `jsonb`) — tương tự.
3. Tạo 1 review có rating/text, xác nhận không lỗi khi insert (bảng `reviews` dùng cú pháp mảng `text[][]` mà H2 không hỗ trợ).
4. Tạo/sửa 1 voucher — xác nhận cột `value` insert/update bình thường (H2 coi `value` là từ khóa dự trữ, Postgres thì không).

**Pass/Fail**: Pass nếu cả 4 thao tác trên chạy được trên Postgres thật không lỗi — nếu có lỗi, đây là bug thật cần fix riêng, không phải do Sapo.

---

## Tổng kết phạm vi

| Hành vi | Cách verify |
|---|---|
| Fallback log khi không có auth context | ✅ Automated (Mockito) |
| `pushOrder()` chỉ gọi khi chuyển sang CONFIRMED | ✅ Automated (Mockito) |
| HMAC verify đúng/sai cho webhook | ✅ Automated (`SapoWebhookControllerTest`) |
| Payload thật Sapo gửi có đúng field FV mong đợi không | ❌ Cần test thủ công (mục 1, 4) |
| DB thật (Postgres) lưu đúng jsonb/reviews/vouchers | ❌ Cần test thủ công (mục 8) |
| Webhook thật từ Sapo tới đúng server production | ❌ Cần test thủ công (mục 4, 5, 6) |
| Email thật gửi đi | ❌ Cần test thủ công (mục 7) |
