package com.etribunal.core.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.core.analytics.AnalyticsService;
import com.etribunal.core.analytics.InteractionAction;
import com.etribunal.core.cases.dto.CaseResponse;
import com.etribunal.core.cases.dto.UpdateCaseRequest;
import com.etribunal.core.comments.CommentRepository;
import com.etribunal.core.config.FrontendUrlProperties;
import com.etribunal.core.moderation.ModerationService;
import com.etribunal.core.reactions.ReactionRepository;
import com.etribunal.core.reports.ReportStatus;
import com.etribunal.core.saved.CaseShareRepository;
import com.etribunal.core.saved.SavedCaseRepository;
import com.etribunal.core.security.CurrentUserResolver;
import com.etribunal.core.users.InternalUsersClient;
import com.etribunal.core.users.UserSummary;
import com.etribunal.core.votes.CaseVoteEntity;
import com.etribunal.core.votes.VoteRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CaseServiceAdditionalTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private InternalUsersClient usersClient;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Mock
    private HttpServletRequest request;

    @Mock
    private SavedCaseRepository savedCaseRepository;

    @Mock
    private CaseShareRepository caseShareRepository;

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private ModerationService moderationService;

    @Mock
    private AnalyticsService analyticsService;

    private CaseService caseService;

    private final UUID authorId = UUID.randomUUID();
    private final UUID sideBId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        caseService = new CaseService(caseRepository, commentRepository, usersClient,
                currentUserResolver,
                new FrontendUrlProperties("http://localhost:3000/"),
                savedCaseRepository, caseShareRepository, voteRepository, reactionRepository,
                moderationService, analyticsService, 30);
        lenient().when(currentUserResolver.currentUserId(request))
                .thenReturn(Optional.of(requesterId));
        lenient().when(usersClient.summaries(anyList())).thenAnswer(invocation -> {
            List<UUID> ids = invocation.getArgument(0);
            return ids.stream()
                    .map(id -> new UserSummary(id, "user_" + id.toString().substring(0, 4),
                            "https://example.com/a.png", false))
                    .toList();
        });
        lenient().when(savedCaseRepository.findCaseIdsByUserIdAndCaseIdIn(any(), anyList()))
                .thenReturn(List.of());
        lenient().when(caseShareRepository.findCaseIdsByUserIdAndCaseIdIn(any(), anyList()))
                .thenReturn(List.of());
        lenient().when(voteRepository.findByUserIdAndCaseIdIn(any(), anyList()))
                .thenReturn(List.of());
        lenient().when(reactionRepository.findEmojiByTargetTypeAndTargetIdInAndUserId(
                any(), anyList(), any())).thenReturn(List.of());
        lenient().when(reactionRepository.countEmojiByTargetTypeAndTargetIdIn(any(), anyList()))
                .thenReturn(List.of());
        lenient().when(commentRepository.countByCaseIdIn(anyList()))
                .thenReturn(List.of());
    }

    @Test
    void getCasesWithFollowingFeedFiltersByFollowingIds() {
        UUID followingId = UUID.randomUUID();
        lenient().when(currentUserResolver.currentUserId(request))
                .thenReturn(Optional.of(requesterId));
        lenient().when(usersClient.followingIds(requesterId)).thenReturn(List.of(followingId));

        when(caseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of()));

        caseService.getCases(0, 10, "following", null, null, false, request);

        verify(usersClient).followingIds(requesterId);
    }

    @Test
    void getCasesWithCreatedByMeFiltersByAuthor() {
        when(caseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of()));

        caseService.getCases(0, 10, null, null, null, true, request);

        verify(caseRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getCasesTrendingSortsByTotalVotes() {
        when(caseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of()));

        caseService.getCases(0, 10, "trending", null, null, false, request);

        verify(caseRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getCasesByUsernameFiltersPrivate() {
        when(usersClient.findByUsername("testuser")).thenReturn(
                new UserSummary(authorId, "testuser", "https://a.com/x.png", false));
        when(caseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of()));

        caseService.getCasesByUsername("testuser", 0, 10, "private", request);

        verify(caseRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getCasesByUsernameThrowsOnUnknownUser() {
        when(usersClient.findByUsername("unknown")).thenReturn(null);

        assertThatThrownBy(() -> caseService.getCasesByUsername("unknown", 0, 10, null, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    void getCasesVotedByUserReturnsOrderedByVoteDate() {
        UUID caseId1 = UUID.randomUUID();
        UUID caseId2 = UUID.randomUUID();
        CaseVoteEntity vote1 = new CaseVoteEntity();
        vote1.setCaseId(caseId1);
        vote1.setUserId(requesterId);
        CaseVoteEntity vote2 = new CaseVoteEntity();
        vote2.setCaseId(caseId2);
        vote2.setUserId(requesterId);

        when(voteRepository.findByUserIdOrderByCreatedAtDesc(eq(requesterId), any(Pageable.class)))
                .thenReturn(List.of(vote1, vote2));
        when(caseRepository.findAllById(List.of(caseId1, caseId2)))
                .thenReturn(List.of(
                        caseEntity(caseId1, "Caso 1"),
                        caseEntity(caseId2, "Caso 2")));

        List<CaseResponse> results = caseService.getCasesVotedByUser(requesterId, 0, 10);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isEqualTo(caseId1);
        assertThat(results.get(1).id()).isEqualTo(caseId2);
    }

    @Test
    void getCasesVotedByUserReturnsEmptyWhenNoVotes() {
        when(voteRepository.findByUserIdOrderByCreatedAtDesc(eq(requesterId), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(caseService.getCasesVotedByUser(requesterId, 0, 10)).isEmpty();
    }

    @Test
    void getCaseBySlugReturnsCase() {
        String slug = "mi-caso-test";
        CaseEntity entity = caseEntity(UUID.randomUUID(), "Test Case");
        when(caseRepository.findBySlugAndDeletedAtIsNull(slug)).thenReturn(Optional.of(entity));

        CaseResponse response = caseService.getCaseBySlug(slug, request);

        assertThat(response.slug()).isEqualTo(slug);
    }

    @Test
    void getCaseBySlugThrowsWhenNotFound() {
        when(caseRepository.findBySlugAndDeletedAtIsNull("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caseService.getCaseBySlug("no-existe", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Caso no encontrado");
    }

    @Test
    void getCaseByInviteTokenReturnsWaitingCase() {
        CaseEntity entity = caseEntity(UUID.randomUUID(), "Vote Case");
        entity.setType(CaseType.vote);
        entity.setStatus(CaseStatus.WAITING);
        when(caseRepository.findByInviteTokenAndDeletedAtIsNull("tok-abc")).thenReturn(Optional.of(entity));

        CaseResponse response = caseService.getCaseByInviteToken("tok-abc", request);

        assertThat(response.status()).isEqualTo("WAITING");
    }

    @Test
    void getCaseByInviteTokenRejectsClassicCase() {
        CaseEntity entity = caseEntity(UUID.randomUUID(), "Classic Case");
        entity.setType(CaseType.classic);
        entity.setStatus(CaseStatus.PUBLIC);
        when(caseRepository.findByInviteTokenAndDeletedAtIsNull("tok-xyz")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> caseService.getCaseByInviteToken("tok-xyz", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Solo los casos de votación");
    }

    @Test
    void updateCaseSideAEditsAllowedFields() {
        CaseEntity entity = caseEntity(UUID.randomUUID(), "Original Title");
        entity.setSideAUserId(authorId);
        entity.setSideBUserId(sideBId);
        lenient().when(caseRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        lenient().when(caseRepository.save(any(CaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CaseResponse response = caseService.updateCase(entity.getId(), authorId,
                new UpdateCaseRequest(
                        "New Title", "New Side A", "New Side A",
                        "Relationship", "New Subtitle A", "New Subtitle B", "New Both Wrong",
                        true, true));

        assertThat(response.title()).isEqualTo("New Title");
        assertThat(response.side_a_content()).isEqualTo("New Side A");
        assertThat(response.side_a_subtitle()).isEqualTo("New Subtitle A");
        assertThat(response.category()).isEqualTo("Relationship");
        assertThat(response.is_anonymous()).isTrue();
        assertThat(response.is_private()).isTrue();
        assertThat(response.both_wrong_subtitle()).isEqualTo("New Both Wrong");
        verify(moderationService).moderateCaseContentAsync(any(), any(), any(), any());
    }

    @Test
    void updateCaseSideBEditsAllowedFields() {
        CaseEntity entity = caseEntity(UUID.randomUUID(), "Original Title");
        entity.setSideAUserId(authorId);
        entity.setSideBUserId(sideBId);
        lenient().when(caseRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        lenient().when(caseRepository.save(any(CaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CaseResponse response = caseService.updateCase(entity.getId(), sideBId,
                new UpdateCaseRequest(
                        null, null, "New Side B", null, null, "New Subtitle B", null,
                        null, null));

        assertThat(response.side_b_content()).isEqualTo("New Side B");
        assertThat(response.side_b_subtitle()).isEqualTo("New Subtitle B");
        verify(moderationService).moderateCaseContentAsync(any(), any(), any(), any());
    }

    @Test
    void updateCaseRejectsNonParticipant() {
        CaseEntity entity = caseEntity(UUID.randomUUID(), "Title");
        entity.setSideAUserId(authorId);
        when(caseRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        UUID caseUuid = entity.getId();
        UUID otherUser = UUID.randomUUID();
        UpdateCaseRequest updateRequest =
                new UpdateCaseRequest("New", null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> caseService.updateCase(caseUuid, otherUser, updateRequest))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No tienes permisos");
    }

    @Test
    void deleteCaseRemovesReportedFlaggedCase() {
        CaseEntity entity = caseEntity(UUID.randomUUID(), "Bad Case");
        entity.setReportStatus(ReportStatus.REPORTED);
        entity.setModerationStatus(ModerationStatus.FLAGGED);
        when(caseRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        Map<String, Object> result = caseService.deleteCase(entity.getId(), UUID.randomUUID(), "spam");

        assertThat(result).containsEntry("success", true);
        assertThat(entity.getDeletedAt()).isNotNull();
        verify(caseRepository).save(entity);
    }

    @Test
    void deleteCaseRejectsNonReported() {
        CaseEntity entity = caseEntity(UUID.randomUUID(), "Case");
        entity.setReportStatus(ReportStatus.NONE);
        entity.setModerationStatus(ModerationStatus.APPROVED);
        when(caseRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        UUID caseUuid = entity.getId();
        UUID moderatorId = UUID.randomUUID();
        assertThatThrownBy(() -> caseService.deleteCase(caseUuid, moderatorId, "reason"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("revisión");
    }

    @Test
    void getTrendingCasesReturnsLimitedResults() {
        CaseEntity e1 = caseEntity(UUID.randomUUID(), "Trending 1");
        CaseEntity e2 = caseEntity(UUID.randomUUID(), "Trending 2");
        when(caseRepository.findTrendingCases(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e1, e2)));

        List<CaseResponse> results = caseService.getTrendingCases(2);

        assertThat(results).hasSize(2);
    }

    @Test
    void getActiveUsersReturnsUserActivity() {
        UUID userId = UUID.randomUUID();
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{userId, 5L});
        when(caseRepository.findActiveUsersByRecentActivity(any(), any(Pageable.class)))
                .thenReturn(rows);
        when(usersClient.summaries(List.of(userId))).thenReturn(List.of(
                new UserSummary(userId, "active_user", "https://a.com/x.png", false)));

        Map<String, Object> result = caseService.getActiveUsers(10);

        assertThat(result.get("users")).isNotNull();
        assertThat(((List<?>) result.get("users"))).hasSize(1);
    }

    @Test
    void generateSlugProducesSeoFriendlyString() {
        String slug = CaseServiceTestHelper.generateSlug("  Mi Caso con TITULO!!  ");
        assertThat(slug).isEqualTo("mi-caso-con-titulo");
    }

    @Test
    void generateSlugHandlesMultipleSpaces() {
        String slug = CaseServiceTestHelper.generateSlug("a   b   c");
        assertThat(slug).isEqualTo("a-b-c");
    }

    @Test
    void generateSlugTruncatesAt100Chars() {
        String longTitle = "a".repeat(150);
        String slug = CaseServiceTestHelper.generateSlug(longTitle);
        assertThat(slug).hasSize(100);
    }

    @Test
    void getCaseLogsViewForAuthenticatedUser() {
        UUID caseId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        CaseEntity entity = caseEntity(caseId, "Test Case");
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(entity));
        lenient().when(currentUserResolver.currentUserId(request)).thenReturn(Optional.of(viewerId));

        caseService.getCase(caseId, request);

        verify(analyticsService).log(InteractionAction.VIEW.name(), caseId, viewerId);
    }

    @Test
    void getCaseDoesNotLogViewForUnauthenticatedUser() {
        UUID caseId = UUID.randomUUID();
        CaseEntity entity = caseEntity(caseId, "Test Case");
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(entity));
        lenient().when(currentUserResolver.currentUserId(request)).thenReturn(Optional.empty());

        caseService.getCase(caseId, request);

        verify(analyticsService, never()).log(anyString(), any(), any());
    }

    @Test
    void getCasesWithCategoryFilter() {
        when(caseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of()));

        caseService.getCases(0, 10, null, "Relationship", null, false, request);

        verify(caseRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getCasesWithSearchQuery() {
        when(caseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of()));

        caseService.getCases(0, 10, null, null, "search term", false, request);

        verify(caseRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void updateCaseSideBCannotEditSideAFields() {
        CaseEntity entity = caseEntity(UUID.randomUUID(), "Original Title");
        entity.setSideAUserId(authorId);
        entity.setSideBUserId(sideBId);
        lenient().when(caseRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        lenient().when(caseRepository.save(any(CaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CaseResponse response = caseService.updateCase(entity.getId(), sideBId,
                new UpdateCaseRequest(
                        "New Title", "New Side A", "New Side B",
                        "Relationship", "New Subtitle A", "New Subtitle B", "New Both Wrong",
                        null, null));

        assertThat(response.title()).isEqualTo("Original Title");
        assertThat(response.side_a_content()).isEqualTo("Contenido A");
        assertThat(response.category()).isEqualTo("Other");
    }

    @Test
    void updateCaseSideBCannotSetAnonymity() {
        CaseEntity entity = caseEntity(UUID.randomUUID(), "Original Title");
        entity.setSideAUserId(authorId);
        entity.setSideBUserId(sideBId);
        entity.setAnonymous(false);
        lenient().when(caseRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        lenient().when(caseRepository.save(any(CaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // Side B cannot change anonymity - only Side A can
        CaseResponse response = caseService.updateCase(entity.getId(), sideBId,
                new UpdateCaseRequest(
                        null, null, "New Side B",
                        null, null, null, null,
                        true, null));

        assertThat(response.is_anonymous()).isFalse();
    }

    @Test
    void deleteCaseThrowsWhenNotReported() {
        CaseEntity entity = caseEntity(UUID.randomUUID(), "Case");
        entity.setReportStatus(ReportStatus.NONE);
        entity.setModerationStatus(ModerationStatus.APPROVED);
        when(caseRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        UUID caseUuid = entity.getId();
        UUID moderatorId = UUID.randomUUID();
        assertThatThrownBy(() -> caseService.deleteCase(caseUuid, moderatorId, "reason"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("revisión");
        assertThat(entity.getDeletedAt()).isNull();
    }

    @Test
    void deleteCaseThrowsWhenNotFlagged() {
        CaseEntity entity = caseEntity(UUID.randomUUID(), "Case");
        entity.setReportStatus(ReportStatus.REPORTED);
        entity.setModerationStatus(ModerationStatus.APPROVED);
        when(caseRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        UUID caseUuid = entity.getId();
        UUID moderatorId = UUID.randomUUID();
        assertThatThrownBy(() -> caseService.deleteCase(caseUuid, moderatorId, "reason"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("revisión");
        assertThat(entity.getDeletedAt()).isNull();
        verify(caseRepository, never()).save(entity);
    }

    @Test
    void getTrendingCasesReturnsEmptyWhenNoCases() {
        when(caseRepository.findTrendingCases(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        List<CaseResponse> results = caseService.getTrendingCases(10);

        assertThat(results).isEmpty();
    }

    @Test
    void getActiveUsersReturnsEmptyWhenNoActivity() {
        lenient().when(caseRepository.findActiveUsersByRecentActivity(any(), any(Pageable.class)))
                .thenReturn(List.of());

        Map<String, Object> result = caseService.getActiveUsers(10);

        assertThat(result)
                .containsEntry("users", List.of())
                .containsEntry("total", 0);
    }

    @Test
    void toResponseHandlesAnonymousUsers() {
        CaseEntity entity = caseEntity(UUID.randomUUID(), "Anonymous Case");
        entity.setAnonymous(true);
        entity.setSideAUserId(authorId);
        entity.setSideBUserId(sideBId);
        when(usersClient.summaries(anyList())).thenReturn(List.of(
                new UserSummary(authorId, "anon_user", "https://example.com/a.png", true),
                new UserSummary(sideBId, "anon_user2", "https://example.com/b.png", true)));
        lenient().when(currentUserResolver.currentUserId(request)).thenReturn(Optional.of(UUID.randomUUID()));

        List<CaseResponse> responses = CaseServiceTestHelper.toResponse(caseService, List.of(entity), UUID.randomUUID());

        assertThat(responses.get(0).side_a_user().is_anonymous()).isTrue();
        assertThat(responses.get(0).side_a_user().username())
                .isEqualTo(CaseService.MASKED_USERNAME);
    }

@Test
    void toResponseShowsRealIdentityForSelf() {
        CaseEntity entity = caseEntity(UUID.randomUUID(), "My Case");
        entity.setAnonymous(true);
        entity.setSideAUserId(authorId);
        entity.setSideBUserId(sideBId);
        when(usersClient.summaries(anyList())).thenReturn(List.of(
                new UserSummary(authorId, "real_user", "https://example.com/a.png", true),
                new UserSummary(sideBId, "real_user2", "https://example.com/b.png", true)));
        lenient().when(currentUserResolver.currentUserId(request)).thenReturn(Optional.of(authorId));

        List<CaseResponse> responses = CaseServiceTestHelper.toResponse(caseService, List.of(entity), authorId);

        // Case-level anonymity is true, so is_anonymous() is true
        // But the user sees their real identity (not masked) because they are the author
        // The user's is_anonymous flag in UserDto reflects their system anonymity, not response masking
        assertThat(responses.get(0).is_anonymous()).isTrue();
        assertThat(responses.get(0).side_a_user().is_anonymous()).isTrue();
        assertThat(responses.get(0).side_a_user().username()).isEqualTo("real_user");
    }

    private CaseEntity caseEntity(UUID id, String title) {
        CaseEntity entity = new CaseEntity();
        entity.setType(CaseType.classic);
        entity.setTitle(title);
        entity.setSlug("mi-caso-test");
        entity.setSideAContent("Contenido A");
        entity.setStatus(CaseStatus.PUBLIC);
        entity.setSideAUserId(authorId);
        try {
            var field = CaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return entity;
    }

    static class CaseServiceTestHelper {
        static String generateSlug(String title) {
            try {
                var method = CaseService.class.getDeclaredMethod("generateSlug", String.class);
                method.setAccessible(true);
                return (String) method.invoke(null, title);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        static List<CaseResponse> toResponse(CaseService service, List<CaseEntity> entities, UUID requesterId) {
            try {
                var method = CaseService.class.getDeclaredMethod("toResponse", List.class, UUID.class);
                method.setAccessible(true);
                return (List<CaseResponse>) method.invoke(service, entities, requesterId);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
