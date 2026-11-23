-- Departures from the stop: every call except a trip's last one, where the bus only arrives.
-- Line, destination and start minute come from the trip_summary view (V14), the one definition
-- of a trip's origin, destination and start shared with trip.sql and line-trips.sql. A station is
-- on a route at most once (route_stop_station_once), so "not the destination" means "not the last
-- call". Minutes count from midnight of the operating day and every trip starts on that day (V10),
-- so ordering by operating-day minute is ordering by (departure_day_offset, departure_time); ties
-- go by line number (numeric numbers numerically, so 2 before 10), then trip id.
SELECT ts.trip_id, ts.direction, ts.line_id, ts.number, ts.line_name, ts.type, ts.color,
    ts.dest_id, ts.dest_name, ts.dest_image,
    timetable_minute(st.arrival_time) AS arrival_minute,
    timetable_minute(st.departure_time) AS departure_minute
FROM route_stop rs
JOIN stop_time st ON st.route_stop_id = rs.id
JOIN trip_summary ts ON ts.trip_id = st.trip_id
WHERE rs.stop_id = :stopId AND ts.dest_id <> :stopId
ORDER BY departure_minute,
    CASE WHEN ts.number ~ '^[0-9]+$' THEN length(ts.number) END NULLS LAST, ts.number, ts.trip_id
