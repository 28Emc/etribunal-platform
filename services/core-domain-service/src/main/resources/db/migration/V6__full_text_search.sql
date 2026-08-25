-- tsvector column for full-text search on cases
-- Config: 'spanish' handles accents, stemming, and common Spanish stop words

-- 1. Add tsvector column
ALTER TABLE cases ADD COLUMN search_vector tsvector;

-- 2. Populate from existing data
UPDATE cases SET search_vector =
    setweight(to_tsvector('spanish', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('spanish', coalesce(side_a_content, '')), 'B') ||
    setweight(to_tsvector('spanish', coalesce(side_b_content, '')), 'C');

-- 3. GIN index for fast full-text search
CREATE INDEX idx_cases_search_vector ON cases USING GIN (search_vector);

-- 4. Trigger function: auto-update search_vector on INSERT/UPDATE
CREATE OR REPLACE FUNCTION cases_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('spanish', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('spanish', coalesce(NEW.side_a_content, '')), 'B') ||
        setweight(to_tsvector('spanish', coalesce(NEW.side_b_content, '')), 'C');
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

-- 5. Attach trigger to cases
CREATE TRIGGER trg_cases_search_vector
    BEFORE INSERT OR UPDATE OF title, side_a_content, side_b_content
    ON cases
    FOR EACH ROW
    EXECUTE FUNCTION cases_search_vector_update();
