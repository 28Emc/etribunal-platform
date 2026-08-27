package com.etribunal.identity.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank
                @Size(min = 8, max = 128)
                @Pattern(
                        regexp = "(?=.*[A-Z])(?=.*\\d).*",
                        message = "requiere al menos una mayúscula y un dígito")
                String newPassword) {}
