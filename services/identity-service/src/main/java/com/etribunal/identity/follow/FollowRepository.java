package com.etribunal.identity.follow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FollowRepository extends JpaRepository<FollowEntity, FollowId> {

    boolean existsById(FollowId id);

    Optional<FollowEntity> findById(FollowId id);

    long countByFollowingId(UUID followingId);

    long countByFollowerId(UUID followerId);

    @EntityGraph(attributePaths = "follower")
    List<FollowEntity> findByFollowingIdOrderByCreatedAtDesc(UUID followingId, Pageable pageable);

    @EntityGraph(attributePaths = "following")
    List<FollowEntity> findByFollowerIdOrderByCreatedAtDesc(UUID followerId, Pageable pageable);

    @Query(
            "select count(f) from FollowEntity f where f.following.id = :userId and f.follower.isAnonymous = false"
    )
    long countVisibleFollowers(UUID userId);
}
