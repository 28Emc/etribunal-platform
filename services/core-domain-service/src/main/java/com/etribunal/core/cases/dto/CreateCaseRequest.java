package com.etribunal.core.cases.dto;

import com.etribunal.core.cases.CaseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCaseRequest(
        @NotNull CaseType type,
        @NotBlank @Size(min = 10, max = 100) String title,
        @NotBlank @Size(min = 10, max = 10000) String sideAContent,
        @Size(max = 50) String category,
        Boolean isAnonymous,
        @Size(max = 36) String sideBUserId,
        @Size(max = 50) String sideASubtitle,
        @Size(max = 50) String sideBSubtitle,
        @Size(max = 50) String bothWrongSubtitle
) {

    public boolean anonymousOrDefault() {
        return Boolean.TRUE.equals(isAnonymous);
    }
}
