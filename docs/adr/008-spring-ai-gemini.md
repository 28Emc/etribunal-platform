# ADR-008: Spring AI 1.1.8 + Google Gemini como proveedor de IA

**Estado:** Aceptado · **Fecha:** 2026-08-26

## Contexto

El AI Engine Service necesita generar casos de debate, planes de interacción, y contenido para bot automation. Se evaluaron OpenAI, Google Gemini, y Anthropic Claude.

## Decisión

- **Spring AI 1.1.8** (compatible con Spring Boot 3.5.x; 2.x requiere Boot 4.x).
- **Proveedor:** Google Gemini via `spring-ai-starter-model-vertex-ai-gemini`.
- **Modelo:** `gemini-2.0-flash` (rápido, costo bajo, suficiente para generación de texto).
- **Configuración:** API key via env var `AI_API_KEY` (Google AI Studio).
- **Rate limiting:** RPM=12, RPD=425, TPM=212500 (85% del peak oficial, 15% buffer).

## Consecuencias

- Spring AI abstraction permite cambiar de proveedor en el futuro (interfaz `AIProvider`).
- `GeminiProvider` es la única implementación actual; `OpenAIProvider` queda como scaffold.
- La BOM de Spring AI (`spring-ai-bom:1.1.8`) gestiona versiones de dependencias.
- El AI Engine comparte la DB de core-domain (no tiene DB propia).
