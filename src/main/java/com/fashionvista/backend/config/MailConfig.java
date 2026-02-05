package com.fashionvista.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Cung cấp JavaMailSender tối thiểu để tránh lỗi khởi động khi không cấu hình SMTP.
 * Các thông số host/port/username/password sẽ được Spring Boot binding từ
 * spring.mail.* nếu khai báo trong cấu hình môi trường.
 */
@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender() {
        return new JavaMailSenderImpl();
    }
}


