package com.etribunal.identity.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank
                @Pattern(
                        regexp = "^[a-záéíóúüñ][a-záéíóúüñ0-9_]{3,10}[a-záéíóúüñ0-9]$",
                        message = "5-12 caracteres: minúsculas, números y _, sin _ en los extremos")
                String username,
        @NotBlank
                @Size(min = 8, max = 128)
                @Pattern(regexp = "(?=.*[A-Z])(?=.*\\d).*", message = "requiere al menos una mayúscula y un dígito")
                String password,
        @Size(max = 60) String displayName) {}
