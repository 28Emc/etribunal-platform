package com.etribunal.core.cases;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

public final class CaseSpecifications {

    private CaseSpecifications() {
    }

    static Specification<CaseEntity> feed(
            String q, String category, List<UUID> followingIds, UUID currentUserId,
            boolean createdByMe) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                Predicate titleMatches = cb.like(cb.lower(root.get("title")), like);
                Predicate contentMatches = cb.like(cb.lower(root.get("sideAContent")), like);
                predicates.add(cb.or(titleMatches, contentMatches));
            }

            if (category != null && !"All".equalsIgnoreCase(category)) {
                predicates.add(cb.equal(root.get("category"), category));
            }

            if (createdByMe && currentUserId != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("sideAUserId"), currentUserId),
                        cb.equal(root.get("sideBUserId"), currentUserId)));
            } else {
                predicates.add(cb.equal(root.get("status"), CaseStatus.PUBLIC));
                predicates.add(cb.notEqual(root.get("moderationStatus"),
                        ModerationStatus.FLAGGED));
                if (followingIds != null && !followingIds.isEmpty()) {
                    predicates.add(root.get("sideAUserId").in(followingIds));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    static Pageable pageable(int skip, int take, boolean trending) {
        int limit = Math.min(Math.max(take, 1), 50);
        Sort sort = trending
                ? Sort.by(Sort.Direction.DESC, "totalVotes")
                : Sort.by(Sort.Direction.DESC, "createdAt");
        return new OffsetPageable(Math.max(skip, 0), limit, sort);
    }

    static Specification<CaseEntity> isPrivate() {
        return (root, query, cb) -> cb.equal(root.get("isPrivate"), true);
    }
}
