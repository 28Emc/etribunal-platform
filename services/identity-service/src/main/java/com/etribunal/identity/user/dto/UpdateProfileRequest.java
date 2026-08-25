package com.etribunal.identity.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record UpdateProfileRequest(
        @Size(max = 255) String bio,
        @URL @Size(max = 2048) @JsonProperty("avatar_url") String avatarUrl,
        @Pattern(
                        regexp = "^[a-záéíóúüñ][a-záéíóúüñ0-9_]{3,10}[a-záéíóúüñ0-9]$",
                        message =
                                "El username debe tener entre 5-12 caracteres, solo minúsculas, números y guión bajo (_). No puede empezar ni terminar con _")
                String username,
        @JsonProperty("is_anonymous") Boolean isAnonymous,
        @Pattern(regexp = "es|en", message = "Idioma no soportado") String language) {}
