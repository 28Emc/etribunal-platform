package com.etribunal.core.moderation;

import com.etribunal.core.cases.ModerationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class LocalModerationProvider implements ModerationProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalModerationProvider.class);

    private static final double MIN_RISK_SCORE = 0.5;

    @Value("classpath:moderation/moderation-dictionaries.json")
    private Resource dictionaryResource;

    @Value("${etribunal.moderation.min-risk-score:0.5}")
    private double minRiskScore;

    private Map<String, Set<String>> dictionaries = new HashMap<>();
    private List<Pattern> regexPatterns = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadDictionaries();
        compileRegexPatterns();
    }

    private void loadDictionaries() {
        try (InputStream is = dictionaryResource.getInputStream()) {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, List<String>> raw = mapper.readValue(is, new TypeReference<>() {});
            dictionaries = raw.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> new HashSet<>(e.getValue())));
            log.info("Loaded moderation dictionaries: {} categories", dictionaries.size());
        } catch (Exception e) {
            log.error("Failed to load moderation dictionaries", e);
            // Fallback minimal dictionaries
            dictionaries.put("profanity", Set.of("palabra1", "palabra2"));
            dictionaries.put("harassment", Set.of("insulto1", "insulto2"));
            dictionaries.put("hate", Set.of("odio1", "odio2"));
            dictionaries.put("violence", Set.of("violencia1", "violencia2"));
            dictionaries.put("sexual", Set.of("sexual1", "sexual2"));
            dictionaries.put("spam", Set.of("spam1", "spam2"));
        }
    }

    private void compileRegexPatterns() {
        // URLs sospechosos (acortadores, dominios extraños)
        regexPatterns.add(Pattern.compile(
                "(?i)(bit\\.ly|tinyurl|t\\.co|goo\\.gl|ow\\.ly|is\\.gd|buff\\.ly|adf\\.ly|bc\\.vc|shorte\\.st|clck\\.ru|cutt\\.ly|rb\\.gy|rebrand\\.ly|shorturl|url\\.es|tiny\\.cc|v\\.gd|x\\.co|yourls|shrink|lnkd\\.in|ow\\.ly)"));

        // Teléfonos (patrón genérico)
        regexPatterns.add(Pattern.compile(
                "(?i)(\\+?\\d{1,3}[-.\s]?)?\\(?\\d{2,4}\\)?[-.\s]?\\d{3,4}[-.\s]?\\d{3,4}"));

        // Emails
        regexPatterns.add(Pattern.compile(
                "(?i)\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"));

        // Doxxing - direcciones
        regexPatterns.add(Pattern.compile(
                "(?i)(calle|avenida|avda|plaza|paseo|carrera|cr\\.|cl\\.|transversal|tv\\.|diagonal|dg\\.)\\s+\\d+"));

        // Spam - repetición excesiva
        regexPatterns.add(Pattern.compile(
                "(?i)\\b(\\w+)\\s+\\1\\s+\\1\\b")); // 3 palabras iguales seguidas

        // Spam - MAYÚSCULAS excesivas
        regexPatterns.add(Pattern.compile(
                "[A-ZÁÉÍÓÚÑ]{5,}"));

        // Números de documento (básico)
        regexPatterns.add(Pattern.compile(
                "\\b\\d{8,10}\\b"));
    }

    @Override
    public Mono<ModerationResult> moderateText(String text) {
        return Mono.fromCallable(() -> {
            if (text == null || text.trim().isEmpty()) {
                return new ModerationResult(ModerationStatus.APPROVED, 0.0, List.of(), Map.of());
            }

            String normalized = normalizeText(text);
            double riskScore = 0.0;
            List<String> matchedRules = new ArrayList<>();

            // 1. Diccionarios
            for (Map.Entry<String, Set<String>> entry : dictionaries.entrySet()) {
                String category = entry.getKey();
                Set<String> words = entry.getValue();
                for (String word : words) {
                    if (normalized.contains(word.toLowerCase())) {
                        riskScore += getCategoryWeight(category);
                        matchedRules.add("dict:" + category + ":" + word);
                    }
                }
            }

            // 2. Regex patterns
            for (Pattern pattern : regexPatterns) {
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    riskScore += 0.15;
                    matchedRules.add("regex:" + pattern.pattern().substring(0, Math.min(50, pattern.pattern().length())));
                }
            }

            // 3. Longitud sospechosa (muy corto o muy largo sin espacios)
            if (text.length() > 10000 && !text.contains(" ")) {
                riskScore += 0.2;
                matchedRules.add("length:excessive_no_spaces");
            }

            // Cap risk score
            riskScore = Math.min(riskScore, 1.0);

            ModerationStatus status = riskScore >= minRiskScore ? ModerationStatus.FLAGGED : ModerationStatus.APPROVED;

            return new ModerationResult(status, riskScore, matchedRules, Map.of(
                    "normalized_length", normalized.length(),
                    "original_length", text.length()));
        });
    }

    @Override
    public Mono<ModerationResult> moderateImage(String imageUrl) {
        // Para imágenes, por ahora solo validamos URL y metadatos básicos
        // En producción se integraría con un servicio de análisis de imágenes
        return Mono.fromCallable(() -> {
            double riskScore = 0.0;
            List<String> matchedRules = new ArrayList<>();

            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                return new ModerationResult(ModerationStatus.APPROVED, 0.0, List.of(), Map.of());
            }

            // Verificar dominio sospechoso
            try {
                java.net.URL url = new java.net.URL(imageUrl);
                String host = url.getHost().toLowerCase();
                if (host.contains("bit.ly") || host.contains("tinyurl") || host.contains("t.co")) {
                    riskScore += 0.3;
                    matchedRules.add("image:url_shortener");
                }
            } catch (Exception e) {
                riskScore += 0.1;
                matchedRules.add("image:invalid_url");
            }

            ModerationStatus status = riskScore >= minRiskScore ? ModerationStatus.FLAGGED : ModerationStatus.APPROVED;
            return new ModerationResult(status, riskScore, matchedRules, Map.of());
        });
    }

    private String normalizeText(String text) {
        // Lowercase
        String normalized = text.toLowerCase(Locale.ROOT);

        // Normalizar unicode (quitar acentos)
        normalized = java.text.Normalizer.normalize(normalized, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{Mn}", "");

        // Leetspeak básico
        normalized = normalized
                .replace('4', 'a').replace('@', 'a')
                .replace('3', 'e')
                .replace('1', 'i').replace('!', 'i')
                .replace('0', 'o')
                .replace('5', 's').replace('$', 's')
                .replace('7', 't').replace('+', 't')
                .replace('9', 'g')
                .replace('6', 'b');

        // Quitar separadores extraños
        normalized = normalized.replaceAll("[^a-z0-9\\s]", " ");

        // Colapsar espacios múltiples
        normalized = normalized.replaceAll("\\s+", " ");

        // Caracteres repetidos (aaaaaa -> aaa)
        normalized = normalized.replaceAll("(.)\\1{2,}", "$1$1$1");

        return normalized.trim();
    }

    private double getCategoryWeight(String category) {
        return switch (category) {
            case "hate", "violence", "sexual" -> 0.4;
            case "harassment", "profanity" -> 0.25;
            case "spam" -> 0.15;
            default -> 0.1;
        };
    }
}