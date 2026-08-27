package com.etribunal.ai.automation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Component
@ConfigurationProperties(prefix = "etribunal.automation")
@Validated
public class AutomationConfig {

    private boolean enabled = false;
    private boolean dryRun = true;
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

    @Min(5)
    @Max(1440)
    private int schedulingIntervalMin = 30;

    @Min(5)
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
        private String model = "gemini-2.0-flash";

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
}