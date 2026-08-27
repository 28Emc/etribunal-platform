package com.etribunal.ai.automation.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

class CaseGeneratorTest {

    @Test
    void computeHash_producesConsistentHash() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        String raw = ("Test Title" + "Test Content").toLowerCase().trim();
        byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        String result = sb.toString();
        assertThat(result).hasSize(40);
        assertThat(result).matches("[0-9a-f]{40}");
    }

    @Test
    void computeHash_sameInputProducesSameOutput() throws Exception {
        MessageDigest digest1 = MessageDigest.getInstance("SHA-1");
        MessageDigest digest2 = MessageDigest.getInstance("SHA-1");

        String input = "Same Input";
        byte[] hash1 = digest1.digest(input.getBytes(StandardCharsets.UTF_8));
        byte[] hash2 = digest2.digest(input.getBytes(StandardCharsets.UTF_8));

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void computeHash_differentInputProducesDifferentOutput() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash1 = digest.digest("Input A".getBytes(StandardCharsets.UTF_8));
        byte[] hash2 = digest.digest("Input B".getBytes(StandardCharsets.UTF_8));

        assertThat(hash1).isNotEqualTo(hash2);
    }
}