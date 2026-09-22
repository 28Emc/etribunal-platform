package com.etribunal.gateway.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class ShadowComparerTest {

    @Test
    void hashBody_isDeterministicPerContent() {
        assertThat(ShadowComparer.hashBody("{\"ok\":true}"))
                .isEqualTo(ShadowComparer.hashBody("{\"ok\":true}"))
                .isNotEqualTo(ShadowComparer.hashBody("{\"ok\":false}"));
        assertThat(ShadowComparer.hashBody(null)).isEqualTo("null");
    }

    @Test
    void hashBody_matchingBodies_producesSameHash() {
        assertThat(ShadowComparer.hashBody("{\"ok\":true}"))
                .isEqualTo(ShadowComparer.hashBody("{\"ok\":true}"));
    }

    @Test
    void hashBody_differentBodies_producesDifferentHash() {
        assertThat(ShadowComparer.hashBody("{\"ok\":true}"))
                .isNotEqualTo(ShadowComparer.hashBody("{\"ok\":false}"));
    }

    @Test
    void hashBody_nullBody_handledConsistently() {
        assertThat(ShadowComparer.hashBody(null)).isEqualTo("null");
    }

    @Test
    void compare_doesNotThrowForMatchingBodies() {
        ShadowComparer comparer = new ShadowComparer();
        assertThatCode(() -> comparer.compare(200, "{\"ok\":true}", 200, "{\"ok\":true}", "/api/cases/1", false))
                .doesNotThrowAnyException();
    }

    @Test
    void compare_doesNotThrowForMismatchingBodies() {
        ShadowComparer comparer = new ShadowComparer();
        assertThatCode(() -> comparer.compare(200, "{\"ok\":true}", 500, null, "/api/cases/2", true))
                .doesNotThrowAnyException();
        assertThatCode(() -> comparer.compare(200, "{\"ok\":true}", 200, "{\"ok\":false}", "/api/cases/3", true))
                .doesNotThrowAnyException();
    }
}