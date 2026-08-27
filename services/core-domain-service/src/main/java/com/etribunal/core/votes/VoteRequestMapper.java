package com.etribunal.core.votes;

import com.etribunal.core.votes.dto.CreateVoteRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Normaliza el payload de voto (contrato legacy: 'A' | 'B' | 'BOTH_WRONG').
 */
public final class VoteRequestMapper {

    private VoteRequestMapper() {
    }

    static VoteType toVoteType(CreateVoteRequest dto) {
        try {
            return VoteType.valueOf(dto.vote_type().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "vote_type debe ser A, B o BOTH_WRONG");
        }
    }
}
