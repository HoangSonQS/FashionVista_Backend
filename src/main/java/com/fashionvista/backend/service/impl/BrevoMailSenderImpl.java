package com.fashionvista.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionvista.backend.service.BrevoMailSender;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrevoMailSenderImpl implements BrevoMailSender {

    private static final URI BREVO_URI = URI.create("https://api.brevo.com/v3/smtp/email");

    private final ObjectMapper objectMapper;

    @Value("${brevo.api.key:}")
    private String apiKey;

    @Value("${brevo.sender.email:}")
    private String senderEmail;

    @Value("${brevo.sender.name:Sixthsoul}")
    private String senderName;

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(senderEmail);
    }

    @Override
    public boolean sendEmail(String toEmail, String toName, String subject, String htmlBody) {
        if (!isConfigured()) {
            log.warn("Brevo chưa được cấu hình, bỏ qua gửi mail.");
            return false;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            Map<String, Object> sender = new HashMap<>();
            sender.put("email", senderEmail);
            sender.put("name", senderName);
            payload.put("sender", sender);

            Map<String, Object> toItem = new HashMap<>();
            toItem.put("email", toEmail);
            if (StringUtils.hasText(toName)) {
                toItem.put("name", toName);
            }
            payload.put("to", List.of(toItem));
            payload.put("subject", subject);
            payload.put("htmlContent", htmlBody);

            String body = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder(BREVO_URI)
                .timeout(Duration.ofSeconds(10))
                .header("api-key", apiKey)
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                log.info("Brevo email sent to {} with status {}", toEmail, status);
                return true;
            }

            log.error("Brevo email send failed to {}: status={}, body={}", toEmail, status, response.body());
        } catch (Exception ex) {
            log.error("Lỗi khi gửi email qua Brevo đến {}: {}", toEmail, ex.getMessage(), ex);
        }
        return true; // không chặn flow chính
    }
}


