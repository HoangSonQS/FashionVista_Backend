# Hướng dẫn cấu hình Environment Variables

## File .env hoặc System Environment Variables

Tạo file `.env` trong thư mục `FashionVista_Backend` hoặc cấu hình system environment variables với các giá trị sau:

```bash
# Database Configuration
DB_USERNAME=postgres
DB_PASSWORD=your_password

# JWT Configuration
JWT_SECRET_KEY=your-secret-key-change-this-in-production-min-256-bits

# Cloudinary Configuration (for image uploads)
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=your-api-key
CLOUDINARY_API_SECRET=your-api-secret

# Email Configuration
# For Gmail: Bạn cần tạo App Password tại https://myaccount.google.com/apppasswords
# Không dùng mật khẩu Gmail thông thường
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password-16-chars

# Frontend URL (for email links)
FRONTEND_URL=http://localhost:5173

# VNPay Configuration
# Đăng ký tài khoản VNPay sandbox: https://sandbox.vnpayment.vn/
VNPAY_TMN_CODE=your-tmn-code
VNPAY_HASH_SECRET=your-hash-secret
VNPAY_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=http://localhost:8085/api/payments/vnpay/return
VNPAY_IPN_URL=http://localhost:8085/api/payments/vnpay/ipn
```

## Cách cấu hình Email (Gmail)

### Bước 1: Bật 2-Step Verification
1. Truy cập: https://myaccount.google.com/security
2. Bật "2-Step Verification" nếu chưa bật

### Bước 2: Tạo App Password
1. Truy cập: https://myaccount.google.com/apppasswords
2. Chọn "Mail" và "Other (Custom name)"
3. Nhập tên: "FashionVista Backend"
4. Click "Generate"
5. Copy mật khẩu 16 ký tự (không có khoảng trắng)

### Bước 3: Sử dụng App Password
- Đặt `MAIL_PASSWORD` = App Password (16 ký tự)
- **KHÔNG** dùng mật khẩu Gmail thông thường

Xem chi tiết tại file `EMAIL_SETUP.md`

