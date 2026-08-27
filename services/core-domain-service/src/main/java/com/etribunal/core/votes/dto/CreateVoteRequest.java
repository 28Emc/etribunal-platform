package com.etribunal.core.votes.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateVoteRequest(@NotBlank String vote_type) {
}
