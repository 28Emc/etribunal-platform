package com.etribunal.ai.automation.domain;

public class IntensityMapper {

    private IntensityMapper() {}

    public static String toDirective(int intensity) {
        if (intensity <= 20) return "Provocador, irónico, busca reacción emocional.";
        if (intensity <= 40) return "Crítico, directo, expone contradicciones sin filtro.";
        if (intensity <= 60) return "Equilibrado, razonado, argumentos claros y medidos.";
        if (intensity <= 80) return "Analítico, profundo, matices y evidencia.";
        return "Académico, riguroso, tono formal y exhaustivo.";
    }
}