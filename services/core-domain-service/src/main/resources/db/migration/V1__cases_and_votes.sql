-- Fase 2 slice 1: Cases + Votes (contrato heredado del monolito)
-- SIN foreign keys a users: cross-service (identity-service posee esa tabla)
CREATE TABLE cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(10) NOT NULL CHECK (type IN ('classic', 'vote')),
    category VARCHAR(50) NOT NULL DEFAULT 'Other',
    status VARCHAR(10) NOT NULL DEFAULT 'WAITING' CHECK (status IN ('WAITING', 'PUBLIC')),
    title VARCHAR(100) NOT NULL,
    side_a_content TEXT NOT NULL,
    side_b_content TEXT,
    side_a_subtitle VARCHAR(50),
    side_b_subtitle VARCHAR(50),
    both_wrong_subtitle VARCHAR(50),
    content_language VARCHAR(10) NOT NULL DEFAULT 'es',
    invite_token VARCHAR(36) UNIQUE,
    is_anonymous BOOLEAN NOT NULL DEFAULT false,
    is_private BOOLEAN NOT NULL DEFAULT false,
    total_votes INTEGER NOT NULL DEFAULT 0,
    votes_a INTEGER NOT NULL DEFAULT 0,
    votes_b INTEGER NOT NULL DEFAULT 0,
    votes_both_wrong INTEGER NOT NULL DEFAULT 0,
    total_comments INTEGER NOT NULL DEFAULT 0,
    total_views INTEGER NOT NULL DEFAULT 0,
    total_shares INTEGER NOT NULL DEFAULT 0,
    total_anchors INTEGER NOT NULL DEFAULT 0,
    moderation_status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    risk_score DOUBLE PRECISION,
    side_a_user_id UUID NOT NULL,
    side_b_user_id UUID,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_cases_status ON cases(status);
CREATE INDEX idx_cases_created_at ON cases(created_at);
CREATE INDEX idx_cases_side_a_user ON cases(side_a_user_id);
CREATE INDEX idx_cases_side_b_user ON cases(side_b_user_id);

CREATE TABLE case_votes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vote_type VARCHAR(12) NOT NULL CHECK (vote_type IN ('A', 'B', 'BOTH_WRONG')),
    case_id UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_case_votes_case_user UNIQUE (case_id, user_id)
);

CREATE INDEX idx_case_votes_user ON case_votes(user_id);
