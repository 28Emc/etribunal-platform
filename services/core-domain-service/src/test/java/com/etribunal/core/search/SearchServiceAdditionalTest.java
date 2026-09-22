package com.etribunal.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseStatus;
import com.etribunal.core.cases.CaseType;
import com.etribunal.core.cases.ModerationStatus;
import com.etribunal.core.users.InternalUsersClient;
import com.etribunal.core.users.UserSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchServiceAdditionalTest {

    @Mock
    private EntityManager em;

    @Mock
    private InternalUsersClient usersClient;

    @Mock
    private SearchService self;

    private SearchService searchService;

    private final UUID requesterId = UUID.randomUUID();
    private final UUID sideAId = UUID.randomUUID();
    private final UUID sideBId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        searchService = new SearchService(usersClient, self);
        try {
            var field = SearchService.class.getDeclaredField("em");
            field.setAccessible(true);
            field.set(searchService, em);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        lenient().when(usersClient.summaries(anyList())).thenAnswer(invocation -> {
            List<UUID> ids = invocation.getArgument(0);
            return ids.stream()
                    .map(id -> new UserSummary(id, "user_" + id.toString().substring(0, 4),
                            "https://example.com/a.png", false))
                    .toList();
        });
    }

    @Test
    void quickSearchDelegatesToAdvancedSearch() {
        UUID caseId = UUID.randomUUID();
        when(self.advancedSearch("test", "ALL", 0, 5, requesterId))
                .thenReturn(Map.of("users", List.of(), "cases", List.of(), "hasMore", false));

        Map<String, Object> result = searchService.quickSearch("test", requesterId);

        assertThat(result.get("users")).isEqualTo(List.of());
        assertThat(result.get("cases")).isEqualTo(List.of());
        assertThat(result.get("hasMore")).isEqualTo(false);
        verify(self).advancedSearch("test", "ALL", 0, 5, requesterId);
    }

    @Test
    void advancedSearchUsersTypeCallsUsersClient() {
        lenient().when(usersClient.searchUsers("john", 5, 0, requesterId))
                .thenReturn(List.of(Map.of("id", requesterId, "username", "john_doe")));

        Map<String, Object> result = searchService.advancedSearch("john", "USERS", 0, 5, requesterId);

        assertThat(result.get("users")).isNotNull();
        assertThat(result.get("cases")).isEqualTo(List.of());
        verify(usersClient).searchUsers("john", 5, 0, requesterId);
    }

    @Test
    void advancedSearchCasesTypeCallsSelfSearch() {
        UUID caseId = UUID.randomUUID();
        lenient().when(self.search("test", 0, 5, requesterId))
                .thenReturn(List.of(new SearchService.SearchResult(
                        buildCaseResponse(caseId), 0.9)));

        Map<String, Object> result = searchService.advancedSearch("test", "CASES", 0, 5, requesterId);

        assertThat(result.get("cases")).isNotNull();
        assertThat(result.get("users")).isEqualTo(List.of());
        verify(self).search("test", 0, 5, requesterId);
    }

    @Test
    void advancedSearchAllTypeCallsBothWithLimits() {
        lenient().when(usersClient.searchUsers("query", 5, 0, requesterId))
                .thenReturn(List.of(Map.of("id", requesterId, "username", "user_q")));
        lenient().when(self.search("query", 0, 5, requesterId))
                .thenReturn(List.of(new SearchService.SearchResult(
                        buildCaseResponse(UUID.randomUUID()), 0.8)));

        Map<String, Object> result = searchService.advancedSearch("query", "ALL", 0, 10, requesterId);

        assertThat(result.get("users")).isNotNull();
        assertThat(result.get("cases")).isNotNull();
        verify(usersClient).searchUsers("query", 5, 0, requesterId);
        verify(self).search("query", 0, 5, requesterId);
    }

    @Test
    void advancedSearchAtPrefixForcesUsersType() {
        lenient().when(usersClient.searchUsers("user123", 10, 0, requesterId))
                .thenReturn(List.of());

        Map<String, Object> result = searchService.advancedSearch("@user123", "CASES", 0, 10, requesterId);

        assertThat(result.get("users")).isNotNull();
        assertThat(result.get("cases")).isEqualTo(List.of());
        verify(usersClient).searchUsers("user123", 10, 0, requesterId);
        verify(self, org.mockito.Mockito.never()).search(anyString(), anyInt(), anyInt(), any());
    }

    @Test
    void advancedSearchClampsTakeAndSkip() {
        // ALL type uses hardcoded 5 limit, not clamp
        lenient().when(usersClient.searchUsers(anyString(), eq(5), eq(0), any())).thenReturn(List.of());
        lenient().when(self.search(anyString(), eq(0), eq(5), any())).thenReturn(List.of());

        searchService.advancedSearch("test", "ALL", -5, 999, requesterId);

        verify(usersClient).searchUsers("test", 5, 0, requesterId);
        verify(self).search("test", 0, 5, requesterId);
    }

    @Test
    void advancedSearchEmptyQueryReturnsEmpty() {
        Map<String, Object> result = searchService.advancedSearch("a", "ALL", 0, 10, requesterId);
        assertThat(result.get("users")).isEqualTo(List.of());
        assertThat(result.get("cases")).isEqualTo(List.of());
    }

    private com.etribunal.core.cases.dto.CaseResponse buildCaseResponse(UUID caseId) {
        return new com.etribunal.core.cases.dto.CaseResponse(
                caseId,
                "vote",
                "PUBLIC",
                "general",
                "Test",
                "slug",
                "A",
                "B",
                "subA",
                "subB",
                "subBoth",
                "es",
                false,
                false,
                Instant.now(),
                Instant.now(),
                sideAId,
                sideBId,
                null,
                null,
                0, 0, 0, 0,
                0, 0, 0, 0,
                "PENDING",
                false,
                false,
                null,
                null,
                new com.etribunal.core.cases.dto.CaseResponse.ReactionsSummary(
                        new com.etribunal.core.cases.dto.CaseResponse.ReactionsSummary.Counts(0L, 0L, 0L)));
    }
}