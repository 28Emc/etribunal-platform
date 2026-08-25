package com.etribunal.identity.follow;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class FollowId implements Serializable {

    private UUID followerId;

    private UUID followingId;

    public FollowId() {}

    public FollowId(UUID followerId, UUID followingId) {
        this.followerId = followerId;
        this.followingId = followingId;
    }

    public UUID getFollowerId() { return followerId; }
    public void setFollowerId(UUID followerId) { this.followerId = followerId; }
    public UUID getFollowingId() { return followingId; }
    public void setFollowingId(UUID followingId) { this.followingId = followingId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FollowId that)) return false;
        return Objects.equals(followerId, that.followerId)
                && Objects.equals(followingId, that.followingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(followerId, followingId);
    }
}
