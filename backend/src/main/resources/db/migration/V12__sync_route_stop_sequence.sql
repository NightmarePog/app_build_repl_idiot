-- V10 added an identity column to route_stop with existing rows; move the sequence past them.
SELECT setval(pg_get_serial_sequence('route_stop', 'id'), COALESCE((SELECT max(id) FROM route_stop), 1));
