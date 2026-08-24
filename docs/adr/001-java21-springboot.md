# ADR-001: Java 21 LTS + Spring Boot 3.5.x como stack base

**Estado:** Aceptado · **Fecha:** 2026-08-21

## Contexto

El equipo tiene 5+ años de experiencia en Java/Spring. El monolito NestJS corre sobre
Node 20. La máquina de desarrollo tiene JDK 25; CI usa Temurin 21.

## Decisión

- Lenguaje: **Java 21 LTS** (toolchain Gradle fijado en 21; auto-provisionado vía Foojay
  si el JDK local difiere).
- Framework: **Spring Boot 3.5.x** con BOM de dependencias.
- Build: Gradle Kotlin DSL 9.x con wrapper.

Spring Boot 3.3 (versión originalmente planificada) fue elevada a 3.5 porque el plugin
de Boot ≤3.3 no es compatible con Gradle 9.x, requerido para correr el daemon sobre
JDK 25 en la estación de desarrollo. El lineal 3.x mantiene compatibilidad completa
con el diseño del plan.

## Consecuencias

- Virtual threads disponibles sin flag experimental.
- `options.release = 21` garantiza bytecode objetivo aunque se compile con JDK más nuevo.
- Actualizaciones de Boot dentro de 3.5.x = parches automáticos vía Dependabot semanal.
