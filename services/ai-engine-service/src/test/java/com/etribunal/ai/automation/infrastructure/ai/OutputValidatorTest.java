package com.etribunal.ai.automation.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.etribunal.ai.automation.domain.dtos.GeneratedComment;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OutputValidatorTest {

    private final OutputValidator validator = new OutputValidator();

    @Test
    void validate_parsesCleanObject() {
        Mono<GeneratedComment> mono = validator.validate(
                "{\"content\":\"Hola mundo\"}", GeneratedComment.class);
        StepVerifier.create(mono)
                .assertNext(c -> assertThat(c.content()).isEqualTo("Hola mundo"))
                .verifyComplete();
    }

    @Test
    void validate_stripsMarkdownFences() {
        Mono<GeneratedComment> mono = validator.validate(
                "```json\n{\"content\":\"En fenced\"}\n```", GeneratedComment.class);
        StepVerifier.create(mono)
                .assertNext(c -> assertThat(c.content()).isEqualTo("En fenced"))
                .verifyComplete();
    }

    @Test
    void validate_extractsJsonFromProseBeforeAndAfter() {
        String prose = "Aquí tienes el resultado:\n{\"content\":\"Con prosa\"}\nEspero que sirva.";
        StepVerifier.create(validator.validate(prose, GeneratedComment.class))
                .assertNext(c -> assertThat(c.content()).isEqualTo("Con prosa"))
                .verifyComplete();
    }

    @Test
    void validate_doesNotClipArrayPayloads() {
        // Un array JSON (que antes se recortaba mal) se parsea como objeto esperado
        // o devuelve INVALID_OUTPUT en lugar de un error silencioso — nunca corrompe.
        Mono<GeneratedComment> mono = validator.validate(
                "[{\"content\":\"first\"},{\"content\":\"second\"}]", GeneratedComment.class);
        StepVerifier.create(mono).expectError().verify();
    }

    @Test
    void validate_doesNotCutInsideBracketsWithinStrings() {
        // Llaves/llaves dentro de strings no deben romper la extracción.
        String payload = "{\"content\":\"Utiliza {llaves} y [corchetes] dentro\"}";
        StepVerifier.create(validator.validate(payload, GeneratedComment.class))
                .assertNext(c -> assertThat(c.content()).isEqualTo("Utiliza {llaves} y [corchetes] dentro"))
                .verifyComplete();
    }

    @Test
    void validate_returnsInvalidOutput_onGarbage() {
        Mono<GeneratedComment> mono = validator.validate("no es json", GeneratedComment.class);
        StepVerifier.create(mono).expectError().verify();
    }
}