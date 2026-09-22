package com.etribunal.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void buildsOpenApiWithJwtBearerScheme() {
        OpenApiConfig config = new OpenApiConfig();
        var api = config.identityOpenAPI();

        assertThat(api.getInfo().getTitle()).isEqualTo("eTribunal Identity Service");
        assertThat(api.getInfo().getVersion()).isEqualTo("1.0.0");
        assertThat(api.getComponents().getSecuritySchemes()).containsKey("Bearer JWT");
        assertThat(api.getSecurity()).hasSize(1);
    }
}