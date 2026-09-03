-- Activity profile: distribución de actividad real por hora (scheduling ponderado 2.0)
CREATE TABLE activity_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hour_of_day INTEGER NOT NULL,             -- 0-23
    weight DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_activity_profile_hour UNIQUE (hour_of_day)
);

CREATE INDEX idx_activity_profile_hour ON activity_profile(hour_of_day);
