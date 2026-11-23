-- Departures from the stop: every call except a trip's last one, where the bus only arrives.
-- Minutes count from midnight of the operating day; the trip's own start minute lets the
-- service turn them into day offsets. Every trip starts on the operating day (V10 enforces it), so ordering by
-- operating-day minute is ordering by (departure_day_offset, departure_time).
WITH calls AS (
    SELECT st.trip_id, rs.stop_id, rs.position,
        timetable_minute(st.arrival_time) AS arrival_minute,
        timetable_minute(st.departure_time) AS departure_minute,
        first_value(timetable_minute(st.departure_time))
            OVER (PARTITION BY st.trip_id ORDER BY rs.position) AS start_minute,
        last_value(rs.stop_id) OVER (PARTITION BY st.trip_id ORDER BY rs.position
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS dest_id,
        max(rs.position) OVER (PARTITION BY st.trip_id) AS last_position
    FROM stop_time st JOIN route_stop rs ON rs.id = st.route_stop_id
    WHERE st.trip_id IN (SELECT st2.trip_id FROM stop_time st2
        JOIN route_stop rs2 ON rs2.id = st2.route_stop_id WHERE rs2.stop_id = :stopId)
)
SELECT calls.trip_id, route.direction::TEXT AS direction,
    line.id AS line_id, line.number, line.name AS line_name, line.type, line.color,
    dest.id AS dest_id, dest.name AS dest_name, dest.image_url AS dest_image,
    calls.arrival_minute, calls.departure_minute, calls.start_minute
FROM calls
JOIN trip ON trip.id = calls.trip_id
JOIN route ON route.id = trip.route_id
JOIN line ON line.id = route.line_id
JOIN station dest ON dest.id = calls.dest_id
WHERE calls.stop_id = :stopId AND calls.position < calls.last_position
ORDER BY calls.departure_minute, line.number, calls.trip_id
