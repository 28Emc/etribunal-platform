package com.etribunal.core;

import com.etribunal.common.domain.config.InternalApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableConfigurationProperties(InternalApiProperties.class)
@EnableScheduling
public class CoreDomainApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreDomainApplication.class, args);
    }
}
