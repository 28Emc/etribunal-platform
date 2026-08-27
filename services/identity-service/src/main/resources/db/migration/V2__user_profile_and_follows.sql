-- Fase 1 Sprint 3: campos de perfil heredados del monolito + tabla followers
ALTER TABLE users
    ADD COLUMN bio VARCHAR(255),
    ADD COLUMN is_anonymous BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN is_bot BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN receive_notifications BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN language VARCHAR(10) NOT NULL DEFAULT 'es',
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN last_login TIMESTAMPTZ,
    ADD COLUMN total_shares INTEGER NOT NULL DEFAULT 0;

CREATE TABLE followers (
    follower_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    following_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, following_id)
);

CREATE INDEX idx_followers_following ON followers(following_id);
