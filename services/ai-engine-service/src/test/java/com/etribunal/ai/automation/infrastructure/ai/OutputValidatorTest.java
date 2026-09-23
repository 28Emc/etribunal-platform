package com.etribunal.ai.automation.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OutputValidatorTest {

    private final OutputValidator validator = new OutputValidator();

    @Test
    void validate_returnsParsedObject_whenValidJson() {
        String json = "{\"name\":\"test\",\"value\":42}";
        TestDto dto = validator.validate(json, TestDto.class).block();

        assertThat(dto).isNotNull();
        assertThat(dto.getName()).isEqualTo("test");
        assertThat(dto.getValue()).isEqualTo(42);
    }

    @ParameterizedTest
    @MethodSource("validJsonVariants")
    void validate_parsesEmbeddedJson(String json, String expectedName) {
        TestDto dto = validator.validate(json, TestDto.class).block();

        assertThat(dto).isNotNull();
        assertThat(dto.getName()).isEqualTo(expectedName);
    }

    static java.util.stream.Stream<Arguments> validJsonVariants() {
        return java.util.stream.Stream.of(
                Arguments.of("```json\n{\"name\":\"test\"}\n```", "test"),
                Arguments.of("```\n{\"name\":\"test\"}\n```", "test"),
                Arguments.of("Some text before {\"name\":\"test\"} and after", "test"),
                Arguments.of("{\n  \"name\": \"test\"\n}", "test"),
                Arguments.of("{\"name\":\"Hello {world}\"}", "Hello {world}"),
                Arguments.of("{\"name\":\"He said \\\"hello\\\"\"}", "He said \"hello\"")
        );
    }

    @Test
    void validate_returnsParsedArray_whenValidJsonArray() {
        String json = "[{\"name\":\"a\"},{\"name\":\"b\"}]";
        TestDto[] array = validator.validate(json, TestDto[].class).block();

        assertThat(array).hasSize(2);
        assertThat(array[0].getName()).isEqualTo("a");
        assertThat(array[1].getName()).isEqualTo("b");
    }

    @Test
    void validate_extractsOutermostJsonArray() {
        String mixed = "Prefix [{\"name\":\"a\"},{\"name\":\"b\"}] suffix";
        TestDto[] array = validator.validate(mixed, TestDto[].class).block();

        assertThat(array).hasSize(2);
    }

    @Test
    void validate_handlesNestedObjects() {
        String json = "{\"outer\":{\"inner\":{\"name\":\"nested\"}}}";
        OuterDto dto = validator.validate(json, OuterDto.class).block();

        assertThat(dto).isNotNull();
        assertThat(dto.getOuter().getInner().getName()).isEqualTo("nested");
    }

    @Test
    void validate_returnsError_whenInvalidJson() {
        String invalid = "not json at all";
        reactor.test.StepVerifier.create(validator.validate(invalid, TestDto.class))
                .expectErrorMatches(e -> e instanceof com.etribunal.ai.automation.domain.AiError)
                .verify();
    }

    @Test
    void validate_returnsError_whenMissingClosingBrace() {
        String invalid = "{\"name\":\"test\"";
        reactor.test.StepVerifier.create(validator.validate(invalid, TestDto.class))
                .expectErrorMatches(e -> e instanceof com.etribunal.ai.automation.domain.AiError)
                .verify();
    }

    @Test
    void cleanJson_removesMarkdownFences() {
        String withFence = "```json\n{\"test\":true}\n```";
        TestDto dto = validator.validate(withFence, TestDto.class).block();
        assertThat(dto).isNotNull();
        assertThat(dto.isTest()).isTrue();
    }

    @Test
    void cleanJson_handlesEmptyInput_returnsError() {
        String empty = "";
        reactor.test.StepVerifier.create(validator.validate(empty, TestDto.class))
                .expectErrorMatches(e -> e instanceof com.etribunal.ai.automation.domain.AiError)
                .verify();
    }

    @Test
    void cleanJson_handlesWhitespaceOnly_returnsError() {
        String ws = "   \n\t  ";
        reactor.test.StepVerifier.create(validator.validate(ws, TestDto.class))
                .expectErrorMatches(e -> e instanceof com.etribunal.ai.automation.domain.AiError)
                .verify();
    }

    @Test
    void cleanJson_handlesJsonWithNewlines() {
        String json = "{\n  \"name\": \"test\"\n}";
        TestDto dto = validator.validate(json, TestDto.class).block();
        assertThat(dto).isNotNull();
        assertThat(dto.getName()).isEqualTo("test");
    }

    // DTO classes for testing
    static class TestDto {
        private String name;
        private int value;
        private boolean test;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
        public boolean isTest() { return test; }
        public void setTest(boolean test) { this.test = test; }
    }

    static class OuterDto {
        private InnerDto outer;

        public InnerDto getOuter() { return outer; }
        public void setOuter(InnerDto outer) { this.outer = outer; }
    }

    static class InnerDto {
        private TestDto inner;

        public TestDto getInner() { return inner; }
        public void setInner(TestDto inner) { this.inner = inner; }
    }
}
