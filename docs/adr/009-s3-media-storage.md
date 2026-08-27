# ADR-009: AWS S3 (via LocalStack/Floci) para almacenamiento de media

**Estado:** Aceptado · **Fecha:** 2026-08-26

## Contexto

Los casos de eTribunal permiten imágenes adjuntas (evidencia). Se necesita almacenamiento de objetos escalable con acceso público para imágenes.

## Decisión

- **AWS SDK S3 v2.31.25** (`software.amazon.awssdk:s3`) para upload/descarga.
- **Presigned URLs** para upload directo del frontend a S3 (sin pasar por el backend).
- **LocalStack/Floci** como emulador local (`:4566`), AWS S3 real en producción.
- **Bucket:** `etribunal-media` (configurable via `S3_BUCKET`).
- **Límites:** 5MB por imagen, MIME types: JPEG, PNG, GIF, WebP.

## Consecuencias

- El backend solo genera presigned URLs y confirma uploads (no maneja binarios).
- Flujo: `POST /upload-url` → frontend PUT directo a S3 → `POST /confirm`.
- `MediaUploadedEvent` se publica a Kafka después de confirmar (futuro: trigger de moderación de imágenes).
- En producción, se reemplaza Floci por AWS S3 real cambiando `S3_ENDPOINT` y credenciales.
- Las URLs públicas de S3 son permanentes (CloudFront recomendado en producción para CDN + cache).
