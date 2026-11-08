-- Provisional network taken from the stop seed (stops.csv "lines" column) and the published
-- campus map. Replace with the official network file once it is attached to the assignment.
INSERT INTO line (id, number, name, type, color) VALUES
    (1, 'A', 'Turing Terminal – Stack Garden', 'bus', '#2B90D9'),
    (2, 'B', 'Hopper Hall – Campus Gate', 'bus', '#91F5AD'),
    (3, 'C', 'Edison East – Quantum Commons', 'bus', '#49B3B4')
ON CONFLICT (id) DO NOTHING;

CREATE TEMP TABLE seed_line_stop (
    line_id BIGINT NOT NULL,
    position INTEGER NOT NULL,
    stop_name VARCHAR(255) NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_line_stop (line_id, position, stop_name) VALUES
    (1, 1, 'Turing Terminal'), (1, 2, 'Ada Exchange'), (1, 3, 'Pixel Park'),
    (1, 4, 'Byte Square'), (1, 5, 'Stack Garden'),
    (2, 1, 'Hopper Hall'), (2, 2, 'Ada Exchange'), (2, 3, 'Compiler Court'),
    (2, 4, 'Kernel Hub'), (2, 5, 'Syntax Square'), (2, 6, 'Lambda Lane'), (2, 7, 'Campus Gate'),
    (3, 1, 'Edison East'), (3, 2, 'Notebook Quay'), (3, 3, 'Kernel Hub'),
    (3, 4, 'Library Loop'), (3, 5, 'Byte Square'), (3, 6, 'Quantum Commons');

INSERT INTO route (line_id, direction)
SELECT id, direction
FROM line CROSS JOIN unnest(enum_range(NULL::route_direction)) AS direction
WHERE id IN (SELECT line_id FROM seed_line_stop)
ON CONFLICT (line_id, direction) DO NOTHING;

-- Outbound runs in the listed order, inbound runs it backwards.
INSERT INTO route_stop (route_id, stop_id, position)
SELECT route.id, station.id,
    CASE route.direction
        WHEN 'outbound' THEN seed.position
        ELSE (SELECT max(position) FROM seed_line_stop WHERE line_id = seed.line_id) - seed.position + 1
    END
FROM seed_line_stop seed
JOIN route ON route.line_id = seed.line_id
JOIN station ON station.name = seed.stop_name
ON CONFLICT DO NOTHING;

SELECT setval(pg_get_serial_sequence('line', 'id'),
    GREATEST((SELECT MAX(id) FROM line), (SELECT last_value FROM line_id_seq)));
