-- Fase 4 slice 14: Media — case_images table
-- Migración desde Cloudinary → S3 presigned URLs
CREATE TABLE case_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    side VARCHAR(1) NOT NULL DEFAULT 'A' CHECK (side IN ('A', 'B')),
    url TEXT NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255),
    mime_type VARCHAR(50),
    file_size INTEGER,
    width INTEGER,
    height INTEGER,
    order_index INTEGER NOT NULL DEFAULT 0,
    moderation_status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    risk_score DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_case_images_case_id ON case_images(case_id);
CREATE INDEX idx_case_images_case_side ON case_images(case_id, side);
CREATE INDEX idx_case_images_moderation ON case_images(moderation_status);