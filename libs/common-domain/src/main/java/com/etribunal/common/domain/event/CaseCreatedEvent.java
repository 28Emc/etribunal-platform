package com.etribunal.common.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Emitido por core-domain-service cuando se crea un caso. Topic: case-events */
public class CaseCreatedEvent extends DomainEvent {

    public static final String TYPE = "case.created";

    private final UUID caseId;
    private final UUID sideAUserId;
    private final UUID sideBUserId;
    private final String caseType;

    public CaseCreatedEvent(UUID caseId, UUID sideAUserId, UUID sideBUserId, String caseType) {
        super(TYPE, 1);
        this.caseId = caseId;
        this.sideAUserId = sideAUserId;
        this.sideBUserId = sideBUserId;
        this.caseType = caseType;
    }

    @JsonCreator
    public CaseCreatedEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("occurredAt") java.time.Instant occurredAt,
            @JsonProperty("version") int version,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("causationId") String causationId,
            @JsonProperty("caseId") UUID caseId,
            @JsonProperty("sideAUserId") UUID sideAUserId,
            @JsonProperty("sideBUserId") UUID sideBUserId,
            @JsonProperty("caseType") String caseType) {
        super(eventId, eventType, occurredAt, version, correlationId, causationId);
        this.caseId = caseId;
        this.sideAUserId = sideAUserId;
        this.sideBUserId = sideBUserId;
        this.caseType = caseType;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public UUID getSideAUserId() {
        return sideAUserId;
    }

    public UUID getSideBUserId() {
        return sideBUserId;
    }

    public String getCaseType() {
        return caseType;
    }
}
