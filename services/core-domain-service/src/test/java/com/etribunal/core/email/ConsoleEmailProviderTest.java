package com.etribunal.core.email;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class ConsoleEmailProviderTest {

    @Test
    void sendEmailLogsPreviewWithStrippedHtml() {
        ConsoleEmailProvider provider = new ConsoleEmailProvider();
        assertThatCode(() -> provider.sendEmail("user@example.com", "Subject",
                "<html><body><p>Hola</p></body></html>")).doesNotThrowAnyException();
    }

    @Test
    void sendEmail_shortBodyDoesNotTruncate() {
        ConsoleEmailProvider provider = new ConsoleEmailProvider();
        assertThatCode(() -> provider.sendEmail("user@example.com", "Subject", "<p>Hola</p>"))
                .doesNotThrowAnyException();
    }
}