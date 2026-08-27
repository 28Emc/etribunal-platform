package com.etribunal.core.votes.dto;

public record VoteResponse(
        String case_id,
        String vote_type,
        int votes_a,
        int votes_b,
        int votes_both_wrong
) {
}
