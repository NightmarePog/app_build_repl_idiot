-- Replaces trip_check_timetable() (V13 body) so deleting a trip's first or last station no longer
-- fails at commit: the new terminal's dwell is folded away instead of rejected.
CREATE OR REPLACE FUNCTION trip_check_timetable() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    checked BIGINT;
    calls INT;
    first_departure INTERVAL;
BEGIN
    -- Each branch only touches fields its table has; plpgsql resolves record fields lazily.
    IF TG_TABLE_NAME = 'trip' THEN
        checked := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN
        checked := OLD.trip_id;
    ELSE
        checked := NEW.trip_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM trip WHERE id = checked) THEN
        RETURN NULL;
    END IF;
    SELECT count(*) INTO calls FROM stop_time WHERE trip_id = checked;
    IF calls < 2 THEN
        IF TG_OP = 'DELETE' THEN
            -- Its stops were removed (phase-3 stop delete); a one-stop trip is no trip.
            DELETE FROM trip WHERE id = checked;
            RETURN NULL;
        END IF;
        RAISE EXCEPTION 'trip % needs at least two stop times', checked
            USING ERRCODE = 'check_violation';
    END IF;
    SELECT st.departure_time INTO first_departure
    FROM stop_time st JOIN route_stop rs ON rs.id = st.route_stop_id
    WHERE st.trip_id = checked ORDER BY rs.position LIMIT 1;
    IF first_departure >= INTERVAL '24 hours' THEN
        RAISE EXCEPTION 'trip % must start before 24:00 of its operating day', checked
            USING ERRCODE = 'check_violation';
    END IF;
    -- A phase-3 stop delete can remove a trip's first or last call; the next call becomes the new
    -- end and may still carry its dwell. Fold the dwell into the departure (first stop) or the
    -- arrival (last stop) so deleting a terminal station keeps working.
    IF TG_OP = 'DELETE' THEN
        UPDATE stop_time st SET arrival_time = st.departure_time
        FROM route_stop rs WHERE rs.id = st.route_stop_id AND st.trip_id = checked
          AND st.arrival_time <> st.departure_time
          AND rs.position = (SELECT min(r.position) FROM stop_time s JOIN route_stop r ON r.id = s.route_stop_id
                             WHERE s.trip_id = checked);
        UPDATE stop_time st SET departure_time = st.arrival_time
        FROM route_stop rs WHERE rs.id = st.route_stop_id AND st.trip_id = checked
          AND st.arrival_time <> st.departure_time
          AND rs.position = (SELECT max(r.position) FROM stop_time s JOIN route_stop r ON r.id = s.route_stop_id
                             WHERE s.trip_id = checked);
    END IF;
    -- A trip starts when it leaves its first stop and ends when it reaches its last one, so
    -- neither end has a dwell: otherwise the first arrival (or last departure) could be any time.
    IF EXISTS (
        SELECT 1 FROM (
            SELECT st.arrival_time, st.departure_time,
                rs.position = min(rs.position) OVER () AS is_first,
                rs.position = max(rs.position) OVER () AS is_last
            FROM stop_time st JOIN route_stop rs ON rs.id = st.route_stop_id
            WHERE st.trip_id = checked
        ) ends WHERE (is_first OR is_last) AND arrival_time <> departure_time) THEN
        RAISE EXCEPTION 'trip % must arrive and depart at the same time at its first and last stop', checked
            USING ERRCODE = 'check_violation';
    END IF;
    IF EXISTS (
        SELECT 1 FROM (
            SELECT st.arrival_time,
                lag(st.departure_time) OVER (ORDER BY rs.position) AS previous
            FROM stop_time st JOIN route_stop rs ON rs.id = st.route_stop_id
            WHERE st.trip_id = checked
        ) ordered WHERE arrival_time < previous) THEN
        RAISE EXCEPTION 'trip % arrives at a stop before leaving the previous one', checked
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END $$;
