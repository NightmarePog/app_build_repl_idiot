-- V15 put the terminal-dwell fold inside trip_check_timetable(), a deferred constraint trigger,
-- so the fold's own UPDATEs queued more deferred checks. The fold moves to its own ordinary
-- trigger here, and trip_check_timetable() goes back to only checking.
--
-- Current bodies after this migration:
--   trip_check_timetable()      this file (the V13 checks; V10, V13 and V15 bodies are history)
--   trip_check_segments()       V14
--   trip_fold_terminal_dwell()  this file
--
-- A trip may skip a stop of its route. That is intended: a phase-3 DELETE /stops/{id} removes the
-- station's calls from every trip, and the remaining calls stay valid. stops.sql reports
-- stop_order as the stop's place in the route, so a gap shows as a missing stop, not a renumber.

-- Runs right after each deleted call (not deferred), before any commit-time check. When the
-- deleted call was the trip's first or last, the call that is now the end may still have a dwell;
-- the dwell is folded into the departure (new first stop) or the arrival (new last stop). The
-- fold's UPDATE fires the deferred checks once more for this trip, which then pass.
CREATE FUNCTION trip_fold_terminal_dwell() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM trip WHERE id = OLD.trip_id) THEN
        RETURN NULL; -- the whole trip is being deleted
    END IF;
    UPDATE stop_time st SET arrival_time = st.departure_time
    FROM route_stop rs WHERE rs.id = st.route_stop_id AND st.trip_id = OLD.trip_id
      AND st.arrival_time <> st.departure_time
      AND rs.position = (SELECT min(r.position) FROM stop_time s JOIN route_stop r ON r.id = s.route_stop_id
                         WHERE s.trip_id = OLD.trip_id);
    UPDATE stop_time st SET departure_time = st.arrival_time
    FROM route_stop rs WHERE rs.id = st.route_stop_id AND st.trip_id = OLD.trip_id
      AND st.arrival_time <> st.departure_time
      AND rs.position = (SELECT max(r.position) FROM stop_time s JOIN route_stop r ON r.id = s.route_stop_id
                         WHERE s.trip_id = OLD.trip_id);
    RETURN NULL;
END $$;

CREATE TRIGGER stop_time_fold_terminal_dwell AFTER DELETE ON stop_time
FOR EACH ROW EXECUTE FUNCTION trip_fold_terminal_dwell();

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
