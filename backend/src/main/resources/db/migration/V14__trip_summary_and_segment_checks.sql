-- Read side and the last timetable gaps.
-- Note for readers: trip_check_timetable() was created in V10 and replaced in full by V13; V13 is
-- the current body. New checks live in their own function below, so it is not copied a third time.

-- Minutes since midnight of the operating day; the one place that rounds a timetable time.
CREATE FUNCTION timetable_minute(t INTERVAL) RETURNS INT LANGUAGE sql IMMUTABLE PARALLEL SAFE
RETURN (EXTRACT(EPOCH FROM t) / 60)::INT;

-- One row per trip: line, and origin, destination and start from the trip's own first and last
-- timetabled stops. trip.sql and line-trips.sql both read it, so they cannot drift apart.
CREATE VIEW trip_summary AS
SELECT trip.id AS trip_id, trip.route_id, route.direction::TEXT AS direction,
    line.id AS line_id, line.number, line.name AS line_name, line.type, line.color,
    origin.id AS origin_id, origin.name AS origin_name, origin.image_url AS origin_image,
    dest.id AS dest_id, dest.name AS dest_name, dest.image_url AS dest_image,
    first.departure_minute AS start_minute
FROM trip
JOIN route ON route.id = trip.route_id
JOIN line ON line.id = route.line_id
-- LATERAL keeps the per-trip lookup index-driven, so WHERE trip_id = ? reads one trip only.
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

-- Two checks V13 left open: travel between two stops is bounded (a bus that takes hours between
-- neighbouring stops is a typo), and a route cannot run the same trip twice (same first departure).
CREATE FUNCTION trip_check_segments() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    checked BIGINT;
    checked_route BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'trip' THEN
        checked := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN
        checked := OLD.trip_id;
    ELSE
        checked := NEW.trip_id;
    END IF;
    SELECT route_id INTO checked_route FROM trip WHERE id = checked;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;
    IF EXISTS (
        SELECT 1 FROM (
            SELECT st.arrival_time,
                lag(st.departure_time) OVER (ORDER BY rs.position) AS previous
            FROM stop_time st JOIN route_stop rs ON rs.id = st.route_stop_id
            WHERE st.trip_id = checked
        ) ordered WHERE arrival_time - previous > INTERVAL '2 hours') THEN
        RAISE EXCEPTION 'trip % takes more than 2 hours between two stops', checked
            USING ERRCODE = 'check_violation';
    END IF;
    -- A whole trip stays within 12 hours; longer is a data error, not a bus run.
    IF (SELECT max(arrival_time) - min(departure_time) FROM stop_time WHERE trip_id = checked)
            > INTERVAL '12 hours' THEN
        RAISE EXCEPTION 'trip % runs longer than 12 hours', checked USING ERRCODE = 'check_violation';
    END IF;
    -- Same-start check only against trips of this route (trip_route index), never the whole table.
    IF EXISTS (
        SELECT 1 FROM trip other
        CROSS JOIN LATERAL (
            SELECT st.departure_time FROM stop_time st JOIN route_stop rs ON rs.id = st.route_stop_id
            WHERE st.trip_id = other.id ORDER BY rs.position LIMIT 1) other_start
        WHERE other.route_id = checked_route AND other.id <> checked
          AND timetable_minute(other_start.departure_time) = (
            SELECT timetable_minute(st.departure_time)
            FROM stop_time st JOIN route_stop rs ON rs.id = st.route_stop_id
            WHERE st.trip_id = checked ORDER BY rs.position LIMIT 1)) THEN
        RAISE EXCEPTION 'route % already has a trip leaving at the same time as trip %',
            checked_route, checked USING ERRCODE = 'unique_violation';
    END IF;
    RETURN NULL;
END $$;

CREATE CONSTRAINT TRIGGER trip_check_segments AFTER INSERT OR UPDATE ON trip
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION trip_check_segments();
CREATE CONSTRAINT TRIGGER stop_time_check_segments AFTER INSERT OR UPDATE OR DELETE ON stop_time
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION trip_check_segments();

-- A stop inserted between timetabled stops would be skipped by every existing trip and shift the
-- stop_order that clients already saw. Extending a timetabled route past its last stop is fine.
CREATE FUNCTION route_stop_keep_timetable_order() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM stop_time st JOIN route_stop rs ON rs.id = st.route_stop_id
        WHERE rs.route_id = NEW.route_id AND rs.position > NEW.position) THEN
        RAISE EXCEPTION 'route % has trips; a stop cannot be inserted between timetabled stops',
            NEW.route_id USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER route_stop_keep_timetable_order BEFORE INSERT ON route_stop
FOR EACH ROW EXECUTE FUNCTION route_stop_keep_timetable_order();

