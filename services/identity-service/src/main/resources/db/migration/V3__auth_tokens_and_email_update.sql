-- Token-based flows: email verification + password reset
ALTER TABLE users
    ADD COLUMN verification_token VARCHAR(64),
    ADD COLUMN verification_expires TIMESTAMPTZ,
    ADD COLUMN reset_token VARCHAR(64),
    ADD COLUMN reset_token_expires TIMESTAMPTZ;

CREATE INDEX idx_users_verification_token ON users(verification_token) WHERE verification_token IS NOT NULL;
CREATE INDEX idx_users_reset_token ON users(reset_token) WHERE reset_token IS NOT NULL;
