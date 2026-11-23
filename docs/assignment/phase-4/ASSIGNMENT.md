# Phase 4 – Routes and Schedules (Tour de App assignment, verbatim)

Attachments in this folder: `trips.json`, `stop_times.json`, `phase-4.openapi.yaml` (binding Swagger contract).
Earlier phases are done and tested by the platform; existing endpoints must keep their shape. Brand manual: docs/brand.

## Routes (task-32)
"The line exists. But that doesn't necessarily mean that a bus is actually running right now."
Add a structure to the database for storing individual trips. Each trip is a specific journey along an existing route. The route already specifies the line, direction, and order of stops, so do not unnecessarily duplicate this information in the trip.
A trip contains at least: `id` – a unique trip identifier, `route_id` – the identifier of the route the trip follows.
Each trip must be assigned to exactly one existing route. A single route can have multiple trips. Use the migration mechanism so the structure can be recreated in a new environment.

## Timetable for the route (task-34)
"A connection without a time frame is just a promise that something might go somewhere someday."
Add a schedule to each route. The schedule specifies the arrival and departure times for each stop along the route. Each entry: `trip_id`, `route_stop_id` (the specific stop on the route), `arrival_time`, `departure_time`.
Store the time in a format suitable for working with time; return it in `HH:MM` format in the API.
A timetable record must refer to a stop that is actually part of the route for that service. Use the order of stops from the route—do not create a second, independent order for the service.
Each stop on the route may appear at most once in the timetable for that service. The arrival time must not be later than the departure time.

## Routes and Schedules by the numbers (task-35)
"Theory is a beautiful thing. But passengers will appreciate most of all that they can find actual routes in the app."
Create a seed that loads all the provided data into the database when a new application instance is set up. After deployment, all routes, their paths, stops, and arrival and departure times must be available.
Make the data available via a REST API: a list of routes (trips) for a specific line; details of a specific route including the line, direction, and stops with times; a chronological overview of departures from a specific stop.
Use the endpoints defined in the attached Swagger specification. Return data in a structure that unambiguously identifies the line, direction, stops, and times.

## Routes of lines (task-33, frontend)
"Hilbert can now check the bus schedule... there's no mention of what time the bus leaves."
Expand the route (line) details to include a list of its trips. For each service display at least: the direction of travel, the scheduled departure time from the starting stop, a link to the service details. If a route has multiple directions, it must be clear which direction each service belongs to. Retrieve data from the REST API.

## Hilbert's Diary #1: A Typical Week (task-40) – all must be handled in UI and data
1. Morning, Ada Exchange → Campus Gate. The 'B' runs both directions, but I'm only interested in services going my way.
2. The 'B' leaves the terminus at 7.00. But standing at Ada Exchange, I want to know when it'll be here.
3. Syntax Square → Campus Gate. I want to know which route I'll take and where I'll be at what time.
4. At Kernel Hub, changing to the C bus. When checking the service, I want to see straight away from that stop what's going next.
5. At Kernel Hub, B or C doesn't matter. I want to know which one's coming first.
6. At Syntax Square buses wait a while. The one that arrives first often doesn't leave until after another one has. I need to know when each arrives and, most importantly, when it leaves.
7. The last 'C' from Edison East leaves 23:52 and arrives 00:14. It's still the last evening service, not the first morning one. (Source allows 24:00–47:59.)
8. Arrival 8.10, departure 8.10 – zero minutes dwell; show it sensibly.
9. C takes four minutes longer on the way back than there – directions have different times; don't assume symmetry.

## Upcoming locked tasks (prepare for, do not need to be complete): Route detail, Departures from the stop, The Transport Network Comes to Life.
