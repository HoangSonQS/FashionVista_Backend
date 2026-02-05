package com.fashionvista.backend.service;

/**
 * Gửi email qua Brevo (Sendinblue) API.
 */
public interface BrevoMailSender {

    /**
     * @return true nếu đã cấu hình đủ API key và sender.
     */
    boolean isConfigured();

    /**
     * Gửi email HTML qua Brevo.
     *
     * @param toEmail    người nhận
     * @param toName     tên người nhận (có thể rỗng)
     * @param subject    tiêu đề
     * @param htmlBody   nội dung HTML
     * @return true nếu request được gửi (201) hoặc 202, false nếu chưa cấu hình
     */
    boolean sendEmail(String toEmail, String toName, String subject, String htmlBody);
}


