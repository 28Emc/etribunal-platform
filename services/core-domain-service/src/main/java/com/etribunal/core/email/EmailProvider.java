package com.etribunal.core.email;

public interface EmailProvider {
    void sendEmail(String to, String subject, String htmlBody);
}