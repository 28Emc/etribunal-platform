package com.etribunal.ai.automation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Duration;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "etribunal.automation")
@Validated
public class AutomationConfig {

    private boolean enabled = true;
    private boolean dryRun = false;
    private int runHour = 9;

    @NotBlank
    private String language = "es";

    @Min(1)
    @Max(50)
    private int dailyCasesMin = 1;

    @Min(1)
    @Max(50)
    private int dailyCasesMax = 5;

    @Min(1)
    @Max(50)
    private int usersPerCaseMin = 5;

    @Min(1)
    @Max(50)
    private int usersPerCaseMax = 15;

    @Min(1)
    @Max(10)
    private int maxInteractionsPerUserPerCaseMin = 1;

    @Min(1)
    @Max(10)
    private int maxInteractionsPerUserPerCaseMax = 3;

    @Min(0)
    @Max(100)
    private int intensityMin = 30;

    @Min(0)
    @Max(100)
    private int intensityMax = 70;

    @Min(1)
    @Max(1440)
    private int schedulingIntervalMin = 30;

    @Min(1)
    @Max(1440)
    private int schedulingIntervalMax = 180;

    @Min(1)
    @Max(72)
    private int schedulingWindowHours = 24;

    @Min(0)
    @Max(50)
    private int dailyPoolSize = 0;

    // Nested AI config
    private AiConfig ai = new AiConfig();

    // Nested engagement config (feedback loop 2.0)
    private EngagementConfig engagement = new EngagementConfig();

    // Nested activity config (scheduling ponderado 2.0)
    private ActivityConfig activity = new ActivityConfig();

    // Nested context config (contexto vivo 2.0)
    private ContextConfig context = new ContextConfig();

    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }

    public int getRunHour() { return runHour; }
    public void setRunHour(int runHour) { this.runHour = runHour; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public int getDailyCasesMin() { return dailyCasesMin; }
    public void setDailyCasesMin(int dailyCasesMin) { this.dailyCasesMin = dailyCasesMin; }

    public int getDailyCasesMax() { return dailyCasesMax; }
    public void setDailyCasesMax(int dailyCasesMax) { this.dailyCasesMax = dailyCasesMax; }

    public int getUsersPerCaseMin() { return usersPerCaseMin; }
    public void setUsersPerCaseMin(int usersPerCaseMin) { this.usersPerCaseMin = usersPerCaseMin; }

    public int getUsersPerCaseMax() { return usersPerCaseMax; }
    public void setUsersPerCaseMax(int usersPerCaseMax) { this.usersPerCaseMax = usersPerCaseMax; }

    public int getMaxInteractionsPerUserPerCaseMin() { return maxInteractionsPerUserPerCaseMin; }
    public void setMaxInteractionsPerUserPerCaseMin(int maxInteractionsPerUserPerCaseMin) { this.maxInteractionsPerUserPerCaseMin = maxInteractionsPerUserPerCaseMin; }

    public int getMaxInteractionsPerUserPerCaseMax() { return maxInteractionsPerUserPerCaseMax; }
    public void setMaxInteractionsPerUserPerCaseMax(int maxInteractionsPerUserPerCaseMax) { this.maxInteractionsPerUserPerCaseMax = maxInteractionsPerUserPerCaseMax; }

    public int getIntensityMin() { return intensityMin; }
    public void setIntensityMin(int intensityMin) { this.intensityMin = intensityMin; }

    public int getIntensityMax() { return intensityMax; }
    public void setIntensityMax(int intensityMax) { this.intensityMax = intensityMax; }

    public int getSchedulingIntervalMin() { return schedulingIntervalMin; }
    public void setSchedulingIntervalMin(int schedulingIntervalMin) { this.schedulingIntervalMin = schedulingIntervalMin; }

    public int getSchedulingIntervalMax() { return schedulingIntervalMax; }
    public void setSchedulingIntervalMax(int schedulingIntervalMax) { this.schedulingIntervalMax = schedulingIntervalMax; }

    public int getSchedulingWindowHours() { return schedulingWindowHours; }
    public void setSchedulingWindowHours(int schedulingWindowHours) { this.schedulingWindowHours = schedulingWindowHours; }

    public int getDailyPoolSize() { return dailyPoolSize; }
    public void setDailyPoolSize(int dailyPoolSize) { this.dailyPoolSize = dailyPoolSize; }

    public AiConfig getAi() { return ai; }
    public void setAi(AiConfig ai) { this.ai = ai; }

    public EngagementConfig getEngagement() { return engagement; }
    public void setEngagement(EngagementConfig engagement) { this.engagement = engagement; }

    public ActivityConfig getActivity() { return activity; }
    public void setActivity(ActivityConfig activity) { this.activity = activity; }

    public ContextConfig getContext() { return context; }
    public void setContext(ContextConfig context) { this.context = context; }

    // Bot authentication config (API real autenticada)
    private BotAuthConfig botAuth = new BotAuthConfig();

    public BotAuthConfig getBotAuth() { return botAuth; }
    public void setBotAuth(BotAuthConfig botAuth) { this.botAuth = botAuth; }

    // Helper methods for random range picking
    public int pickDailyCases() {
        return dailyCasesMin + (int) (Math.random() * (dailyCasesMax - dailyCasesMin + 1));
    }

    public int pickUsersPerCase() {
        return usersPerCaseMin + (int) (Math.random() * (usersPerCaseMax - usersPerCaseMin + 1));
    }

    public int pickIntensity() {
        return intensityMin + (int) (Math.random() * (intensityMax - intensityMin + 1));
    }

    public int pickMaxPerUser() {
        return maxInteractionsPerUserPerCaseMin + (int) (Math.random() * (maxInteractionsPerUserPerCaseMax - maxInteractionsPerUserPerCaseMin + 1));
    }

    public int pickSchedulingInterval() {
        return schedulingIntervalMin + (int) (Math.random() * (schedulingIntervalMax - schedulingIntervalMin + 1));
    }

    public static class AiConfig {
        @NotBlank
        private String provider = "gemini";

        @NotBlank
        private String apiKey = "";

        @NotBlank
        private String model = "gemini-3.5-flash-lite";

        private double temperature = 0.8;
        private double topP = 0.95;

        // Output token limits
        private int maxOutputTokensCase = 1024;
        private int maxOutputTokensComment = 1024;
        private int maxOutputTokensReply = 1024;
        private int maxOutputTokensPlan = 8192;

        // Rate limits (effective = 85% of peak)
        private int rpm = 12;
        private int rpd = 425;
        private int tpm = 212500;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public double getTopP() { return topP; }
        public void setTopP(double topP) { this.topP = topP; }

        public int getMaxOutputTokensCase() { return maxOutputTokensCase; }
        public void setMaxOutputTokensCase(int maxOutputTokensCase) { this.maxOutputTokensCase = maxOutputTokensCase; }

        public int getMaxOutputTokensComment() { return maxOutputTokensComment; }
        public void setMaxOutputTokensComment(int maxOutputTokensComment) { this.maxOutputTokensComment = maxOutputTokensComment; }

        public int getMaxOutputTokensReply() { return maxOutputTokensReply; }
        public void setMaxOutputTokensReply(int maxOutputTokensReply) { this.maxOutputTokensReply = maxOutputTokensReply; }

        public int getMaxOutputTokensPlan() { return maxOutputTokensPlan; }
        public void setMaxOutputTokensPlan(int maxOutputTokensPlan) { this.maxOutputTokensPlan = maxOutputTokensPlan; }

        public int getRpm() { return rpm; }
        public void setRpm(int rpm) { this.rpm = rpm; }

        public int getRpd() { return rpd; }
        public void setRpd(int rpd) { this.rpd = rpd; }

        public int getTpm() { return tpm; }
        public void setTpm(int tpm) { this.tpm = tpm; }
    }

    // Feedback loop 2.0: performance por caso IA (0-100) con pesos configurables
    public static class EngagementConfig {
        private boolean enabled = false;
        private int topExamples = 3;
        private int evaluationDays = 7;
        private int votesWeight = 4;
        private int commentsWeight = 5;
        private int reactionsWeight = 3;
        private int sharesWeight = 6;
        private int savesWeight = 4;
        private int viewsWeight = 1;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getTopExamples() { return topExamples; }
        public void setTopExamples(int topExamples) { this.topExamples = topExamples; }

        public int getEvaluationDays() { return evaluationDays; }
        public void setEvaluationDays(int evaluationDays) { this.evaluationDays = evaluationDays; }

        public int getVotesWeight() { return votesWeight; }
        public void setVotesWeight(int votesWeight) { this.votesWeight = votesWeight; }

        public int getCommentsWeight() { return commentsWeight; }
        public void setCommentsWeight(int commentsWeight) { this.commentsWeight = commentsWeight; }

        public int getReactionsWeight() { return reactionsWeight; }
        public void setReactionsWeight(int reactionsWeight) { this.reactionsWeight = reactionsWeight; }

        public int getSharesWeight() { return sharesWeight; }
        public void setSharesWeight(int sharesWeight) { this.sharesWeight = sharesWeight; }

        public int getSavesWeight() { return savesWeight; }
        public void setSavesWeight(int savesWeight) { this.savesWeight = savesWeight; }

        public int getViewsWeight() { return viewsWeight; }
        public void setViewsWeight(int viewsWeight) { this.viewsWeight = viewsWeight; }
    }

    // Scheduling ponderado por actividad real (Fase 2)
    public static class ActivityConfig {
        private boolean weighted = false;
        private boolean enabled = true;
        private int minStableSamples = 200;
        private int minTransitionSamples = 20;
        private int lookbackDays = 7;

        public boolean isWeighted() { return weighted; }
        public void setWeighted(boolean weighted) { this.weighted = weighted; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getMinStableSamples() { return minStableSamples; }
        public void setMinStableSamples(int minStableSamples) { this.minStableSamples = minStableSamples; }

        public int getMinTransitionSamples() { return minTransitionSamples; }
        public void setMinTransitionSamples(int minTransitionSamples) { this.minTransitionSamples = minTransitionSamples; }

        public int getLookbackDays() { return lookbackDays; }
        public void setLookbackDays(int lookbackDays) { this.lookbackDays = lookbackDays; }
    }

    // Contexto vivo 2.0 (Fase 3): fecha/estación/eventos fijos + noticias RSS
    public static class ContextConfig {
        private boolean newsEnabled = true;
        private List<String> rssFeedUrls = new java.util.ArrayList<>();
        private int maxNewsItems = 5;
        private Duration newsCacheTtl = Duration.ofMinutes(15);

        public boolean isNewsEnabled() { return newsEnabled; }
        public void setNewsEnabled(boolean newsEnabled) { this.newsEnabled = newsEnabled; }

        public List<String> getRssFeedUrls() { return rssFeedUrls; }
        public void setRssFeedUrls(List<String> rssFeedUrls) {
            this.rssFeedUrls = rssFeedUrls == null ? new java.util.ArrayList<>() : rssFeedUrls;
        }

        public int getMaxNewsItems() { return maxNewsItems; }
        public void setMaxNewsItems(int maxNewsItems) { this.maxNewsItems = maxNewsItems; }

        public Duration getNewsCacheTtl() { return newsCacheTtl; }
        public void setNewsCacheTtl(Duration newsCacheTtl) { this.newsCacheTtl = newsCacheTtl; }
    }

    // Bot authentication config (API real autenticada)
    public static class BotAuthConfig {
        private String identityUrl = "http://localhost:8081/api";
        private String coreUrl = "http://localhost:8082/api";
        private String emailPattern = "bot%02d@etsocial.local";
        private String password = "Bot@2026";
        private int poolSize = 25;
        private int tokenCacheTtlMinutes = 30;
        private int httpTimeoutSeconds = 10;

        public String getIdentityUrl() { return identityUrl; }
        public void setIdentityUrl(String identityUrl) { this.identityUrl = identityUrl; }

        public String getCoreUrl() { return coreUrl; }
        public void setCoreUrl(String coreUrl) { this.coreUrl = coreUrl; }

        public String getEmailPattern() { return emailPattern; }
        public void setEmailPattern(String emailPattern) { this.emailPattern = emailPattern; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public int getPoolSize() { return poolSize; }
        public void setPoolSize(int poolSize) { this.poolSize = poolSize; }

        public int getTokenCacheTtlMinutes() { return tokenCacheTtlMinutes; }
        public void setTokenCacheTtlMinutes(int tokenCacheTtlMinutes) { this.tokenCacheTtlMinutes = tokenCacheTtlMinutes; }

        public int getHttpTimeoutSeconds() { return httpTimeoutSeconds; }
        public void setHttpTimeoutSeconds(int httpTimeoutSeconds) { this.httpTimeoutSeconds = httpTimeoutSeconds; }
    }
}