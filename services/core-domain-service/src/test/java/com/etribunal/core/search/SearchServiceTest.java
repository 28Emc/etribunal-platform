package com.etribunal.core.search;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private EntityManager em;

    @Mock
    private InternalUsersClient usersClient;

    private SearchService searchService;

    private final UUID requesterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        searchService = new SearchService(usersClient);
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
    void searchReturnsEmptyForShortQuery() {
        assertThat(searchService.search("a", 0, 8, requesterId)).isEmpty();
    }

    @Test
    void searchReturnsEmptyForBlankQuery() {
        assertThat(searchService.search("  ", 0, 8, requesterId)).isEmpty();
    }

    @Test
    void searchReturnsEmptyForNullQuery() {
        assertThat(searchService.search(null, 0, 8, requesterId)).isEmpty();
    }

    @Test
    void searchReturnsEmptyWhenNoResults() {
        stubNativeQuery(List.of());

        var results = searchService.search("inexistente", 0, 8, requesterId);
        assertThat(results).isEmpty();
    }

    @Test
    void searchReturnsResultsRankedByTsRank() {
        UUID case1Id = UUID.randomUUID();
        UUID case2Id = UUID.randomUUID();
        UUID case3Id = UUID.randomUUID();

        // Mock native query returning ranked results
        Query nativeQuery = stubNativeQuery(List.<Object[]>of(
                new Object[]{case2Id, 0.95},
                new Object[]{case1Id, 0.60},
                new Object[]{case3Id, 0.30}));

        // Mock entity fetch
        TypedQuery<CaseEntity> entityQuery = mock(TypedQuery.class);
        when(em.createQuery(contains("CaseEntity"), eq(CaseEntity.class)))
                .thenReturn(entityQuery);
        when(entityQuery.setParameter(eq("ids"), anyList()))
                .thenReturn(entityQuery);
        when(entityQuery.getResultList()).thenReturn(List.of(
                buildCase(case1Id, "Caso de prueba uno"),
                buildCase(case2Id, "Caso de prueba dos"),
                buildCase(case3Id, "Caso de prueba tres")));

        var results = searchService.search("prueba", 0, 8, requesterId);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).rank()).isCloseTo(0.95, org.assertj.core.data.Offset.offset(0.01));
        assertThat(results.get(0).case_data().id()).isEqualTo(case2Id);
        assertThat(results.get(1).rank()).isCloseTo(0.60, org.assertj.core.data.Offset.offset(0.01));
        assertThat(results.get(1).case_data().id()).isEqualTo(case1Id);
        assertThat(results.get(2).rank()).isCloseTo(0.30, org.assertj.core.data.Offset.offset(0.01));
        assertThat(results.get(2).case_data().id()).isEqualTo(case3Id);
    }

    @Test
    void searchClampsTakeToMax50() {
        Query nativeQuery = stubNativeQuery(List.of());
        searchService.search("test", 0, 999, requesterId);
        verify(nativeQuery).setParameter("take", 50);
    }

    @Test
    void searchClampsTakeToMinimum1() {
        Query nativeQuery = stubNativeQuery(List.of());
        searchService.search("test", 0, 0, requesterId);
        verify(nativeQuery).setParameter("take", 1);
    }

    @Test
    void searchEnrichesUserSummaries() {
        UUID caseId = UUID.randomUUID();
        UUID sideAId = UUID.randomUUID();
        UUID sideBId = UUID.randomUUID();

        stubNativeQuery(List.<Object[]>of(new Object[]{caseId, 0.85}));

        CaseEntity entity = buildCase(caseId, "Test case");
        entity.setSideAUserId(sideAId);
        entity.setSideBUserId(sideBId);

        TypedQuery<CaseEntity> entityQuery = mock(TypedQuery.class);
        when(em.createQuery(contains("CaseEntity"), eq(CaseEntity.class)))
                .thenReturn(entityQuery);
        when(entityQuery.setParameter(eq("ids"), anyList()))
                .thenReturn(entityQuery);
        when(entityQuery.getResultList()).thenReturn(List.of(entity));

        var results = searchService.search("test", 0, 8, requesterId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).case_data().side_a_user()).isNotNull();
        assertThat(results.get(0).case_data().side_a_user().username()).startsWith("user_");
        assertThat(results.get(0).case_data().side_b_user()).isNotNull();
    }

    // --- Helpers ---

    /**
     * Stub the native ts_rank query and return a chainable Query mock.
     */
    private Query stubNativeQuery(List<Object[]> rows) {
        Query nativeQuery = mock(Query.class);
        when(em.createNativeQuery(contains("ts_rank"))).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq("q"), anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq("status"), anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq("modStatus"), anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq("take"), anyInt())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq("skip"), anyInt())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(rows);
        return nativeQuery;
    }

    private CaseEntity buildCase(UUID id, String title) {
        CaseEntity c = new CaseEntity();
        c.setType(CaseType.vote);
        c.setStatus(CaseStatus.PUBLIC);
        c.setCategory("general");
        c.setTitle(title);
        c.setSideAContent("Contenido de prueba");
        c.setModerationStatus(ModerationStatus.PENDING);
        c.setSideAUserId(UUID.randomUUID());
        try {
            var idField = CaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(c, id);
            var createdAtField = CaseEntity.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(c, Instant.now());
        } catch (Exception ignored) {
        }
        return c;
    }
}
