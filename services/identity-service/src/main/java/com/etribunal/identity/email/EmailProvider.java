package com.etribunal.identity.email;

public interface EmailProvider {
    void sendEmail(String to, String subject, String htmlBody);
}
