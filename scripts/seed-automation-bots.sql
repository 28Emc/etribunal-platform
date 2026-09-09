-- ============================================================================
--  eTribunal — Seed del pool de automatización (AI Daily Activity Engine)
-- ----------------------------------------------------------------------------
--  Espeja la lógica de veredixo_appi/prisma/seed-automation.ts adaptada a la
--  BD de identity (etribunal_identity), donde vive la tabla `users`.
--
--  PRERREQUISITO: la migración V4 (automation_enabled) debe estar aplicada.
--     flyway migra automáticamente al arrancar identity-service, o aplicar:
--        psql "postgresql://etribunal_user:etribunal_pass@localhost:7002/etribunal_identity" -f db/migration/V4__automation_pool.sql
--
--  USO:
--     docker run --rm --network host postgres:16.3-alpine psql \
--       "postgresql://etribunal_user:etribunal_pass@localhost:7002/etribunal_identity" \
--       -f scripts/seed-automation-bots.sql
--
--  Es IDEMPOTENTE: puede re-ejecutarse sin duplicar usuarios.
-- ============================================================================

BEGIN;

-- 1) Backfill: si existieran usuarios del seed marcables, ninguno manual puede
--    estar en el pool (misma garantia que el legacy).
UPDATE users SET automation_enabled = false
WHERE automation_enabled = true AND is_bot = false;

-- 2) Crear/habilitar los 25 usuarios bot con avatares realistas por género.
--    Password común para los bots (hash bcrypt de 'Bot@2026').
--    ON CONFLICT (username): reafirma el pool sin duplicar.

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot01@etsocial.local', 'maria_g', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Maria Garcia', 'https://randomuser.me/api/portraits/women/0.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot02@etsocial.local', 'lucia_f', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Lucia Fernandez', 'https://randomuser.me/api/portraits/women/1.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot03@etsocial.local', 'carla_m', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Carla Martinez', 'https://randomuser.me/api/portraits/women/2.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot04@etsocial.local', 'sofia_l', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Sofia Lopez', 'https://randomuser.me/api/portraits/women/3.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot05@etsocial.local', 'vale_p', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Vale Perez', 'https://randomuser.me/api/portraits/women/4.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot06@etsocial.local', 'camila_t', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Camila Torres', 'https://randomuser.me/api/portraits/women/5.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot07@etsocial.local', 'paula_r', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Paula Ramirez', 'https://randomuser.me/api/portraits/women/6.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot08@etsocial.local', 'elena_v', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Elena Vargas', 'https://randomuser.me/api/portraits/women/7.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot09@etsocial.local', 'laura_c', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Laura Castro', 'https://randomuser.me/api/portraits/women/8.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot10@etsocial.local', 'andrea_m', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Andrea Morales', 'https://randomuser.me/api/portraits/women/9.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot11@etsocial.local', 'clara_r', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Clara Ruiz', 'https://randomuser.me/api/portraits/women/10.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot12@etsocial.local', 'carolina_m', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Carolina Mendoza', 'https://randomuser.me/api/portraits/women/11.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot13@etsocial.local', 'nadia_c', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Nadia Castillo', 'https://randomuser.me/api/portraits/women/12.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot14@etsocial.local', 'daniela_o', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Daniela Ortiz', 'https://randomuser.me/api/portraits/women/13.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot15@etsocial.local', 'mateo_g', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Mateo Gutierrez', 'https://randomuser.me/api/portraits/men/14.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot16@etsocial.local', 'santiago_s', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Santiago Silva', 'https://randomuser.me/api/portraits/men/15.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot17@etsocial.local', 'nicolas_r', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Nicolas Rios', 'https://randomuser.me/api/portraits/men/16.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot18@etsocial.local', 'sebastian_m', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Sebastian Medina', 'https://randomuser.me/api/portraits/men/17.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot19@etsocial.local', 'javier_c', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Javier Cordoba', 'https://randomuser.me/api/portraits/men/18.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot20@etsocial.local', 'diego_s', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Diego Soto', 'https://randomuser.me/api/portraits/men/19.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot21@etsocial.local', 'andres_n', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Andres Navarro', 'https://randomuser.me/api/portraits/men/20.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot22@etsocial.local', 'cristian_h', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Cristian Herrera', 'https://randomuser.me/api/portraits/men/21.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot23@etsocial.local', 'braulio_a', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Braulio Aguilar', 'https://randomuser.me/api/portraits/men/22.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot24@etsocial.local', 'ernesto_p', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Ernesto Pena', 'https://randomuser.me/api/portraits/men/23.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES ('bot25@etsocial.local', 'gabriel_c', '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Gabriel Campos', 'https://randomuser.me/api/portraits/men/24.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET is_bot = EXCLUDED.is_bot, automation_enabled = EXCLUDED.automation_enabled, updated_at = NOW();

-- 3) Resumen del pool
SELECT
    count(*) FILTER (WHERE is_bot = true AND automation_enabled = true) AS bots_enabled,
    count(*) FILTER (WHERE is_bot = true AND automation_enabled = false) AS bots_disabled
FROM users;

COMMIT;