-- Every call of every trip, trips in id order and calls in route order (route_stop.position),
-- with the trip's line, direction and destination from the trip_summary view (V14). The planner
-- builds its timetable graph from these rows; minutes count from midnight of the operating day.
SELECT ts.trip_id, ts.direction, ts.line_id, ts.number, ts.line_name, ts.type, ts.color,
    ts.dest_id, ts.dest_name, ts.dest_image, ts.block_ref,
    station.id AS stop_id, station.name AS stop_name, station.image_url AS stop_image,
    timetable_minute(st.arrival_time) AS arrival_minute,
    timetable_minute(st.departure_time) AS departure_minute
FROM stop_time st
JOIN route_stop rs ON rs.id = st.route_stop_id
JOIN station ON station.id = rs.stop_id
JOIN trip_summary ts ON ts.trip_id = st.trip_id
ORDER BY ts.trip_id, rs.position
