-- Automation settings: configuracion de negocio editable (Fase 4)
-- Params editables desde panel admin (BD primera, env como fallback).
-- Datos sensibles (api keys, secrets) se mantienen en .env y NO van aqui.
CREATE TABLE automation_settings (
    key VARCHAR(100) PRIMARY KEY,
    value JSONB NOT NULL,
    description TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
