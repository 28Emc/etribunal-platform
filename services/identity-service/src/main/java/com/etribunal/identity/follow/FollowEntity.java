package com.etribunal.identity.follow;

import com.etribunal.identity.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "followers")
public class FollowEntity {

    @Id
    private FollowId id;

    @MapsId("followerId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id")
    private UserEntity follower;

    @MapsId("followingId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "following_id")
    private UserEntity following;

    @Column(nullable = false)
    private Instant createdAt;

    public FollowEntity() {}

    public FollowEntity(UserEntity follower, UserEntity following) {
        this.id = new FollowId(follower.getId(), following.getId());
        this.follower = follower;
        this.following = following;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public FollowId getId() { return id; }
    public UserEntity getFollower() { return follower; }
    public UserEntity getFollowing() { return following; }
    public Instant getCreatedAt() { return createdAt; }
}
