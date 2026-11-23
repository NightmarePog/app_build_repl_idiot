-- One trip with its line, origin, destination and start (see the trip_summary view, V14).
SELECT * FROM trip_summary WHERE trip_id = :tripId
