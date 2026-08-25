-- Fase 2 slice 2: Comments + Reactions (contrato heredado del monolito)
CREATE TABLE comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    parent_id UUID REFERENCES comments(id) ON DELETE CASCADE,
    content_language VARCHAR(10) NOT NULL DEFAULT 'es',
    is_anonymous BOOLEAN NOT NULL DEFAULT false,
    moderation_status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    risk_score DOUBLE PRECISION,
    case_id UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_comments_parent ON comments(parent_id);
CREATE INDEX idx_comments_case_created ON comments(case_id, created_at DESC);

CREATE TABLE reactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_type VARCHAR(10) NOT NULL CHECK (target_type IN ('CASE', 'COMMENT')),
    target_id UUID NOT NULL,
    emoji VARCHAR(10) NOT NULL CHECK (emoji IN ('LIKE', 'LOVE', 'ANGRY')),
    user_id UUID NOT NULL,
    case_id UUID REFERENCES cases(id) ON DELETE CASCADE,
    comment_id UUID REFERENCES comments(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_reactions_target_user_emoji UNIQUE (target_type, target_id, user_id, emoji)
);

CREATE INDEX idx_reactions_case ON reactions(case_id);
CREATE INDEX idx_reactions_comment ON reactions(comment_id);
