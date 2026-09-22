package com.etribunal.identity.email;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailProviderTest {

    private SmtpEmailProvider provider(JavaMailSender mailSender) {
        return new SmtpEmailProvider(mailSender, "noreply@etribunal.com", "eTribunal");
    }

    @Test
    void sendEmailSendsMimeMessage() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        provider(mailSender).sendEmail("a@b.c", "Asunto", "<b>hi</b>");

        verify(mailSender).send(message);
    }

    @Test
    void sendEmailCatchesMessagingException() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        doAnswer(inv -> {
                    throw new MessagingException("boom");
                })
                .when(mailSender)
                .send(message);

        assertThatCode(() -> provider(mailSender).sendEmail("a@b.c", "Asunto", "<b>hi</b>"))
                .doesNotThrowAnyException();
    }
}