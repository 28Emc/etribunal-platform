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

    public static final String CASE_JSON_SCHEMA = """
        SCHEMA JSON OBLIGATORIO (respeta EXACTAMENTE estos nombres de campo):
        {
          "title": "string (≤ 120 chars)",
          "description": "string (≤ 500 chars)",
          "sideAContent": "string (≤ 1000 chars)",
          "sideBContent": "string (≤ 1000 chars, usa \\"\\" si no aplica)",
          "category": "Relationship | Friendship | Work | Family | Other",
          "caseType": "classic | vote",
          "sideASubtitle": "string (≤ 30 chars)",
          "sideBSubtitle": "string (≤ 30 chars)",
          "bothWrongSubtitle": "string (≤ 30 chars)",
          "metadata": { }
        }
        IMPORTANTE: los argumentos van en sideAContent/sideBContent. NO uses "sideA"/"sideB" como nombres de campo.
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
            - Categoría: una de [Relationship, Friendship, Work, Family, Other].
            - Tipo: "classic" (solo Side A, debate abierto) o "vote" (tiene Side B, espera respuesta).
            - Subtítulos personalizados para botones de voto (solo si vote): sideASubtitle, sideBSubtitle, bothWrongSubtitle (≤ 30 chars cada uno).
            
            %s

            %s

            Evita duplicados con estos temas recientes: %s
            Semilla de variación: %s

            %s

            Responde SOLO con JSON válido según el schema proporcionado.
            """.formatted(language, toneDirective, MODERATION_SAFE_WRITING, "{successExamples}", "{liveContext}", "{recentTopics}", "{variationSeed}", CASE_JSON_SCHEMA);
    }

    public static String interactionPlanningPrompt(String language) {
        return """
            Eres un planificador de interacciones para un caso de debate.
            Recomendaciones de intensidad: {interactionCount} ({availableUsers} usuarios disponibles, máx {maxPerUser} por usuario).
            %s
            
            Caso: "%s"
            Side A: %s
            Side B: %s
            Categoría: %s
            
            Responde SOLO con un JSON que cumpla EXACTAMENTE este esquema:
            {
              "interactions": [
                {
                  "type": "COMMENT",
                  "stance": "pro-A | pro-B | neutral",
                  "tone": 0-100,
                  "content": "string",
                  "reaction": null,
                  "option": null,
                  "replyToIndex": null
                }
              ]
            }
            
            Tipos válidos y campos requeridos:
            - COMMENT: type + stance + tone + content
            - REPLY: type + stance + tone + content + replyToIndex (índice del COMMENT al que responde, 0-based)
            - REACTION: type + stance (no para reacciones) + reaction (LIKE, LOVE, ANGRY)
            - VOTE: type + option (A, B, BOTH_WRONG)
            
            NO uses "sideA"/"sideB" ni otros nombres de campo. Distribuye stances (pro-A, pro-B, neutral) según el caso.
            """.formatted("{interactionCount}", "{availableUsers}", "{maxPerUser}", MODERATION_SAFE_WRITING,
                "{title}", "{sideA}", "{sideB}", "{category}");
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