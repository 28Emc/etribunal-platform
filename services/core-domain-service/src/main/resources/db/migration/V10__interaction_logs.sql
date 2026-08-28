-- Analytics: interaction tracking (parity legacy)
CREATE TABLE interaction_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action VARCHAR(20) NOT NULL,   -- VIEW, VOTE, COMMENT, REACTION, SAVE, SHARE
    case_id UUID NOT NULL,
    user_id UUID,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_interaction_logs_action_created ON interaction_logs(action, created_at DESC);
CREATE INDEX idx_interaction_logs_case ON interaction_logs(case_id, created_at DESC);
CREATE INDEX idx_interaction_logs_user ON interaction_logs(user_id, created_at DESC);