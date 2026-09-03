package com.etribunal.ai.automation.infrastructure.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.etribunal.ai.automation.config.AutomationConfig;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

class LiveContextServiceTest {

    @Test
    void buildContext_includesDateAndSeason() {
        AutomationConfig config = new AutomationConfig();
        LiveContextService svc = new LiveContextService(config);

        String ctx = svc.buildContextFor(LocalDate.of(2026, 9, 3));

        assertThat(ctx).contains("Fecha actual:");
        assertThat(ctx).contains("Estación: verano"); // 3 sep → verano (hemisferio norte)
    }

    @Test
    void buildContext_marksCurrentEvent() {
        AutomationConfig config = new AutomationConfig();
        LiveContextService svc = new LiveContextService(config);

        String ctx = svc.buildContextFor(LocalDate.of(2026, 12, 25));

        assertThat(ctx).contains("Estación: invierno");
        assertThat(ctx).contains("Hoy: Navidad");
    }

    @Test
    void buildContext_omitsNews_whenNoFeedsConfigured() {
        AutomationConfig config = new AutomationConfig();
        config.getContext().setNewsEnabled(true);
        LiveContextService svc = new LiveContextService(config);

        String ctx = svc.buildContextFor(LocalDate.of(2026, 9, 3));

        assertThat(ctx).doesNotContain("Noticias recientes");
    }

    @Test
    void buildContext_newsFailure_isTolerated() {
        AutomationConfig config = new AutomationConfig();
        config.getContext().setNewsEnabled(true);
        config.getContext().setRssFeedUrls(List.of("invalid://not-a-valid-feed"));
        LiveContextService svc = new LiveContextService(config);

        // No debe lanzar; continúa con fecha/estación y sin noticias
        String ctx = svc.buildContextFor(LocalDate.of(2026, 9, 3));

        assertThat(ctx).contains("Fecha actual:");
        assertThat(ctx).doesNotContain("Noticias recientes");
    }

    @Test
    void buildContext_upcomingEvent_appearsInWindow() {
        AutomationConfig config = new AutomationConfig();
        LiveContextService svc = new LiveContextService(config);

        String ctx = svc.buildContextFor(LocalDate.of(2026, 12, 5));

        assertThat(ctx).contains("Constitución");
    }
}
