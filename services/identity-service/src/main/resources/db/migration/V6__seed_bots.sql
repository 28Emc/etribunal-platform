-- eTribunal — Pool de automatización (AI Daily Activity Engine)
-- ----------------------------------------------------------------------------
--  Crea/habilita el pool de 25 usuarios bot (is_bot=true, automation_enabled=true)
--  para que el AI Engine genere contenido e interacciones.
--  Espeja scripts/seed-automation-bots.sql como migración editada (idempotente).
--
--  Hash bcrypt común ($2b, cost 10) de 'Bot@2026'.
--  Avatares realistas por género (randomuser.me) alineados al seed.

INSERT INTO users (email, username, password_hash, display_name, avatar_url, role, status, is_bot, is_anonymous, automation_enabled, receive_notifications, language, email_verified, created_at, updated_at)
VALUES
  ('bot01@etsocial.local', 'maria_g',      '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Maria Garcia',    'https://randomuser.me/api/portraits/women/0.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot02@etsocial.local', 'lucia_f',      '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Lucia Fernandez', 'https://randomuser.me/api/portraits/women/1.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot03@etsocial.local', 'carla_m',      '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Carla Martinez',  'https://randomuser.me/api/portraits/women/2.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot04@etsocial.local', 'sofia_l',      '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Sofia Lopez',     'https://randomuser.me/api/portraits/women/3.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot05@etsocial.local', 'vale_p',       '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Vale Perez',      'https://randomuser.me/api/portraits/women/4.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot06@etsocial.local', 'camila_t',     '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Camila Torres',   'https://randomuser.me/api/portraits/women/5.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot07@etsocial.local', 'paula_r',      '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Paula Ramirez',   'https://randomuser.me/api/portraits/women/6.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot08@etsocial.local', 'elena_v',      '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Elena Vargas',    'https://randomuser.me/api/portraits/women/7.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot09@etsocial.local', 'laura_c',      '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Laura Castro',    'https://randomuser.me/api/portraits/women/8.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot10@etsocial.local', 'andrea_m',     '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Andrea Morales',  'https://randomuser.me/api/portraits/women/9.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot11@etsocial.local', 'clara_r',      '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Clara Ruiz',      'https://randomuser.me/api/portraits/women/10.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot12@etsocial.local', 'carolina_m',   '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Carolina Mendoza', 'https://randomuser.me/api/portraits/women/11.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot13@etsocial.local', 'nadia_c',      '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Nadia Castillo',  'https://randomuser.me/api/portraits/women/12.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot14@etsocial.local', 'daniela_o',    '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Daniela Ortiz',   'https://randomuser.me/api/portraits/women/13.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot15@etsocial.local', 'mateo_g',      '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Mateo Gutierrez', 'https://randomuser.me/api/portraits/men/14.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot16@etsocial.local', 'santiago_s',   '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Santiago Silva',  'https://randomuser.me/api/portraits/men/15.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot17@etsocial.local', 'nicolas_r',    '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Nicolas Rios',    'https://randomuser.me/api/portraits/men/16.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot18@etsocial.local', 'sebastian_m',  '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Sebastian Medina', 'https://randomuser.me/api/portraits/men/17.jpg', 'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot19@etsocial.local', 'javier_c',     '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Javier Cordoba',  'https://randomuser.me/api/portraits/men/18.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot20@etsocial.local', 'diego_s',      '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Diego Soto',      'https://randomuser.me/api/portraits/men/19.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot21@etsocial.local', 'andres_n',     '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Andres Navarro',  'https://randomuser.me/api/portraits/men/20.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot22@etsocial.local', 'cristian_h',   '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Cristian Herrera','https://randomuser.me/api/portraits/men/21.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot23@etsocial.local', 'braulio_a',    '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Braulio Aguilar', 'https://randomuser.me/api/portraits/men/22.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot24@etsocial.local', 'ernesto_p',    '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Ernesto Pena',    'https://randomuser.me/api/portraits/men/23.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW()),
  ('bot25@etsocial.local', 'gabriel_c',    '$2b$10$gJNxlsiQB00WSB0YT9aVvu/u22e58myf5B6VGxCCC/zSeEjRO7x3u', 'Gabriel Campos',  'https://randomuser.me/api/portraits/men/24.jpg',  'USER', 'ACTIVE', true, false, true, false, 'es', true, NOW(), NOW())
ON CONFLICT (username) DO UPDATE SET
  is_bot = EXCLUDED.is_bot,
  automation_enabled = EXCLUDED.automation_enabled,
  updated_at = NOW();