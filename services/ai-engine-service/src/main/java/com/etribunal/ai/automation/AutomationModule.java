package com.etribunal.ai.automation;

import com.etribunal.ai.automation.application.AutomationScheduler;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EntityScan("com.etribunal.ai.automation.domain")
@EnableJpaRepositories("com.etribunal.ai.automation.repository")
@ComponentScan("com.etribunal.ai.automation")
@EnableAsync
@EnableScheduling
public class AutomationModule {

    private static final Logger log = LoggerFactory.getLogger(AutomationModule.class);

    @PostConstruct
    void init() {
        log.info("AutomationModule initialized (scheduling + async enabled)");
    }
}
