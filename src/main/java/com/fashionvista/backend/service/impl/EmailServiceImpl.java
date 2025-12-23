package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.service.BrevoMailSender;
import com.fashionvista.backend.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.springframework.util.StringUtils;

/**
 * Implementation của EmailService
 * Sử dụng Spring Mail và Thymeleaf để gửi email HTML
 */
@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine emailTemplateEngine;
    private final OrderRepository orderRepository;
    private final BrevoMailSender brevoMailSender;

    public EmailServiceImpl(
        JavaMailSender mailSender,
        @Qualifier("emailTemplateEngine") SpringTemplateEngine emailTemplateEngine,
        OrderRepository orderRepository,
        BrevoMailSender brevoMailSender
    ) {
        this.mailSender = mailSender;
        this.emailTemplateEngine = emailTemplateEngine;
        this.orderRepository = orderRepository;
        this.brevoMailSender = brevoMailSender;
    }

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.name:FashionVista}")
    private String appName;

    @Override
    public void sendVerificationEmail(User user, String verificationToken) {
        if (brevoMailSender.isConfigured()) {
            String html = buildVerificationHtml(user, verificationToken);
            brevoMailSender.sendEmail(user.getEmail(), user.getFullName(), "Xác thực tài khoản " + appName, html);
            return;
        }
        if (!isSmtpConfigured()) {
            log.warn("Bỏ qua gửi email xác thực vì SMTP chưa cấu hình.");
            return;
        }
        try {
            String htmlContent = buildVerificationHtml(user, verificationToken);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            try {
                helper.setFrom(fromEmail, appName);
            } catch (UnsupportedEncodingException ex) {
                // Fallback: nếu lỗi encoding display name thì chỉ dùng email thuần
                helper.setFrom(fromEmail);
            }
            helper.setTo(user.getEmail());
            helper.setSubject("Xác thực tài khoản " + appName);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Đã gửi email xác thực đến: {}", user.getEmail());
        } catch (Exception e) {
            // Bao phủ cả MessagingException lẫn các MailException (auth, kết nối...)
            log.error("Lỗi khi gửi email xác thực đến: {}", user.getEmail(), e);
            // Không throw exception để không làm gián đoạn flow đăng ký
        }
    }

    @Override
    @Async("emailTaskExecutor")
    @Transactional(readOnly = true)
    public void sendOrderConfirmationEmail(Order order) {
        if (brevoMailSender.isConfigured()) {
            String html = buildOrderConfirmationHtml(order);
            brevoMailSender.sendEmail(order.getUser().getEmail(), order.getUser().getFullName(), "Xác nhận đơn hàng #" + order.getOrderNumber(), html);
            return;
        }
        if (!isSmtpConfigured()) {
            log.warn("Bỏ qua gửi email xác nhận đơn hàng vì SMTP chưa cấu hình.");
            return;
        }
        try {
            String htmlContent = buildOrderConfirmationHtml(order);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            try {
                helper.setFrom(fromEmail, appName);
            } catch (UnsupportedEncodingException ex) {
                helper.setFrom(fromEmail);
            }
            helper.setTo(order.getUser().getEmail());
            helper.setSubject("Xác nhận đơn hàng #" + order.getOrderNumber());
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Đã gửi email xác nhận đơn hàng đến: {}", order.getUser().getEmail());
        } catch (MessagingException e) {
            log.error("Lỗi khi gửi email xác nhận đơn hàng đến: {}", order.getUser().getEmail(), e);
        }
    }

    /**
     * Gửi email cập nhật trạng thái đơn hàng dưới dạng async để
     * không chặn luồng xử lý API admin (tránh cảm giác chậm 5-10s).
     */
    @Override
    @Async("emailTaskExecutor")
    @Transactional(readOnly = true)
    public void sendOrderStatusUpdateEmail(Order order, String oldStatus, String newStatus) {
        if (brevoMailSender.isConfigured()) {
            Order hydratedOrder = orderRepository.findById(order.getId())
                .orElse(order);
            String html = buildOrderStatusUpdateHtml(hydratedOrder, oldStatus, newStatus);
            brevoMailSender.sendEmail(hydratedOrder.getUser().getEmail(), hydratedOrder.getUser().getFullName(), "Cập nhật trạng thái đơn hàng #" + hydratedOrder.getOrderNumber(), html);
            return;
        }
        if (!isSmtpConfigured()) {
            log.warn("Bỏ qua gửi email cập nhật trạng thái đơn hàng vì SMTP chưa cấu hình.");
            return;
        }
        try {
            // Tránh dùng entity có thể còn lazy proxy ngoài transaction gốc:
            // nạp lại Order trong transaction riêng của async thread.
            Order hydratedOrder = orderRepository.findById(order.getId())
                .orElse(order);

            String htmlContent = buildOrderStatusUpdateHtml(hydratedOrder, oldStatus, newStatus);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            try {
                helper.setFrom(fromEmail, appName);
            } catch (UnsupportedEncodingException ex) {
                helper.setFrom(fromEmail);
            }
            helper.setTo(hydratedOrder.getUser().getEmail());
            helper.setSubject("Cập nhật trạng thái đơn hàng #" + hydratedOrder.getOrderNumber());
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Đã gửi email cập nhật trạng thái đơn hàng đến: {}", hydratedOrder.getUser().getEmail());
        } catch (MessagingException e) {
            log.error("Lỗi khi gửi email cập nhật trạng thái đơn hàng đến: {}", order.getUser().getEmail(), e);
        }
    }

    @Override
    public void sendPasswordResetEmail(User user, String resetToken) {
        if (brevoMailSender.isConfigured()) {
            String html = buildPasswordResetHtml(user, resetToken);
            brevoMailSender.sendEmail(user.getEmail(), user.getFullName(), "Đặt lại mật khẩu " + appName, html);
            return;
        }
        if (!isSmtpConfigured()) {
            log.warn("Bỏ qua gửi email reset mật khẩu vì SMTP chưa cấu hình.");
            return;
        }
        try {
            String htmlContent = buildPasswordResetHtml(user, resetToken);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            try {
                helper.setFrom(fromEmail, appName);
            } catch (UnsupportedEncodingException ex) {
                helper.setFrom(fromEmail);
            }
            helper.setTo(user.getEmail());
            helper.setSubject("Đặt lại mật khẩu " + appName);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Đã gửi email reset mật khẩu đến: {}", user.getEmail());
        } catch (Exception e) {
            // Bao phủ MessagingException + MailException để không làm fail API
            log.error("Lỗi khi gửi email reset mật khẩu đến: {}", user.getEmail(), e);
        }
    }

    @Override
    public void sendPaymentConfirmationEmail(Order order) {
        if (brevoMailSender.isConfigured()) {
            String html = buildPaymentConfirmationHtml(order);
            brevoMailSender.sendEmail(order.getUser().getEmail(), order.getUser().getFullName(), "Xác nhận thanh toán đơn hàng #" + order.getOrderNumber(), html);
            return;
        }
        if (!isSmtpConfigured()) {
            log.warn("Bỏ qua gửi email xác nhận thanh toán vì SMTP chưa cấu hình.");
            return;
        }
        try {
            String htmlContent = buildPaymentConfirmationHtml(order);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            try {
                helper.setFrom(fromEmail, appName);
            } catch (UnsupportedEncodingException ex) {
                helper.setFrom(fromEmail);
            }
            helper.setTo(order.getUser().getEmail());
            helper.setSubject("Xác nhận thanh toán đơn hàng #" + order.getOrderNumber());
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Đã gửi email xác nhận thanh toán đến: {}", order.getUser().getEmail());
        } catch (MessagingException e) {
            log.error("Lỗi khi gửi email xác nhận thanh toán đến: {}", order.getUser().getEmail(), e);
        }
    }

    /**
     * Format số tiền theo định dạng VNĐ
     */
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "0 ₫";
        }
        return String.format("%,d ₫", amount.longValue());
    }

    /**
     * Dịch trạng thái đơn hàng sang tiếng Việt
     */
    private String translateOrderStatus(String status) {
        return switch (status.toUpperCase()) {
            case "PENDING" -> "Chờ xử lý";
            case "CONFIRMED" -> "Đã xác nhận";
            case "PROCESSING" -> "Đang xử lý";
            case "SHIPPING" -> "Đang giao hàng";
            case "DELIVERED" -> "Đã giao hàng";
            case "CANCELLED" -> "Đã hủy";
            case "REFUNDED" -> "Đã hoàn tiền";
            default -> status;
        };
    }

    /**
     * Dịch phương thức thanh toán sang tiếng Việt
     */
    private String translatePaymentMethod(String method) {
        return switch (method.toUpperCase()) {
            case "COD" -> "Thanh toán khi nhận hàng (COD)";
            case "VNPAY" -> "VNPay";
            case "MOMO" -> "Ví MoMo";
            case "BANK_TRANSFER" -> "Chuyển khoản ngân hàng";
            default -> method;
        };
    }

    /**
     * Kiểm tra SMTP đã cấu hình đầy đủ để gửi mail hay chưa.
     */
    private boolean isSmtpConfigured() {
        if (!StringUtils.hasText(fromEmail)) {
            return false;
        }

        if (mailSender instanceof JavaMailSenderImpl impl) {
            return StringUtils.hasText(impl.getHost());
        }

        return true; // fallback: assume configured nếu không phải impl cụ thể
    }

    private String buildVerificationHtml(User user, String verificationToken) {
        Context context = new Context(Locale.forLanguageTag("vi-VN"));
        context.setVariable("userName", user.getFullName() != null ? user.getFullName() : user.getEmail());
        context.setVariable("verificationLink", frontendUrl + "/verify-email?token=" + verificationToken);
        context.setVariable("appName", appName);
        return emailTemplateEngine.process("verification", context);
    }

    private String buildOrderConfirmationHtml(Order order) {
        Context context = new Context(Locale.forLanguageTag("vi-VN"));
        context.setVariable("order", order);
        context.setVariable("orderNumber", order.getOrderNumber());
        context.setVariable("userName", order.getUser().getFullName() != null
            ? order.getUser().getFullName()
            : order.getUser().getEmail());
        context.setVariable("orderDate", order.getCreatedAt().format(
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"))));
        context.setVariable("total", formatCurrency(order.getTotal()));
        context.setVariable("orderLink", frontendUrl + "/account/orders/" + order.getOrderNumber());
        context.setVariable("appName", appName);
        return emailTemplateEngine.process("order-confirmation", context);
    }

    private String buildOrderStatusUpdateHtml(Order hydratedOrder, String oldStatus, String newStatus) {
        Context context = new Context(Locale.forLanguageTag("vi-VN"));
        context.setVariable("order", hydratedOrder);
        context.setVariable("orderNumber", hydratedOrder.getOrderNumber());
        context.setVariable("userName", hydratedOrder.getUser().getFullName() != null
            ? hydratedOrder.getUser().getFullName()
            : hydratedOrder.getUser().getEmail());
        context.setVariable("oldStatus", translateOrderStatus(oldStatus));
        context.setVariable("newStatus", translateOrderStatus(newStatus));
        context.setVariable("orderLink", frontendUrl + "/account/orders/" + hydratedOrder.getOrderNumber());
        context.setVariable("appName", appName);
        return emailTemplateEngine.process("order-status-update", context);
    }

    private String buildPasswordResetHtml(User user, String resetToken) {
        Context context = new Context(Locale.forLanguageTag("vi-VN"));
        context.setVariable("userName", user.getFullName() != null ? user.getFullName() : user.getEmail());
        context.setVariable("resetLink", frontendUrl + "/reset-password?token=" + resetToken);
        context.setVariable("appName", appName);
        return emailTemplateEngine.process("password-reset", context);
    }

    private String buildPaymentConfirmationHtml(Order order) {
        Context context = new Context(Locale.forLanguageTag("vi-VN"));
        context.setVariable("order", order);
        context.setVariable("orderNumber", order.getOrderNumber());
        context.setVariable("userName", order.getUser().getFullName() != null
            ? order.getUser().getFullName()
            : order.getUser().getEmail());
        context.setVariable("paymentMethod", translatePaymentMethod(order.getPaymentMethod().name()));
        context.setVariable("total", formatCurrency(order.getTotal()));
        context.setVariable("orderLink", frontendUrl + "/account/orders/" + order.getOrderNumber());
        context.setVariable("appName", appName);
        return emailTemplateEngine.process("payment-confirmation", context);
    }
}

