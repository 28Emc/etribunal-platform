-- Analytics: permitir track-share de perfiles (sin case_id asociado)
ALTER TABLE interaction_logs ALTER COLUMN case_id DROP NOT NULL;

CREATE INDEX idx_interaction_logs_user_action ON interaction_logs(user_id, action);