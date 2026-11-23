-- V20 backfilled block_ref from a hand-typed copy of trips.json. Fail loudly if any seeded trip
-- (source_ref T...) was missed instead of silently leaving it NULL.
DO $$
DECLARE missing TEXT;
BEGIN
    SELECT string_agg(source_ref, ', ' ORDER BY source_ref) INTO missing
    FROM trip WHERE source_ref ~ '^T' AND block_ref IS NULL;
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'seeded trips without block_ref: %', missing;
    END IF;
END $$;

-- One vehicle block may work several trips in a day (as GTFS block_id), so block_ref is
-- deliberately not unique. Index it for "what else does this bus run" lookups.
CREATE INDEX trip_block_ref_idx ON trip (block_ref) WHERE block_ref IS NOT NULL;
COMMENT ON COLUMN trip.block_ref IS
    'Vehicle block (trips.json block_ref, e.g. B03). NULL = no vehicle assigned; not unique, a block may run several trips.';
