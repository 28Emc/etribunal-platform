-- Moderation engine: logs for audit trail
CREATE TABLE moderation_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_type VARCHAR(20) NOT NULL,  -- CASE, COMMENT, CASE_IMAGE
    target_id UUID NOT NULL,
    content_text TEXT,
    moderation_status VARCHAR(20) NOT NULL,
    risk_score DOUBLE PRECISION,
    provider VARCHAR(20) NOT NULL,  -- LOCAL, OPENAI
    matched_rules JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_moderation_logs_target ON moderation_logs(target_type, target_id);
CREATE INDEX idx_moderation_logs_status ON moderation_logs(moderation_status);
CREATE INDEX idx_moderation_logs_created_at ON moderation_logs(created_at DESC);