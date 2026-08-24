package com.etribunal.common.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio base. Sobre la marcha se le agrega un envelope con metadatos de
 * trazabilidad (correlación/causación) para propagar contexto entre microservicios vía Kafka.
 */
public abstract class DomainEvent {

    private final String eventId;
    private final String eventType;
    private final Instant occurredAt;
    private final int version;
    private final String correlationId;
    private final String causationId;

    protected DomainEvent(String eventType, int version) {
        this(UUID.randomUUID().toString(), eventType, Instant.now(), version, null, null);
    }

    @JsonCreator
    protected DomainEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("version") int version,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("causationId") String causationId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.version = version;
        this.correlationId = correlationId;
        this.causationId = causationId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public int getVersion() {
        return version;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getCausationId() {
        return causationId;
    }
}
