package com.etribunal.identity.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    Optional<UserEntity> findByEmailIgnoreCaseAndDeletedAtNullOrUsernameIgnoreCaseAndDeletedAtNull(
            String email, String username);

    Optional<UserEntity> findByIdAndDeletedAtNull(UUID id);

    Optional<UserEntity> findByUsernameAndDeletedAtNull(String username);

    Optional<UserEntity> findByVerificationToken(String token);

    Optional<UserEntity> findByResetToken(String resetToken);

    Optional<UserEntity> findByEmailIgnoreCaseAndDeletedAtNull(String email);

    @Query(
            "select u from UserEntity u where lower(u.username) like lower(concat('%', :q, '%'))"
                    + " and u.deletedAt is null order by u.createdAt desc")
    List<UserEntity> searchByUsername(@Param("q") String q, Pageable pageable);

    @Query(
            "select u from UserEntity u left join FollowEntity f on f.following.id = u.id"
                    + " where u.deletedAt is null"
                    + " group by u.id order by count(f.follower.id) desc, u.createdAt asc")
    List<UserEntity> findTopJudges(Pageable pageable);
}
