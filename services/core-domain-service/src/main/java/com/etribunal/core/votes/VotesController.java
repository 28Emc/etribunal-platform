package com.etribunal.core.votes;

import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.security.CurrentUserResolver;
import com.etribunal.core.votes.dto.CreateVoteRequest;
import com.etribunal.core.votes.dto.VoteResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cases/{caseId}/votes")
public class VotesController {

    private final VotesService votesService;
    private final CurrentUserResolver currentUser;

    public VotesController(VotesService votesService,
                           CurrentUserResolver currentUser) {
        this.votesService = votesService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VoteResponse>> vote(
            @PathVariable UUID caseId,
            @Valid @RequestBody CreateVoteRequest dto,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(
                votesService.createVote(caseId, userId, VoteRequestMapper.toVoteType(dto))));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<VoteResponse>> removeVote(
            @PathVariable UUID caseId,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(votesService.deleteVote(caseId, userId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<VoteResponse>> myVote(
            @PathVariable UUID caseId,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(votesService.getVote(caseId, userId)));
    }
}
