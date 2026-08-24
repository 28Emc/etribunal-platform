package com.etribunal.common.kafka;

import com.etribunal.common.domain.event.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** ObjectMapper compartido para serializar eventos de dominio a JSON en Kafka. */
public final class EventJson {

    private static final ObjectMapper MAPPER =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private EventJson() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static byte[] serialize(DomainEvent event) {
        try {
            return MAPPER.writeValueAsBytes(event);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el evento " + event.getEventType(), e);
        }
    }
}
