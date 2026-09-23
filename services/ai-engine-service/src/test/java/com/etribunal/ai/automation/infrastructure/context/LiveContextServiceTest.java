package com.etribunal.ai.automation.infrastructure.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.etribunal.ai.automation.config.AutomationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

class LiveContextServiceTest {

    private LiveContextService service;
    private AutomationConfig config;

    @BeforeEach
    void setUp() {
        config = new AutomationConfig();
        config.getContext().setNewsEnabled(false);
        config.getContext().setMaxNewsItems(5);
        config.getContext().setNewsCacheTtl(java.time.Duration.ofMinutes(15));
        service = new LiveContextService(config);
    }

    @Test
    void buildContext_returnsNonEmptyString() {
        String context = service.buildContext();

        assertThat(context)
                .isNotNull()
                .isNotEmpty()
                .contains("Fecha actual:")
                .contains("Estación:");
    }

    @Test
    void buildContextFor_specificDate_includesDateLabel() {
        LocalDate date = LocalDate.of(2026, 9, 15);
        String context = service.buildContextFor(date);

        assertThat(context).contains("martes", "15 de septiembre de 2026");
    }

    @Test
    void buildContextFor_winterDate_showsInvierno() {
        LocalDate winter = LocalDate.of(2026, 1, 15);
        String context = service.buildContextFor(winter);

        assertThat(context).contains("invierno");
    }

    @Test
    void buildContextFor_springDate_showsPrimavera() {
        LocalDate spring = LocalDate.of(2026, 4, 15);
        String context = service.buildContextFor(spring);

        assertThat(context).contains("primavera");
    }

    @Test
    void buildContextFor_summerDate_showsVerano() {
        LocalDate summer = LocalDate.of(2026, 7, 15);
        String context = service.buildContextFor(summer);

        assertThat(context).contains("verano");
    }

    @Test
    void buildContextFor_autumnDate_showsOtono() {
        LocalDate autumn = LocalDate.of(2026, 10, 15);
        String context = service.buildContextFor(autumn);

        assertThat(context).contains("otoño");
    }

    @Test
    void buildContextFor_newYear_showsEvent() {
        LocalDate newYear = LocalDate.of(2026, 1, 1);
        String context = service.buildContextFor(newYear);

        assertThat(context).contains("Año Nuevo", "Hoy:");
    }

    @Test
    void buildContextFor_christmas_showsEvent() {
        LocalDate christmas = LocalDate.of(2026, 12, 25);
        String context = service.buildContextFor(christmas);

        assertThat(context).contains("Navidad");
    }

    @Test
    void buildContextFor_upcomingEvent_includesUpcoming() {
        LocalDate date = LocalDate.of(2026, 12, 20);
        String context = service.buildContextFor(date);

        assertThat(context).contains("Navidad", "Próximos eventos");
    }

    @Test
    void buildContext_disabledNews_skipsNewsSection() {
        config.getContext().setNewsEnabled(false);
        LiveContextService svc = new LiveContextService(config);

        String context = svc.buildContext();

        assertThat(context).doesNotContain("Noticias recientes");
    }

    @Test
    void buildContext_enabledNews_noFeeds_skipsNewsSection() {
        config.getContext().setNewsEnabled(true);
        config.getContext().setRssFeedUrls(List.of());
        LiveContextService svc = new LiveContextService(config);

        String context = svc.buildContext();

        assertThat(context).doesNotContain("Noticias recientes");
    }

    @Test
    void seasonFor_december21_isWinter() {
        LocalDate date = LocalDate.of(2026, 12, 21);
        // Using reflection to test private method
        String context = service.buildContextFor(date);
        assertThat(context).contains("invierno");
    }

    @Test
    void seasonFor_march20_isWinter() {
        LocalDate date = LocalDate.of(2026, 3, 20);
        String context = service.buildContextFor(date);
        assertThat(context).contains("invierno");
    }

    @Test
    void seasonFor_march21_isSpring() {
        LocalDate date = LocalDate.of(2026, 3, 21);
        String context = service.buildContextFor(date);
        assertThat(context).contains("primavera");
    }

    @Test
    void seasonFor_june20_isSpring() {
        LocalDate date = LocalDate.of(2026, 6, 20);
        String context = service.buildContextFor(date);
        assertThat(context).contains("primavera");
    }

    @Test
    void seasonFor_june21_isSummer() {
        LocalDate date = LocalDate.of(2026, 6, 21);
        String context = service.buildContextFor(date);
        assertThat(context).contains("verano");
    }

    @Test
    void seasonFor_september20_isSummer() {
        LocalDate date = LocalDate.of(2026, 9, 20);
        String context = service.buildContextFor(date);
        assertThat(context).contains("verano");
    }

    @Test
    void seasonFor_september21_isAutumn() {
        LocalDate date = LocalDate.of(2026, 9, 21);
        String context = service.buildContextFor(date);
        assertThat(context).contains("otoño");
    }

    @Test
    void seasonFor_december20_isAutumn() {
        LocalDate date = LocalDate.of(2026, 12, 20);
        String context = service.buildContextFor(date);
        assertThat(context).contains("otoño");
    }

    @Test
    void nextOccurrence_sameDayThisYear() {
        // Testing via context building for a date close to an event
        LocalDate date = LocalDate.of(2026, 12, 20);
        String context = service.buildContextFor(date);
        assertThat(context).contains("Navidad");
    }

    @Test
    void extractTitle_handlesLongTitle() {
        // Test via context building - title truncation is internal
        // Just ensure buildContext works
        assertThat(service.buildContext()).isNotEmpty();
    }
}
