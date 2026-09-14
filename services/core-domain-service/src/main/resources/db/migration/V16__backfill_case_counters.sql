-- Backfill idempotente de contadores de casos a partir del estado real.
-- Los counters se mantenían en writes, pero datos migrados/seeded podían
-- quedar desfasados (ej: trending con 0 comentarios). Esta migración los
-- reconcilia y corre SOLO UNA VEZ (Flyway la aplica y versiona).
UPDATE cases c SET
    total_comments = COALESCE((
        SELECT COUNT(*) FROM comments cm
        WHERE cm.case_id = c.id AND cm.deleted_at IS NULL
    ), 0),
    total_votes = COALESCE((
        SELECT COUNT(*) FROM case_votes cv
        WHERE cv.case_id = c.id
    ), 0),
    votes_a = COALESCE((
        SELECT COUNT(*) FROM case_votes cv
        WHERE cv.case_id = c.id AND cv.vote_type = 'A'
    ), 0),
    votes_b = COALESCE((
        SELECT COUNT(*) FROM case_votes cv
        WHERE cv.case_id = c.id AND cv.vote_type = 'B'
    ), 0),
    votes_both_wrong = COALESCE((
        SELECT COUNT(*) FROM case_votes cv
        WHERE cv.case_id = c.id AND cv.vote_type = 'BOTH_WRONG'
    ), 0);