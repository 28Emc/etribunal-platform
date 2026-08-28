package com.etribunal.core.votes;

import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.cases.CaseService;
import com.etribunal.core.cases.dto.CaseResponse;
import com.etribunal.core.security.CurrentUserResolver;
import com.etribunal.core.votes.dto.CreateVoteRequest;
import com.etribunal.core.votes.dto.VoteResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VotesController {

    private final VotesService votesService;
    private final CaseService caseService;
    private final CurrentUserResolver currentUser;

    public VotesController(VotesService votesService,
                           CaseService caseService,
                           CurrentUserResolver currentUser) {
        this.votesService = votesService;
        this.caseService = caseService;
        this.currentUser = currentUser;
    }

    @PostMapping("/cases/{caseId}/votes")
    public ResponseEntity<ApiResponse<VoteResponse>> vote(
            @PathVariable UUID caseId,
            @Valid @RequestBody CreateVoteRequest dto,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(
                votesService.createVote(caseId, userId, VoteRequestMapper.toVoteType(dto))));
    }

    @DeleteMapping("/cases/{caseId}/votes")
    public ResponseEntity<ApiResponse<VoteResponse>> removeVote(
            @PathVariable UUID caseId,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(votesService.deleteVote(caseId, userId)));
    }

    @GetMapping("/cases/{caseId}/votes")
    public ResponseEntity<ApiResponse<VoteResponse>> myVote(
            @PathVariable UUID caseId,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        return ResponseEntity.ok(ApiResponse.ok(votesService.getVote(caseId, userId)));
    }

    /**
     * GET /users/me/votes — casos donde el usuario ha votado (parity legacy).
     * Orden: created_at del voto descendente. Casos enriquecidos para CaseCard.
     */
    @GetMapping("/users/me/votes")
    public ResponseEntity<ApiResponse<List<CaseResponse>>> myVotedCases(
            @RequestParam(defaultValue = "0") Integer skip,
            @RequestParam(defaultValue = "20") Integer take,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        int page = Math.max(skip != null ? skip : 0, 0);
        int size = Math.min(Math.max(take != null ? take : 20, 1), 100);
        return ResponseEntity.ok(ApiResponse.ok(
                caseService.getCasesVotedByUser(userId, page, size)));
    }
}