-- A line number is how passengers and the seed name a line, so two lines may not share it
-- (ignoring case and surrounding spaces).
CREATE UNIQUE INDEX line_number_key ON line (lower(trim(number)));

-- A route with trips keeps its line and direction: moving it would silently move every trip
-- to another line or direction and break what their source refs mean.
CREATE FUNCTION route_keep_timetable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.line_id, NEW.direction) IS DISTINCT FROM (OLD.line_id, OLD.direction)
        AND EXISTS (SELECT 1 FROM trip WHERE route_id = OLD.id) THEN
        RAISE EXCEPTION 'route % has trips and cannot change line or direction', OLD.id
            USING ERRCODE = 'foreign_key_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER route_keep_timetable BEFORE UPDATE OF line_id, direction ON route
FOR EACH ROW EXECUTE FUNCTION route_keep_timetable();

-- V12 set the sequence to max(id) as already used, so an empty table would start at 2.
SELECT setval(pg_get_serial_sequence('route_stop', 'id'),
              COALESCE((SELECT max(id) FROM route_stop), 0) + 1, false);
