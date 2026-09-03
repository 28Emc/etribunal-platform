-- Engagement: performance por caso generado por IA (feedback loop 2.0)
CREATE TABLE case_performance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID UNIQUE NOT NULL,
    engagement_score INTEGER NOT NULL DEFAULT 0,
    votes INTEGER NOT NULL DEFAULT 0,
    comments INTEGER NOT NULL DEFAULT 0,
    reactions INTEGER NOT NULL DEFAULT 0,
    shares INTEGER NOT NULL DEFAULT 0,
    saves INTEGER NOT NULL DEFAULT 0,
    views INTEGER NOT NULL DEFAULT 0,
    evaluation_date DATE NOT NULL DEFAULT CURRENT_DATE,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_case_performance_score ON case_performance(engagement_score DESC);
CREATE INDEX idx_case_performance_evaluation ON case_performance(evaluation_date DESC);
