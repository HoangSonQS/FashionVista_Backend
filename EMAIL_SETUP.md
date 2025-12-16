# Hướng dẫn cấu hình Email Service

## 1. Cấu hình Gmail (Khuyến nghị cho development)

### Bước 1: Bật 2-Step Verification
1. Truy cập: https://myaccount.google.com/security
2. Bật "2-Step Verification" nếu chưa bật

### Bước 2: Tạo App Password
1. Truy cập: https://myaccount.google.com/apppasswords
2. Chọn "Mail" và "Other (Custom name)"
3. Nhập tên: "FashionVista Backend"
4. Click "Generate"
5. Copy mật khẩu 16 ký tự (không có khoảng trắng)

### Bước 3: Cấu hình trong application.properties hoặc .env

```properties
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=xxxx xxxx xxxx xxxx  # App Password 16 ký tự
```

**Lưu ý:** 
- Không dùng mật khẩu Gmail thông thường
- Phải dùng App Password (16 ký tự)
- Nếu không có App Password, sẽ bị lỗi "Username and Password not accepted"

## 2. Cấu hình Email khác (Outlook, Yahoo, etc.)

### Outlook/Hotmail
```properties
MAIL_HOST=smtp-mail.outlook.com
MAIL_PORT=587
MAIL_USERNAME=your-email@outlook.com
MAIL_PASSWORD=your-password
```

### Yahoo Mail
```properties
MAIL_HOST=smtp.mail.yahoo.com
MAIL_PORT=587
MAIL_USERNAME=your-email@yahoo.com
MAIL_PASSWORD=your-app-password
```

## 3. Test Email Service

Sau khi cấu hình, khởi động ứng dụng và test bằng cách:
1. Đăng ký tài khoản mới → Email verification sẽ được gửi
2. Đặt đơn hàng → Email xác nhận đơn hàng sẽ được gửi

## 4. Troubleshooting

### Lỗi: "Authentication failed"
- Kiểm tra App Password đã được tạo chưa
- Đảm bảo 2-Step Verification đã bật
- Kiểm tra username và password đúng chưa

### Lỗi: "Connection timeout"
- Kiểm tra firewall/antivirus có chặn port 587 không
- Thử đổi port sang 465 (SSL) hoặc 25

### Lỗi: "Could not convert socket to TLS"
- Kiểm tra `spring.mail.properties.mail.smtp.starttls.enable=true`
- Thử đổi sang SSL: `spring.mail.properties.mail.smtp.ssl.enable=true` và port 465

## 5. Production Setup

Cho production, nên sử dụng:
- **SendGrid** (khuyến nghị)
- **Amazon SES**
- **Mailgun**
- **Postmark**

Cấu hình tương tự, chỉ cần thay `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` theo provider.

