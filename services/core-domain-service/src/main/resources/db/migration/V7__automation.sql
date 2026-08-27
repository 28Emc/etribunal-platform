-- Automation tables for AI Engine (Fase 3)
-- Maps: AutomationRun, AutomationCase, AutomationInteraction

-- 1. automation_runs
CREATE TABLE automation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    cases_requested INTEGER NOT NULL DEFAULT 0,
    cases_created INTEGER NOT NULL DEFAULT 0,
    cases_failed INTEGER NOT NULL DEFAULT 0,
    interactions_per_case INTEGER NOT NULL DEFAULT 0,
    interaction_intensity INTEGER NOT NULL DEFAULT 0,
    dry_run BOOLEAN NOT NULL DEFAULT true,
    metadata JSONB,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_automation_runs_status_created ON automation_runs(status, created_at);

-- 2. automation_cases
CREATE TABLE automation_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    automation_run_id UUID NOT NULL,
    case_id VARCHAR(36) UNIQUE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    target_interactions INTEGER NOT NULL DEFAULT 0,
    successful_interactions INTEGER NOT NULL DEFAULT 0,
    failed_interactions INTEGER NOT NULL DEFAULT 0,
    moderation_result VARCHAR(20),
    error_message TEXT,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_automation_cases_run
        FOREIGN KEY (automation_run_id)
        REFERENCES automation_runs(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_automation_cases_run ON automation_cases(automation_run_id);

-- 3. automation_interactions
CREATE TABLE automation_interactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    automation_case_id UUID NOT NULL,
    user_id VARCHAR(36),
    interaction_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(36),
    result_id VARCHAR(36),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    stance VARCHAR(20),
    tone VARCHAR(50),
    plan_index INTEGER,
    scheduled_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ,
    error_message TEXT,
    error_code VARCHAR(50),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_automation_interactions_case
        FOREIGN KEY (automation_case_id)
        REFERENCES automation_cases(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_automation_interactions_status_scheduled ON automation_interactions(status, scheduled_at);
CREATE INDEX idx_automation_interactions_case ON automation_interactions(automation_case_id);
CREATE UNIQUE INDEX idx_automation_interactions_case_plan ON automation_interactions(automation_case_id, plan_index);
