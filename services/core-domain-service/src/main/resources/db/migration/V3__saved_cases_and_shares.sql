-- V3__saved_cases_and_shares.sql
-- Saved cases (bookmarks) y Shares (compartidos)
-- Sin FKs a users (cross-service, está en identity-service)

CREATE TABLE saved_cases (
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    user_id UUID NOT NULL,
    case_id UUID NOT NULL,
    CONSTRAINT pk_saved_cases PRIMARY KEY (user_id, case_id),
    CONSTRAINT fk_saved_cases_case FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE CASCADE
);

CREATE INDEX idx_saved_cases_case_id ON saved_cases(case_id);

CREATE TABLE case_shares (
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    user_id UUID NOT NULL,
    case_id UUID NOT NULL,
    CONSTRAINT pk_case_shares PRIMARY KEY (user_id, case_id),
    CONSTRAINT fk_case_shares_case FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE CASCADE
);

CREATE INDEX idx_case_shares_case_id ON case_shares(case_id);