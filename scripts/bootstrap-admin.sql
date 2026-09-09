-- eTribunal - Promueve un usuario a ADMIN para acceder al panel /admin/motor-ia
-- Uso: Editar USERNAME_TARGET abajo y ejecutar en la BD etribunal_identity (puerto 7002)
--   psql -h localhost -p 7002 -U etribunal_user -d etribunal_identity -f scripts/bootstrap-admin.sql

\set USERNAME_TARGET 'tu_usuario_aqui'

UPDATE public.users
SET role = 'ADMIN'
WHERE username = :'USERNAME_TARGET'
  AND role <> 'ADMIN';

SELECT id, username, email, role, created_at
FROM public.users
WHERE username = :'USERNAME_TARGET';