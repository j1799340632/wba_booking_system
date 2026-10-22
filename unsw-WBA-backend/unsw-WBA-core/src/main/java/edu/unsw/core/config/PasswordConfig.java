package edu.unsw.core.config;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 31/7/2026 22:08
 * @description Password (encrypt) configuration
 */
@SpringBootConfiguration
public class PasswordConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Automatic salting is used, and the salt generated each time is random. BCrypt's default strength is 10 (i.e., 2^10 hash iterations), which can be adjusted between 4 and 31
        return new BCryptPasswordEncoder();
    }
}
