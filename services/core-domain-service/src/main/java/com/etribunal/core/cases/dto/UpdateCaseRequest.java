package com.etribunal.core.cases.dto;

import jakarta.validation.constraints.Size;

public record UpdateCaseRequest(
        @Size(max = 100) String title,
        @Size(max = 10000) String side_a_content,
        @Size(max = 10000) String side_b_content,
        String category,
        @Size(max = 50) String side_a_subtitle,
        @Size(max = 50) String side_b_subtitle,
        @Size(max = 50) String both_wrong_subtitle,
        Boolean is_private,
        Boolean is_anonymous) {}
