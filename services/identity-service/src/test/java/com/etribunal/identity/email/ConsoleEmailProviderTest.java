package com.etribunal.identity.email;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class ConsoleEmailProviderTest {

    @Test
    void sendEmailHandlesHtmlPreview() {
        assertThatCode(
                        () ->
                                new ConsoleEmailProvider()
                                        .sendEmail("a@b.c", "Asunto", "<p>Hola <b>mundo</b></p>"))
                .doesNotThrowAnyException();
    }
}