package com.etribunal.core.search;

import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseStatus;
import com.etribunal.core.cases.ModerationStatus;
import com.etribunal.core.cases.dto.CaseResponse;
import com.etribunal.core.users.InternalUsersClient;
import com.etribunal.core.users.UserSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_TAKE = 50;
    private static final String MASKED_USERNAME = "Anonymous Judge";
    private static final String MASKED_AVATAR =
            "https://secure.gravatar.com/avatar/0?d=mp&f=y";

    @PersistenceContext
    private EntityManager em;

    private final InternalUsersClient usersClient;

    public SearchService(InternalUsersClient usersClient) {
        this.usersClient = usersClient;
    }

    /**
     * Full-text search using PostgreSQL tsvector + ts_rank.
     * Returns results ranked by relevance (title weighted highest).
     */
    @Transactional(readOnly = true)
    public List<SearchResult> search(String query, int skip, int take,
                                     UUID requesterId) {
        if (query == null || query.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }

        String safeQuery = query.trim();
        int clampedTake = Math.min(Math.max(take, 1), MAX_TAKE);

        // Native SQL: ts_rank + plainto_tsquery for Spanish stemming
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT c.id, ts_rank(c.search_vector, plainto_tsquery('spanish', :q)) AS rank "
                + "FROM cases c "
                + "WHERE c.search_vector @@ plainto_tsquery('spanish', :q) "
                + "AND c.deleted_at IS NULL "
                + "AND c.status = :status "
                + "AND c.moderation_status <> :modStatus "
                + "ORDER BY rank DESC, c.created_at DESC "
                + "LIMIT :take OFFSET :skip")
                .setParameter("q", safeQuery)
                .setParameter("status", CaseStatus.PUBLIC.name())
                .setParameter("modStatus", ModerationStatus.FLAGGED.name())
                .setParameter("take", clampedTake)
                .setParameter("skip", skip)
                .getResultList();

        if (rows.isEmpty()) {
            return List.of();
        }

        // Batch fetch CaseEntity by IDs for full data
        List<UUID> caseIds = rows.stream()
                .map(r -> (UUID) r[0])
                .collect(Collectors.toList());

        List<CaseEntity> entities = em.createQuery(
                "SELECT c FROM CaseEntity c WHERE c.id IN :ids", CaseEntity.class)
                .setParameter("ids", caseIds)
                .getResultList();

        Map<UUID, CaseEntity> entityMap = entities.stream()
                .collect(Collectors.toMap(CaseEntity::getId, Function.identity()));

        Map<UUID, Double> rankMap = new HashMap<>();
        for (Object[] row : rows) {
            rankMap.put((UUID) row[0], ((Number) row[1]).doubleValue());
        }

        // Fetch user summaries for enrichment
        LinkedHashSet<UUID> userIds = new LinkedHashSet<>();
        for (CaseEntity c : entities) {
            userIds.add(c.getSideAUserId());
            if (c.getSideBUserId() != null) {
                userIds.add(c.getSideBUserId());
            }
        }
        Map<UUID, UserSummary> summaries = new HashMap<>();
        if (!userIds.isEmpty()) {
            summaries.putAll(usersClient.summaries(List.copyOf(userIds)).stream()
                    .collect(Collectors.toMap(UserSummary::id, Function.identity())));
        }

        // Build results preserving ts_rank order
        List<SearchResult> results = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            UUID caseId = (UUID) row[0];
            Double rank = rankMap.get(caseId);
            CaseEntity c = entityMap.get(caseId);
            if (c == null) {
                continue;
            }

            CaseResponse caseResponse = toCaseResponse(c, summaries, requesterId);
            results.add(new SearchResult(caseResponse, rank));
        }

        return results;
    }

    private CaseResponse toCaseResponse(CaseEntity c,
                                        Map<UUID, UserSummary> summaries,
                                        UUID requesterId) {
        return new CaseResponse(
                c.getId(),
                c.getType().name(),
                c.getStatus().name(),
                c.getCategory(),
                c.getTitle(),
                c.getSideAContent(),
                c.getSideBContent(),
                c.getSideASubtitle(),
                c.getSideBSubtitle(),
                c.getBothWrongSubtitle(),
                c.getContentLanguage(),
                c.isAnonymous(),
                c.isPrivate(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getSideAUserId(),
                c.getSideBUserId(),
                toMaskedDto(summaries.get(c.getSideAUserId()), requesterId),
                c.getSideBUserId() != null
                        ? toMaskedDto(summaries.get(c.getSideBUserId()), requesterId)
                        : null,
                c.getTotalVotes(),
                c.getVotesA(),
                c.getVotesB(),
                c.getVotesBothWrong(),
                c.getTotalComments(),
                c.getTotalViews(),
                c.getTotalShares(),
                c.getTotalAnchors(),
                c.getModerationStatus().name(),
                false,
                false);
    }

    private static CaseResponse.UserDto toMaskedDto(UserSummary summary,
                                                    UUID requesterId) {
        if (summary == null) {
            return new CaseResponse.UserDto(null, "Unknown", null, true);
        }
        boolean self = summary.id() != null && summary.id().equals(requesterId);
        if (summary.anonymous() && !self) {
            return new CaseResponse.UserDto(summary.id(), MASKED_USERNAME,
                    MASKED_AVATAR, true);
        }
        return CaseResponse.toUserDto(summary);
    }

    public record SearchResult(CaseResponse case_data, double rank) {
    }
}
