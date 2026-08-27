package com.etribunal.core.cases.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RespondSideBRequest(
        @NotBlank @Size(max = 36) String invite_token,
        @NotBlank @Size(min = 10, max = 10000) String side_b_content,
        Boolean is_anonymous
) {
}
