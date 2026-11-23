-- The planner and stop search find stops by name, so two stops may not share one
-- (case and surrounding spaces ignored). Duplicates are rejected with 409 by the API.
CREATE UNIQUE INDEX station_name_unique ON station (lower(btrim(name)));
