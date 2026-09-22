package com.etribunal.core.votes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.cases.CaseService;
import com.etribunal.core.cases.dto.CaseResponse;
import com.etribunal.core.security.CurrentUserResolver;
import com.etribunal.core.votes.dto.CreateVoteRequest;
import com.etribunal.core.votes.dto.VoteResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class VotesControllerTest {

    @Mock
    private VotesService votesService;

    @Mock
    private CaseService caseService;

    @Mock
    private CurrentUserResolver currentUser;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private VotesController controller;

    private UUID userId;
    private UUID caseId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        caseId = UUID.randomUUID();
        lenient().when(currentUser.requiredUserId(request)).thenReturn(userId);
    }

    @Test
    void voteDelegatesWithMappedType() {
        when(votesService.createVote(caseId, userId, VoteType.A))
                .thenReturn(new VoteResponse(caseId.toString(), "A", 1, 0, 0));

        ResponseEntity<ApiResponse<VoteResponse>> response = controller.vote(
                caseId, new CreateVoteRequest("A"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().vote_type()).isEqualTo("A");
        verify(votesService).createVote(caseId, userId, VoteType.A);
    }

    @Test
    void removeVoteDelegatesToService() {
        when(votesService.deleteVote(caseId, userId))
                .thenReturn(new VoteResponse(caseId.toString(), null, 0, 0, 0));

        ResponseEntity<ApiResponse<VoteResponse>> response =
                controller.removeVote(caseId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(votesService).deleteVote(caseId, userId);
    }

    @Test
    void myVoteDelegatesToService() {
        when(votesService.getVote(caseId, userId))
                .thenReturn(new VoteResponse(caseId.toString(), "B", 0, 1, 0));

        ResponseEntity<ApiResponse<VoteResponse>> response = controller.myVote(caseId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().vote_type()).isEqualTo("B");
        verify(votesService).getVote(caseId, userId);
    }

    @Test
    void myVotedCasesClampsPagination() {
        when(caseService.getCasesVotedByUser(userId, 0, 100)).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<CaseResponse>>> response =
                controller.myVotedCases(0, 500, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).isEmpty();
        verify(caseService).getCasesVotedByUser(userId, 0, 100);
    }
}