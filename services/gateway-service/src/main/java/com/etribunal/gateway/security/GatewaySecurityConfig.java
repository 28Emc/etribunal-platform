package com.etribunal.gateway.security;

import com.etribunal.common.security.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({JwtValidationProperties.class, GatewayAuthProperties.class})
public class GatewaySecurityConfig {

    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtValidationProperties props) {
        return JwtTokenProvider.forAccessValidation(
                props.accessSecret().getBytes(StandardCharsets.UTF_8), props.issuer());
    }

    @Bean
    public CorsWebFilter corsWebFilter(GatewayAuthProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        props.allowedOrigins().forEach(config::addAllowedOrigin);
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
