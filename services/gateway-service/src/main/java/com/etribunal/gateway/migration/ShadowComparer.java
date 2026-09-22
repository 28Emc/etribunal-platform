package com.etribunal.gateway.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Compara la respuesta real de Spring con la de NestJS hasheando ambos cuerpos.
 * Separado de {@link ShadowTrafficFilter} para permitir testeo directo.
 */
@Component
public class ShadowComparer {

    private static final Logger log = LoggerFactory.getLogger(ShadowComparer.class);

    /**
     * Compara y loguea diferencia. Se invoca con el body real de Spring capturado
     * del response (no con un literal).
     *
     * @param logMatches si true, también loguea en info cuando las respuestas coinciden
     */
    public void compare(int springStatus, String springBody, int nestjsStatus, String nestjsBody,
                        String path, boolean logMatches) {
        boolean statusMatch = springStatus == nestjsStatus;
        boolean bodyMatch = hashBody(springBody).equals(hashBody(nestjsBody));

        if (!statusMatch || !bodyMatch) {
            log.warn("SHADOW MISMATCH {}: spring={} nestjs={} bodyMatch={}",
                    path, springStatus, nestjsStatus, bodyMatch);
        } else if (logMatches) {
            log.info("SHADOW MATCH {}: status={} bodyMatch=true", path, springStatus);
        }
    }

    static String hashBody(String body) {
        if (body == null) {
            return "null";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "hashing-error";
        }
    }
}