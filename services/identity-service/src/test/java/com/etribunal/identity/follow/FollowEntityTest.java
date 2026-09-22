package com.etribunal.identity.follow;

import static org.assertj.core.api.Assertions.assertThat;

import com.etribunal.identity.user.UserEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FollowEntityTest {

    @Test
    void constructorBuildsIdAndExposesUsers() {
        UserEntity follower = new UserEntity();
        follower.setId(UUID.randomUUID());
        UserEntity following = new UserEntity();
        following.setId(UUID.randomUUID());

        FollowEntity follow = new FollowEntity(follower, following);

        assertThat(follow.getId()).isEqualTo(new FollowId(follower.getId(), following.getId()));
        assertThat(follow.getFollower()).isSameAs(follower);
        assertThat(follow.getFollowing()).isSameAs(following);

        follow.onCreate();

        assertThat(follow.getCreatedAt()).isNotNull();
    }
}