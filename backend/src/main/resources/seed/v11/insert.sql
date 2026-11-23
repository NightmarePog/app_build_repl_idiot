-- The assignment data names lines and routes by reference (L001, R00101); the phase-3 network
-- (V8) defines them by line number and direction. Map once here, explicitly; the checks below fail the
-- deploy if any trip still points at a reference that does not exist.
UPDATE line SET source_ref = map.ref
FROM (VALUES ('A', 'L001'), ('B', 'L002'), ('C', 'L003')) AS map (number, ref)
WHERE line.number = map.number;

UPDATE route SET source_ref = map.ref
FROM line, (VALUES
        ('A', 'outbound', 'R00101'), ('A', 'inbound', 'R00102'),
        ('B', 'outbound', 'R00201'), ('B', 'inbound', 'R00202'),
        ('C', 'outbound', 'R00301'), ('C', 'inbound', 'R00302')
    ) AS map (number, direction, ref)
WHERE line.id = route.line_id AND line.number = map.number
    AND route.direction = map.direction::route_direction;

-- Every trip must resolve to the route named by its line_ref and route_ref; direction_id 0 is
-- outbound and 1 inbound. Anything else aborts the deploy rather than booting with gaps.
DO $$
DECLARE
    bad TEXT;
BEGIN
    SELECT string_agg(seed.source_trip_id, ', ') INTO bad
    FROM seed_trip seed
    LEFT JOIN route ON route.source_ref = seed.route_ref
    LEFT JOIN line ON line.id = route.line_id AND line.source_ref = seed.line_ref
    WHERE line.id IS NULL
        OR seed.direction_id NOT IN (0, 1)
        OR route.direction <> (CASE seed.direction_id WHEN 0 THEN 'outbound' ELSE 'inbound' END)::route_direction;
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'Timetable seed: trips without a matching line, route or direction: %', bad;
    END IF;

    SELECT string_agg(seed.trip_id || '/' || seed.stop_sequence, ', ') INTO bad
    FROM seed_stop_time seed
    LEFT JOIN seed_trip trip ON trip.id = seed.trip_id
    LEFT JOIN route ON route.source_ref = trip.route_ref
    LEFT JOIN route_stop rs ON rs.route_id = route.id AND rs.position = seed.stop_sequence
    LEFT JOIN station ON station.id = rs.stop_id AND station.name = seed.stop_name
    WHERE station.id IS NULL
        OR seed.arrival_time !~ '^([0-3][0-9]|4[0-7]):[0-5][0-9]$'
        OR seed.departure_time !~ '^([0-3][0-9]|4[0-7]):[0-5][0-9]$';
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'Timetable seed: stop times that do not match the route stops: %', bad;
    END IF;
END $$;

INSERT INTO trip (id, route_id, source_ref)
SELECT seed.id, route.id, seed.source_trip_id
FROM seed_trip seed JOIN route ON route.source_ref = seed.route_ref;

INSERT INTO stop_time (trip_id, route_id, route_stop_id, arrival_time, departure_time)
SELECT seed.trip_id, rs.route_id, rs.id,
    (seed.arrival_time || ':00')::INTERVAL, (seed.departure_time || ':00')::INTERVAL
FROM seed_stop_time seed
JOIN trip ON trip.id = seed.trip_id
JOIN route_stop rs ON rs.route_id = trip.route_id AND rs.position = seed.stop_sequence;

SELECT setval(pg_get_serial_sequence('trip', 'id'), (SELECT MAX(id) FROM trip));
