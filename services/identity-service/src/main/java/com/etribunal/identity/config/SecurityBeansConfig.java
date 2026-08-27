package com.etribunal.identity.config;

import com.etribunal.common.security.JwtTokenProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityBeansConfig {

    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtProperties props) {
        return new JwtTokenProvider(
                props.accessSecret().getBytes(),
                props.refreshSecret().getBytes(),
                props.issuer(),
                props.accessTtl(),
                props.refreshTtl());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
