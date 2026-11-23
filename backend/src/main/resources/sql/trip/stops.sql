-- stop_order is the stop's place in the route (route_stop.position, renumbered without the gaps
-- a deleted stop leaves), so it matches GET /lines/{id} even if a trip skips a stop.
WITH ordered AS (
    SELECT rs.id, rs.stop_id, rs.position,
        row_number() OVER (ORDER BY rs.position)::INT AS stop_order
    FROM route_stop rs
    WHERE rs.route_id = (SELECT route_id FROM trip WHERE id = :tripId)
)
SELECT station.id, station.name, station.image_url, ordered.stop_order,
    timetable_minute(st.arrival_time) AS arrival_minute,
    timetable_minute(st.departure_time) AS departure_minute
FROM stop_time st
JOIN ordered ON ordered.id = st.route_stop_id
JOIN station ON station.id = ordered.stop_id
WHERE st.trip_id = :tripId
ORDER BY ordered.position
