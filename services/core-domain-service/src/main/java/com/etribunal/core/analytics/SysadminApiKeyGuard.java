package com.etribunal.core.analytics;

import java.security.MessageDigest;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Guard para endpoints de administración (analytics), protección via
 * X-Sysadmin-Api-Key con comparación timing-safe (parity legacy SysadminGuard).
 */
@Component
@ConfigurationProperties(prefix = "etribunal.sysadmin")
public class SysadminApiKeyGuard {

    private String apiKey;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void assertAuthorized(String headerValue) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Clave de administración no configurada");
        }
        if (headerValue == null || !timingSafeEquals(headerValue, apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Clave de administración inválida");
        }
    }

    private static boolean timingSafeEquals(String a, String b) {
        byte[] ba = sha256(a);
        byte[] bb = sha256(b);
        return MessageDigest.isEqual(ba, bb);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}