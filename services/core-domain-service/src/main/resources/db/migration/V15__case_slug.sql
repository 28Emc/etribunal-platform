-- V15__case_slug.sql
-- Add slug column to cases table for SEO-friendly URLs

ALTER TABLE cases ADD COLUMN slug VARCHAR(100);

-- Create index for slug lookups
CREATE INDEX idx_cases_slug ON cases (slug);

-- Populate slug for existing cases (slugify title)
UPDATE cases 
SET slug = lower(regexp_replace(title, '[^a-z0-9]+', '-', 'g'))
WHERE slug IS NULL;

-- Make slug unique where not null
CREATE UNIQUE INDEX idx_cases_slug_unique ON cases (slug) WHERE slug IS NOT NULL;