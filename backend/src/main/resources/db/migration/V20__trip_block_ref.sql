-- The vehicle working a trip (trips.json block_ref, e.g. B03), shown to passengers as "Bus B03".
-- Nullable so a trip added later without a vehicle assignment is still valid; the seeded trips
-- are backfilled by their stable source reference.
ALTER TABLE trip ADD COLUMN block_ref VARCHAR(3)
    CONSTRAINT trip_block_ref_format CHECK (block_ref ~ '^[A-Z][0-9]{2}$');

UPDATE trip SET block_ref = seed.block_ref
FROM (VALUES
    ('T00101070001', 'A01'),
    ('T00102233201', 'A02'),
    ('T00201070001', 'B01'),
    ('T00202070001', 'B02'),
    ('T00201200001', 'B03'),
    ('T00202200901', 'B04'),
    ('T00201224901', 'B05'),
    ('T00202224901', 'B06'),
    ('T00301064201', 'C01'),
    ('T00301071201', 'C02'),
    ('T00301141801', 'C03'),
    ('T00301154301', 'C04'),
    ('T00301155801', 'C05'),
    ('T00302153001', 'C06'),
    ('T00301222501', 'C07'),
    ('T00301235201', 'C08')) AS seed (source_ref, block_ref)
WHERE trip.source_ref = seed.source_ref;

-- Same view as V14 with block_ref appended (CREATE OR REPLACE may only add trailing columns).
CREATE OR REPLACE VIEW trip_summary AS
SELECT trip.id AS trip_id, trip.route_id, route.direction::TEXT AS direction,
    line.id AS line_id, line.number, line.name AS line_name, line.type, line.color,
    origin.id AS origin_id, origin.name AS origin_name, origin.image_url AS origin_image,
    dest.id AS dest_id, dest.name AS dest_name, dest.image_url AS dest_image,
    first.departure_minute AS start_minute,
    trip.block_ref
FROM trip
JOIN route ON route.id = trip.route_id
JOIN line ON line.id = route.line_id
CROSS JOIN LATERAL (
    SELECT rs.stop_id, timetable_minute(st.departure_time) AS departure_minute
    FROM stop_time st JOIN route_stop rs ON rs.id = st.route_stop_id
    WHERE st.trip_id = trip.id ORDER BY rs.position LIMIT 1) first
CROSS JOIN LATERAL (
    SELECT rs.stop_id
    FROM stop_time st JOIN route_stop rs ON rs.id = st.route_stop_id
    WHERE st.trip_id = trip.id ORDER BY rs.position DESC LIMIT 1) last
JOIN station origin ON origin.id = first.stop_id
JOIN station dest ON dest.id = last.stop_id;
