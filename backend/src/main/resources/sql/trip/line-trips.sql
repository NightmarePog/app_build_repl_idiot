-- All trips of one line (see the trip_summary view, V14). Every trip starts on the operating day
-- (V10 rejects a first departure at or after 24:00), so start minute order is (offset, time).
-- Same-minute trips follow the direction enum order, not its text.
SELECT trip_summary.* FROM trip_summary JOIN route ON route.id = trip_summary.route_id
WHERE trip_summary.line_id = :lineId
ORDER BY trip_summary.start_minute, route.direction, trip_summary.trip_id
