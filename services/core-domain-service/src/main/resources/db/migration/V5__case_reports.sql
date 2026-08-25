-- V5__case_reports.sql
-- Case reports (sin FK a users cross-service)

-- Eliminar enum type anterior si existe
DROP TYPE IF EXISTS report_status;

-- Eliminar columnas antiguas si existen (con enum type)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'cases' AND column_name = 'report_status'
    ) THEN
        ALTER TABLE cases DROP COLUMN report_status;
    END IF;
    
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'cases' AND column_name = 'report_reason'
    ) THEN
        ALTER TABLE cases DROP COLUMN report_reason;
    END IF;
    
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'cases' AND column_name = 'reported_by_id'
    ) THEN
        ALTER TABLE cases DROP COLUMN reported_by_id;
    END IF;
END $$;

-- Agregar campos nuevos como VARCHAR
ALTER TABLE cases ADD COLUMN report_status VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE cases ADD COLUMN report_reason TEXT;
ALTER TABLE cases ADD COLUMN reported_by_id UUID;

CREATE TABLE case_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL,
    reporter_id UUID NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT fk_case_reports_case FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE CASCADE
);

CREATE INDEX idx_case_reports_case_id ON case_reports(case_id);