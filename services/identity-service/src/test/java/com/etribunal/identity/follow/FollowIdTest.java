package com.etribunal.identity.follow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FollowIdTest {

    @Test
    void equalsAndHashCode() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        FollowId id1 = new FollowId(a, b);
        FollowId id2 = new FollowId(a, b);
        FollowId other = new FollowId(b, a);

        assertThat(id1)
                .isEqualTo(id2)
                .hasSameHashCodeAs(id2)
                .isNotEqualTo(other)
                .isNotEqualTo(null)
                .isNotEqualTo("x");
        assertThat(id1.getFollowerId()).isEqualTo(a);
        assertThat(id1.getFollowingId()).isEqualTo(b);
    }
}