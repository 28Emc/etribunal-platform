package com.etribunal.core.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

class CaseSpecificationsTest {

    @SuppressWarnings("unchecked")
    private CriteriaBuilder fullyStubbedBuilder(Predicate predicate) {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        when(cb.isNull(any())).thenReturn(predicate);
        when(cb.equal(any(Expression.class), any(Object.class))).thenReturn(predicate);
        when(cb.notEqual(any(Expression.class), any(Object.class))).thenReturn(predicate);
        Expression<String> lower = mock(Expression.class);
        when(cb.lower(any())).thenReturn(lower);
        when(cb.like(any(), anyString())).thenReturn(predicate);
        when(cb.or(any(), any())).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);
        return cb;
    }

    private int predicatesCount(Specification<CaseEntity> spec, CriteriaBuilder cb) {
        Root<CaseEntity> root = mock(Root.class);
        when(root.get(anyString())).thenReturn(mock(Path.class));
        Predicate first = cb.isNull(any());
        when(((Path<Object>) mock(Path.class)).in(anyCollection())).thenReturn(first);
        spec.toPredicate(root, mock(jakarta.persistence.criteria.CriteriaQuery.class), cb);
        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(captor.capture());
        return captor.getValue().length;
    }

    @Test
    void feedAppliesStatusAndModerationPredicates() {
        Predicate predicate = mock(Predicate.class);
        CriteriaBuilder cb = fullyStubbedBuilder(predicate);
        Specification<CaseEntity> spec = CaseSpecifications.feed(
                null, null, null, null, false);

        assertThat(predicatesCount(spec, cb)).isEqualTo(3);
    }

    @Test
    void feedAddsSearchPredicateWhenQueryProvided() {
        Predicate predicate = mock(Predicate.class);
        CriteriaBuilder cb = fullyStubbedBuilder(predicate);
        Specification<CaseEntity> spec = CaseSpecifications.feed(
                " Caso ", null, null, null, false);

        assertThat(predicatesCount(spec, cb)).isEqualTo(4);
    }

    @Test
    void feedSkipsCategoryWhenAll() {
        Predicate predicate = mock(Predicate.class);
        CriteriaBuilder cb = fullyStubbedBuilder(predicate);
        Specification<CaseEntity> spec = CaseSpecifications.feed(
                null, "All", null, null, false);

        assertThat(predicatesCount(spec, cb)).isEqualTo(3);
    }

    @Test
    void feedCreatedByMeUsesOwnerPredicate() {
        Predicate predicate = mock(Predicate.class);
        CriteriaBuilder cb = fullyStubbedBuilder(predicate);
        Specification<CaseEntity> spec = CaseSpecifications.feed(
                null, null, null, UUID.randomUUID(), true);

        assertThat(predicatesCount(spec, cb)).isEqualTo(2);
    }

    @Test
    void feedFiltersByFollowingList() {
        Predicate predicate = mock(Predicate.class);
        CriteriaBuilder cb = fullyStubbedBuilder(predicate);
        Specification<CaseEntity> spec = CaseSpecifications.feed(
                null, null, List.of(UUID.randomUUID()), null, false);

        assertThat(predicatesCount(spec, cb)).isEqualTo(4);
    }

    @Test
    void isPrivateAppliesEqualityPredicate() {
        Predicate predicate = mock(Predicate.class);
        CriteriaBuilder cb = fullyStubbedBuilder(predicate);
        Specification<CaseEntity> spec = CaseSpecifications.isPrivate();
        Root<CaseEntity> root = mock(Root.class);
        when(root.get(anyString())).thenReturn(mock(Path.class));

        assertThat(spec.toPredicate(root,
                mock(jakarta.persistence.criteria.CriteriaQuery.class), cb)).isSameAs(predicate);
    }

    @Test
    void pageableClampsSkipAndTake() {
        Pageable paging = CaseSpecifications.pageable(-3, 0, false);
        assertThat(paging.getOffset()).isZero();
        assertThat(paging.getPageSize()).isEqualTo(1);
        assertThat(paging.getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void pageableCapsTakeAndSortsByVotesWhenTrending() {
        Pageable paging = CaseSpecifications.pageable(2, 500, true);
        assertThat(paging.getOffset()).isEqualTo(2);
        assertThat(paging.getPageSize()).isEqualTo(50);
        assertThat(paging.getSort().getOrderFor("totalVotes").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }
}