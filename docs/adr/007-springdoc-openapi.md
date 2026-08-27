# ADR-007: SpringDoc OpenAPI 2.8 para documentación de APIs

**Estado:** Aceptado · **Fecha:** 2026-08-26

## Contexto

Los 4 servicios exponen REST APIs pero no tenían documentación OpenAPI自动. Swagger UI era una dependencia pendiente desde ADR-006 (monorepo) que mencionaba "Contratos entre repos vía OpenAPI publicado desde cada servicio".

## Decisión

- **SpringDoc OpenAPI 2.8.6** con `springdoc-openapi-starter-webmvc-ui` para servicios WebMVC (identity, core-domain, ai-engine).
- **`springdoc-openapi-starter-webflux-ui`** para gateway (Spring Cloud Gateway es reactivo).
- Configuración vía `application.yml` con habilitación/deshabilitación por env var (`SPRINGDOC_API_DOCS_ENABLED`, `SPRINGDOC_SWAGGER_UI_ENABLED`).
- JWT Bearer security scheme documentado en cada servicio para testing autenticado desde Swagger UI.

## Consecuencias

- Swagger UI accesible en `{host}:{port}/{context-path}/swagger-ui.html`.
- OpenAPI JSON en `{host}:{port}/{context-path}/v3/api-docs`.
- Deshabilitable en producción vía env vars (ya configurado en application.yml de cada servicio).
- Los contratos entre repos se publican desde cada servicio (como preveía ADR-006).
