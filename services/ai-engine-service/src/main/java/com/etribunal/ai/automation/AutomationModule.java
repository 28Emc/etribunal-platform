package com.etribunal.ai.automation;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EntityScan("com.etribunal.ai.automation.domain")
@EnableJpaRepositories("com.etribunal.ai.automation.repository")
@ComponentScan("com.etribunal.ai.automation")
@EnableAsync
public class AutomationModule {
}
