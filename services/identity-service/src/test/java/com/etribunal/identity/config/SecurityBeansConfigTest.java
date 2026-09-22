package com.etribunal.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.etribunal.common.security.JwtTokenProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class SecurityBeansConfigTest {

    @Test
    void exposesJwtProviderAndPasswordEncoder() {
        JwtProperties props =
                new JwtProperties(
                        "0123456789abcdef0123456789abcdef-access",
                        "fedcba9876543210fedcba9876543210-refresh",
                        "etribunal",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7));
        SecurityBeansConfig config = new SecurityBeansConfig();

        assertThat(config.jwtTokenProvider(props)).isInstanceOf(JwtTokenProvider.class);
        assertThat(config.passwordEncoder()).isInstanceOf(BCryptPasswordEncoder.class);
    }
}