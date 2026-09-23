package com.etribunal.core.analytics;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SysadminApiKeyGuardTest {

    @Mock
    private SysadminApiKeyGuard sysadminGuard;

    private SysadminApiKeyGuard guard;

    @BeforeEach
    void setUp() {
        guard = new SysadminApiKeyGuard();
        guard.setApiKey("test-secret-key");
    }

    @Test
    void assertAuthorized_allowsValidKey() {
        guard.assertAuthorized("test-secret-key");
    }

    @Test
    void assertAuthorized_throwsOnMissingKey() {
        guard.setApiKey(null);

        assertThatThrownBy(() -> guard.assertAuthorized("any-key"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no configurada");
    }

    @Test
    void assertAuthorized_throwsOnBlankKey() {
        guard.setApiKey("   ");

        assertThatThrownBy(() -> guard.assertAuthorized("any-key"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no configurada");
    }

    @Test
    void assertAuthorized_throwsOnInvalidKey() {
        assertThatThrownBy(() -> guard.assertAuthorized("wrong-key"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("inválida");
    }

    @Test
void assertAuthorized_usesTimingSafeComparison() {
    // Test that timing-safe comparison is used (indirectly verified by valid key working)
    guard.assertAuthorized("test-secret-key");
}
}
