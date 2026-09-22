package com.etribunal.common.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.etribunal.common.domain.event.CaseCreatedEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventJsonTest {

    @Test
    void deserializesStableEvent_toleratingUnknownFields() throws Exception {
        // FIX #25: un productor más nuevo (campo extra) no debe romper al consumidor.
        UUID caseId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", "e-1");
        payload.put("eventType", CaseCreatedEvent.TYPE);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("version", 1);
        payload.put("correlationId", null);
        payload.put("causationId", null);
        payload.put("caseId", caseId.toString());
        payload.put("sideAUserId", UUID.randomUUID().toString());
        payload.put("sideBUserId", null);
        payload.put("caseType", "vote");
        payload.put("futureField", "valor de un productor más nuevo");

        byte[] bytes = EventJson.mapper().writeValueAsBytes(payload);
        CaseCreatedEvent event = EventJson.mapper().readValue(bytes, CaseCreatedEvent.class);

        assertThat(event.getEventType()).isEqualTo("case.created");
        assertThat(event.getCaseId()).isEqualTo(caseId);
    }

    @Test
    void serialize_roundTripsConcreteEvent() throws Exception {
        CaseCreatedEvent event =
                new CaseCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), null, "classic");

        byte[] bytes = EventJson.serialize(event);
        CaseCreatedEvent roundTripped = EventJson.mapper().readValue(bytes, CaseCreatedEvent.class);

        assertThat(roundTripped.getCaseId()).isEqualTo(event.getCaseId());
        assertThat(roundTripped.getCaseType()).isEqualTo("classic");
        assertThat(roundTripped.getEventId()).isEqualTo(event.getEventId());
    }

    @Test
    void mapper_isSharedInstance() {
        assertThat(EventJson.mapper()).isSameAs(EventJson.mapper());
    }
}