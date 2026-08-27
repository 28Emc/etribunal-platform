package com.etribunal.ai.automation.infrastructure.ai;

import com.etribunal.ai.automation.config.AutomationConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    @ConditionalOnBean(ChatClient.Builder.class)
    public ChatClient geminiChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }

    @Bean
    public RateLimiter rateLimiter(AutomationConfig config) {
        return new RateLimiter(
                config.getAi().getRpm(),
                config.getAi().getRpd(),
                config.getAi().getTpm()
        );
    }

    @Bean
    public OutputValidator outputValidator() {
        return new OutputValidator();
    }
}