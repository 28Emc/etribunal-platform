package com.etribunal.common.kafka;

/** Topics Kafka del dominio eTribunal. Nombres estables — no renombrar sin migración (ADR-002). */
public final class Topics {

    public static final String CASE_EVENTS = "case-events";
    public static final String USER_EVENTS = "user-events";
    public static final String VOTE_EVENTS = "vote-events";
    public static final String COMMENT_EVENTS = "comment-events";
    public static final String REACTION_EVENTS = "reaction-events";
    public static final String AI_TASKS = "ai-tasks";
    public static final String MODERATION_TASKS = "moderation-tasks";
    public static final String NOTIFICATION_TASKS = "notification-tasks";

    private Topics() {}
}
