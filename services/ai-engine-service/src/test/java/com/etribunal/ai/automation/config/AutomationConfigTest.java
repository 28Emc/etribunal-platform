package com.etribunal.ai.automation.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationConfigTest {

    @Test
    void defaults_areSensible() {
        AutomationConfig c = new AutomationConfig();

        assertThat(c.isEnabled()).isTrue();
        assertThat(c.isDryRun()).isFalse();
        assertThat(c.getRunHour()).isEqualTo(9);
        assertThat(c.getLanguage()).isEqualTo("es");
        assertThat(c.getDailyCasesMin()).isEqualTo(1);
        assertThat(c.getDailyCasesMax()).isEqualTo(5);
        assertThat(c.getSchedulingWindowHours()).isEqualTo(24);
        assertThat(c.getAi()).isNotNull();
        assertThat(c.getBotAuth()).isNotNull();
        assertThat(c.getActivity()).isNotNull();
        assertThat(c.getContext()).isNotNull();
        assertThat(c.getEngagement()).isNotNull();
    }

    @Test
    void setters_setValues() {
        AutomationConfig c = new AutomationConfig();
        c.setEnabled(false);
        c.setDryRun(true);
        c.setRunHour(3);
        c.setLanguage("en");
        c.setDailyCasesMin(2);
        c.setDailyCasesMax(10);
        c.setUsersPerCaseMin(4);
        c.setUsersPerCaseMax(12);
        c.setMaxInteractionsPerUserPerCaseMin(2);
        c.setMaxInteractionsPerUserPerCaseMax(5);
        c.setIntensityMin(10);
        c.setIntensityMax(90);
        c.setSchedulingIntervalMin(5);
        c.setSchedulingIntervalMax(60);
        c.setSchedulingWindowHours(12);
        c.setDailyPoolSize(25);

        assertThat(c.isEnabled()).isFalse();
        assertThat(c.isDryRun()).isTrue();
        assertThat(c.getRunHour()).isEqualTo(3);
        assertThat(c.getLanguage()).isEqualTo("en");
        assertThat(c.getDailyCasesMin()).isEqualTo(2);
        assertThat(c.getDailyCasesMax()).isEqualTo(10);
        assertThat(c.getUsersPerCaseMin()).isEqualTo(4);
        assertThat(c.getUsersPerCaseMax()).isEqualTo(12);
        assertThat(c.getMaxInteractionsPerUserPerCaseMin()).isEqualTo(2);
        assertThat(c.getMaxInteractionsPerUserPerCaseMax()).isEqualTo(5);
        assertThat(c.getIntensityMin()).isEqualTo(10);
        assertThat(c.getIntensityMax()).isEqualTo(90);
        assertThat(c.getSchedulingIntervalMin()).isEqualTo(5);
        assertThat(c.getSchedulingIntervalMax()).isEqualTo(60);
        assertThat(c.getSchedulingWindowHours()).isEqualTo(12);
        assertThat(c.getDailyPoolSize()).isEqualTo(25);
    }

    @Test
    void pickDailyCases_withinRange() {
        AutomationConfig c = new AutomationConfig();
        c.setDailyCasesMin(2);
        c.setDailyCasesMax(4);
        for (int i = 0; i < 50; i++) {
            int v = c.pickDailyCases();
            assertThat(v).isBetween(2, 4);
        }
    }

    @Test
    void pickUsersPerCase_withinRange() {
        AutomationConfig c = new AutomationConfig();
        c.setUsersPerCaseMin(5);
        c.setUsersPerCaseMax(5);
        assertThat(c.pickUsersPerCase()).isEqualTo(5);
    }

    @Test
    void pickIntensity_withinRange() {
        AutomationConfig c = new AutomationConfig();
        c.setIntensityMin(30);
        c.setIntensityMax(30);
        assertThat(c.pickIntensity()).isEqualTo(30);
    }

    @Test
    void pickMaxPerUser_withinRange() {
        AutomationConfig c = new AutomationConfig();
        c.setMaxInteractionsPerUserPerCaseMin(2);
        c.setMaxInteractionsPerUserPerCaseMax(2);
        assertThat(c.pickMaxPerUser()).isEqualTo(2);
    }

    @Test
    void pickSchedulingInterval_withinRange() {
        AutomationConfig c = new AutomationConfig();
        c.setSchedulingIntervalMin(15);
        c.setSchedulingIntervalMax(15);
        assertThat(c.pickSchedulingInterval()).isEqualTo(15);
    }

    @Test
    void aiConfig_gettersSetters() {
        AutomationConfig.AiConfig ai = new AutomationConfig.AiConfig();
        ai.setProvider("mock");
        ai.setApiKey("key");
        ai.setModel("model-x");
        ai.setTemperature(0.5);
        ai.setTopP(0.9);
        ai.setMaxOutputTokensCase(512);
        ai.setMaxOutputTokensComment(256);
        ai.setMaxOutputTokensReply(256);
        ai.setMaxOutputTokensPlan(4096);
        ai.setRpm(10);
        ai.setRpd(100);
        ai.setTpm(50000);

        assertThat(ai.getProvider()).isEqualTo("mock");
        assertThat(ai.getApiKey()).isEqualTo("key");
        assertThat(ai.getModel()).isEqualTo("model-x");
        assertThat(ai.getTemperature()).isEqualTo(0.5);
        assertThat(ai.getTopP()).isEqualTo(0.9);
        assertThat(ai.getMaxOutputTokensCase()).isEqualTo(512);
        assertThat(ai.getMaxOutputTokensComment()).isEqualTo(256);
        assertThat(ai.getMaxOutputTokensReply()).isEqualTo(256);
        assertThat(ai.getMaxOutputTokensPlan()).isEqualTo(4096);
        assertThat(ai.getRpm()).isEqualTo(10);
        assertThat(ai.getRpd()).isEqualTo(100);
        assertThat(ai.getTpm()).isEqualTo(50000);
    }

    @Test
    void aiConfig_defaults() {
        AutomationConfig.AiConfig ai = new AutomationConfig.AiConfig();
        assertThat(ai.getProvider()).isEqualTo("gemini");
        assertThat(ai.getModel()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(ai.getMaxOutputTokensCase()).isEqualTo(1024);
        assertThat(ai.getRpm()).isEqualTo(12);
    }

    @Test
    void engagementConfig_gettersSetters() {
        AutomationConfig.EngagementConfig e = new AutomationConfig.EngagementConfig();
        e.setEnabled(true);
        e.setTopExamples(5);
        e.setEvaluationDays(14);
        e.setVotesWeight(1);
        e.setCommentsWeight(2);
        e.setReactionsWeight(3);
        e.setSharesWeight(4);
        e.setSavesWeight(5);
        e.setViewsWeight(6);

        assertThat(e.isEnabled()).isTrue();
        assertThat(e.getTopExamples()).isEqualTo(5);
        assertThat(e.getEvaluationDays()).isEqualTo(14);
        assertThat(e.getVotesWeight()).isEqualTo(1);
        assertThat(e.getCommentsWeight()).isEqualTo(2);
        assertThat(e.getReactionsWeight()).isEqualTo(3);
        assertThat(e.getSharesWeight()).isEqualTo(4);
        assertThat(e.getSavesWeight()).isEqualTo(5);
        assertThat(e.getViewsWeight()).isEqualTo(6);
    }

    @Test
    void activityConfig_gettersSetters() {
        AutomationConfig.ActivityConfig a = new AutomationConfig.ActivityConfig();
        a.setWeighted(true);
        a.setEnabled(false);
        a.setMinStableSamples(100);
        a.setMinTransitionSamples(10);
        a.setLookbackDays(14);

        assertThat(a.isWeighted()).isTrue();
        assertThat(a.isEnabled()).isFalse();
        assertThat(a.getMinStableSamples()).isEqualTo(100);
        assertThat(a.getMinTransitionSamples()).isEqualTo(10);
        assertThat(a.getLookbackDays()).isEqualTo(14);
    }

    @Test
    void contextConfig_gettersSetters() {
        AutomationConfig.ContextConfig ctx = new AutomationConfig.ContextConfig();
        ctx.setNewsEnabled(false);
        ctx.setRssFeedUrls(List.of("http://a/rss"));
        ctx.setMaxNewsItems(3);
        ctx.setNewsCacheTtl(Duration.ofMinutes(5));

        assertThat(ctx.isNewsEnabled()).isFalse();
        assertThat(ctx.getRssFeedUrls()).containsExactly("http://a/rss");
        assertThat(ctx.getMaxNewsItems()).isEqualTo(3);
        assertThat(ctx.getNewsCacheTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void contextConfig_nullRssUrls_becomesEmptyList() {
        AutomationConfig.ContextConfig ctx = new AutomationConfig.ContextConfig();
        ctx.setRssFeedUrls(null);
        assertThat(ctx.getRssFeedUrls()).isEmpty();
    }

    @Test
    void botAuthConfig_gettersSetters() {
        AutomationConfig.BotAuthConfig b = new AutomationConfig.BotAuthConfig();
        b.setIdentityUrl("http://id");
        b.setCoreUrl("http://core");
        b.setEmailPattern("x%03d@y.z");
        b.setPassword("p");
        b.setPoolSize(10);
        b.setTokenCacheTtlMinutes(60);
        b.setHttpTimeoutSeconds(20);

        assertThat(b.getIdentityUrl()).isEqualTo("http://id");
        assertThat(b.getCoreUrl()).isEqualTo("http://core");
        assertThat(b.getEmailPattern()).isEqualTo("x%03d@y.z");
        assertThat(b.getPassword()).isEqualTo("p");
        assertThat(b.getPoolSize()).isEqualTo(10);
        assertThat(b.getTokenCacheTtlMinutes()).isEqualTo(60);
        assertThat(b.getHttpTimeoutSeconds()).isEqualTo(20);
    }

    @Test
    void botAuthConfig_defaults() {
        AutomationConfig.BotAuthConfig b = new AutomationConfig.BotAuthConfig();
        assertThat(b.getIdentityUrl()).contains("8081");
        assertThat(b.getCoreUrl()).contains("8082");
        assertThat(b.getPoolSize()).isEqualTo(25);
    }

    @Test
    void setNestedConfigs() {
        AutomationConfig c = new AutomationConfig();
        AutomationConfig.AiConfig ai = new AutomationConfig.AiConfig();
        ai.setProvider("mock");
        c.setAi(ai);
        assertThat(c.getAi().getProvider()).isEqualTo("mock");

        AutomationConfig.BotAuthConfig bot = new AutomationConfig.BotAuthConfig();
        bot.setPassword("x");
        c.setBotAuth(bot);
        assertThat(c.getBotAuth().getPassword()).isEqualTo("x");

        AutomationConfig.ActivityConfig act = new AutomationConfig.ActivityConfig();
        act.setWeighted(true);
        c.setActivity(act);
        assertThat(c.getActivity().isWeighted()).isTrue();

        AutomationConfig.EngagementConfig eng = new AutomationConfig.EngagementConfig();
        eng.setEnabled(true);
        c.setEngagement(eng);
        assertThat(c.getEngagement().isEnabled()).isTrue();

        AutomationConfig.ContextConfig ctx = new AutomationConfig.ContextConfig();
        ctx.setMaxNewsItems(9);
        c.setContext(ctx);
        assertThat(c.getContext().getMaxNewsItems()).isEqualTo(9);
    }
}
