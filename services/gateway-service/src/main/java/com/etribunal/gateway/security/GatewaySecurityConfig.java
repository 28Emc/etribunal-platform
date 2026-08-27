package com.etribunal.gateway.security;

import com.etribunal.common.security.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtValidationProperties.class, GatewayAuthProperties.class})
public class GatewaySecurityConfig {

    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtValidationProperties props) {
        return JwtTokenProvider.forAccessValidation(
                props.accessSecret().getBytes(StandardCharsets.UTF_8), props.issuer());
    }
}
