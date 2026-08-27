package com.etribunal.identity.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank
                @Size(min = 8, max = 128)
                @Pattern(
                        regexp = "(?=.*[A-Z])(?=.*\\d).*",
                        message = "requiere al menos una mayúscula y un dígito")
                String newPassword) {}
