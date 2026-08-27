package com.etribunal.core.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "etribunal.email.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailProvider.class);

    @Override
    public void sendEmail(String to, String subject, String htmlBody) {
        log.info(
                "[EMAIL-CONSOLE] To: {} | Subject: {} | Body preview: {}",
                to,
                subject,
                htmlBody.replaceAll("<[^>]+>", "").substring(0, Math.min(120, htmlBody.length())));
    }
}