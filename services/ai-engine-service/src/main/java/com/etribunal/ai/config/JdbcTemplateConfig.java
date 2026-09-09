package com.etribunal.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * JdbcTemplate primario contra la BD de core (etribunal_core), donde viven
 * case_performance, automation_settings, automation_runs, cases, etc.
 *
 * <p>Es necesario definirlo explícitamente: el autoconfig de Spring Boot
 * ({@code JdbcTemplateAutoConfiguration}) deja de crear el bean
 * {@code jdbcTemplate} cuando existe cualquier {@code JdbcOperations} definido
 * por el usuario (el {@code identityJdbcTemplate}), por lo que sin este bean
 * todas las inyecciones sin cualificar caían en la BD de identity.</p>
 */
@Configuration
public class JdbcTemplateConfig {

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}