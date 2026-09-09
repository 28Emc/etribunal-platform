-- eTribunal — Usuario admin inicial para acceso a /admin/motor-ia
-- ----------------------------------------------------------------------------
--  Crea al usuario administrador admin@etribunal.com / Admin@2026 con rol ADMIN.
--  Se aplica una sola vez via Flyway; el ON CONFLICT lo hace re-ejecutable sin
--  duplicar (idempotente).
--
--  Hash bcrypt ($2b, cost 10) de 'Admin@2026'.

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_anonymous, is_bot, receive_notifications, language, email_verified, created_at, updated_at)
VALUES (
  'admin@etribunal.com',
  'admin',
  '$2b$10$mn8idB4s9ZUy1ypflkSbauh3m.UuGRCTzajPLfgPmfKkm9nDsJkH2',
  'Administrador',
  NULL,
  'ADMIN',
  'ACTIVE',
  false,
  false,
  true,
  'es',
  true,
  NOW(),
  NOW()
)
ON CONFLICT (email) DO UPDATE SET
  role = EXCLUDED.role,
  status = EXCLUDED.status,
  email_verified = EXCLUDED.email_verified,
  updated_at = NOW();