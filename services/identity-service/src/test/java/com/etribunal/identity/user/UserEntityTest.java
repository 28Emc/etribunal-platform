package com.etribunal.identity.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserEntityTest {

    @Test
    void defaultsAreSane() {
        UserEntity user = new UserEntity();

        assertThat(user.getRole()).isEqualTo(UserEntity.ROLE_USER);
        assertThat(user.getStatus()).isEqualTo(UserEntity.STATUS_ACTIVE);
        assertThat(user.getIsAnonymous()).isFalse();
        assertThat(user.getLanguage()).isEqualTo("es");
        assertThat(user.getReceiveNotifications()).isTrue();
        assertThat(user.getTotalShares()).isZero();
        assertThat(user.getEmailVerified()).isFalse();
    }

    @Test
    void isActiveTrueByDefault() {
        assertThat(new UserEntity().isActive()).isTrue();
    }

    @Test
    void isActiveFalseWhenSuspended() {
        UserEntity user = new UserEntity();
        user.setStatus(UserEntity.STATUS_SUSPENDED);

        assertThat(user.isActive()).isFalse();
    }

    @Test
    void isActiveFalseWhenDeleted() {
        UserEntity user = new UserEntity();
        user.setDeletedAt(Instant.now());

        assertThat(user.isActive()).isFalse();
    }

    @Test
    void onCreateAndOnUpdateSetTimestamps() {
        UserEntity user = new UserEntity();

        user.onCreate();

        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        Instant createdAt = user.getCreatedAt();

        user.onUpdate();

        assertThat(user.getUpdatedAt()).isAfterOrEqualTo(createdAt);
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
    }
}