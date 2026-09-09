-- Pool de automatización (AI Daily Activity Engine):
-- habilita usuarios elegibles como bots para el motor de generación de contenido.
ALTER TABLE users
    ADD COLUMN automation_enabled BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_users_automation_pool
    ON users(is_bot, automation_enabled)
    WHERE deleted_at IS NULL AND is_anonymous = false;