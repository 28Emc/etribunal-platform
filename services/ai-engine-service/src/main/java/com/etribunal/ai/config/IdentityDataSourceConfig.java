package com.etribunal.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Segundo datasource de SOLO LECTURA hacia la BD de identity (etribunal_identity),
 * donde vive la tabla {@code users}. Se usa únicamente para consultar el pool de
 * bots y el perfil de actividad real; NO se expone como bean DataSource conflictivo
 * para no desactivar el autoconfig del datasource principal (etribunal_core).
 */
@Configuration
public class IdentityDataSourceConfig {

    @Value("${etribunal.identity.datasource.url}")
    private String url;

    @Value("${etribunal.identity.datasource.username}")
    private String username;

    @Value("${etribunal.identity.datasource.password}")
    private String password;

    @Bean(name = "identityJdbcTemplate")
    public JdbcTemplate identityJdbcTemplate() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        return new JdbcTemplate(ds);
    }
}