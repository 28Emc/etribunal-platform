package com.etribunal.ai.automation.infrastructure.ai;

import com.etribunal.ai.automation.domain.IntensityMapper;

public final class PromptUtils {

    private PromptUtils() {}

    public static final String MODERATION_SAFE_WRITING = """
        REGLAS DE SEGURIDAD OBLIGATORIAS (cumple SIEMPRE):
        1. NO generes contenido sexualmente explícito, violento gráfico, apología del odio, autolesiones, actividades ilegales, doxxing, PII real, instrucciones de armas o promoción de trastornos alimenticios.
        2. Si el tema es sensible, abórdalo con madurez y responsabilidad: muestra consecuencias, no glorifiques.
        3. Evita estereotipos dañinos por raza, género, religión, orientación, discapacidad, origen.
        4. NO inventes datos personales reales (nombres, teléfonos, emails, direcciones, DNI).
        5. El contenido debe ser apto para debate público: provocador sí, tóxico no.
        """;

    public static String caseGenerationPrompt(String language, int intensity) {
        String toneDirective = IntensityMapper.toDirective(intensity);
        return """
            Eres un generador de casos de debate para una plataforma de opinión.
            Genera UN caso en %s.
            Tono: %s
            %s
            
            Requisitos:
            - Título: claro, concreto, ≤ 120 chars.
            - Descripción: contexto neutral, ≤ 500 chars.
            - Side A: argumento a favor, ≤ 1000 chars.
            - Side B: argumento en contra (si vote), ≤ 1000 chars.
            - Categoría: una de [politica, sociedad, tecnologia, economia, cultura, ciencia, deportes, otro].
            - Tipo: "classic" (solo Side A, debate abierto) o "vote" (tiene Side B, espera respuesta).
            - Subtítulos personalizados para botones de voto (solo si vote): sideASubtitle, sideBSubtitle, bothWrongSubtitle (≤ 30 chars cada uno).
            
            Evita duplicados con estos temas recientes: %s
            Semilla de variación: %s
            
            Responde SOLO con JSON válido según el schema proporcionado.
            """.formatted(language, toneDirective, MODERATION_SAFE_WRITING, "{recentTopics}", "{variationSeed}");
    }

    public static String interactionPlanningPrompt(String language, int intensity) {
        String toneDirective = IntensityMapper.toDirective(intensity);
        return """
            Eres un planificador de interacciones para un caso de debate.
            Genera un plan de %d interacciones para %d usuarios disponibles (máx %d por usuario).
            Tono: %s
            %s
            
            Caso: "%s"
            Side A: %s
            Side B: %s
            Categoría: %s
            
            Tipos válidos: COMMENT, REPLY, REACTION, VOTE.
            - COMMENT: requiere content (opinión original).
            - REPLY: requiere content + replyToIndex (índice del COMMENT al que responde, 0-based).
            - REACTION: requiere reaction (LIKE, LOVE, ANGRY).
            - VOTE: requiere option (A, B, BOTH_WRONG).
            
            Reglas:
            - Distribuye stances (pro-A, pro-B, neutral) según el caso.
            - REPLY solo a COMMENTS previos en el plan.
            - Intensidad del tono: %d (0-100).
            - NO repitas usuarios consecutivamente si es posible.
            
            Responde SOLO con JSON válido según el schema.
            """.formatted("{interactionCount}", "{availableUsers}", "{maxPerUser}", toneDirective, MODERATION_SAFE_WRITING,
                "{title}", "{sideA}", "{sideB}", "{category}", intensity);
    }

    public static String commentGenerationPrompt(String language, int intensity) {
        String toneDirective = IntensityMapper.toDirective(intensity);
        return """
            Genera UN comentario para un caso de debate.
            Tono: %s
            %s
            
            Caso: "%s"
            Side A: %s
            Side B: %s
            Postura del bot: %s (pro-A / pro-B / neutral)
            Intensidad: %d
            
            El comentario debe ser coherente con la postura, ≤ 1000 chars, en %s.
            Responde SOLO con JSON: { "content": "..." }
            """.formatted(toneDirective, MODERATION_SAFE_WRITING, "{title}", "{sideA}", "{sideB}", "{stance}", intensity, language);
    }

    public static String replyGenerationPrompt(String language, int intensity) {
        String toneDirective = IntensityMapper.toDirective(intensity);
        return """
            Genera UNA respuesta a un comentario en un caso de debate.
            Tono: %s
            %s
            
            Caso: "%s"
            Comentario padre: "%s"
            Postura del bot: %s (pro-A / pro-B / neutral)
            Intensidad: %d
            
            La respuesta debe responder al comentario padre, ser coherente con la postura, ≤ 1000 chars, en %s.
            Responde SOLO con JSON: { "content": "..." }
            """.formatted(toneDirective, MODERATION_SAFE_WRITING, "{title}", "{parentComment}", "{stance}", intensity, language);
    }
}