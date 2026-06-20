package com.fashionvista.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(SapoProperties.class)
public class SapoSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain sapoFilterChain(HttpSecurity http,
                                               SapoProperties sapoProperties) throws Exception {
        http
            .securityMatcher("/api/sapo/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(new SapoApiKeyFilter(sapoProperties),
                             UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
