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
        if (log.isInfoEnabled()) {
            String textPreview = htmlBody.replaceAll("<[^>]+>", "");
            int previewLen = Math.min(120, textPreview.length());
            log.info(
                    "[EMAIL-CONSOLE] To: {} | Subject: {} | Body preview: {}",
                    to,
                    subject,
                    textPreview.substring(0, previewLen));
        }
    }
}