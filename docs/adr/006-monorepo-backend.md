# ADR-006: Monorepo Gradle para backend, repo separado para frontend

**Estado:** Aceptado · **Fecha:** 2026-08-21

## Contexto

Hay 4 módulos backend (3 libs compartidas + gateway) que evolucionan juntos. El frontend
(React) tiene ciclo de release independiente y tooling Node.

## Decisión

- **Backend → monorepo `etribunal-platform`** (este repo) con composite builds Gradle,
  catálogo de versiones central (`gradle/libs.versions.toml`) y CI por paths afectados.
- **Frontend → repo propio `etribunal-ui`** (React + Vite + TS).
- Los repos legacy (`veredixo_appi`, `veridixo`, `veredixo_ui_v2`) quedan intactos como
  referencia y fuente de contratos durante el Strangler Fig.

## Consecuencias

- Refactors atómicos cross-módulo en un solo PR.
- CI debe filtrar por paths para no compilar todo siempre (job único mientras el repo es chico;
  matrix por servicio cuando crezca).
- Contratos entre repos vía OpenAPI publicado desde cada servicio (Swagger UI habilitado
  salvo en producción).
