package cloud.twoidiotz.app.v1.domain.connection;

import cloud.twoidiotz.app.v1.domain.line.RouteDirection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads the whole timetable (it is small) for one planner search. */
@Repository
class ConnectionRepository {
  private static final String CALLS = sql();

  record Line(long id, String number, String name, String type, String color) {}

  record Stop(long id, String name, String imageUrl) {}

  record TripInfo(
      long id, Line line, RouteDirection direction, Stop destination, String blockRef) {}

  record Timetable(Map<Long, TripInfo> trips, Map<Long, Stop> stops, List<Planner.Trip> calls) {}

  private final NamedParameterJdbcTemplate jdbc;

  ConnectionRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Timetable timetable() {
    Map<Long, TripInfo> trips = new LinkedHashMap<>();
    Map<Long, Stop> stops = new LinkedHashMap<>();
    Map<Long, List<Planner.Call>> calls = new LinkedHashMap<>();
    jdbc.query(
        CALLS,
        row -> {
          var tripId = row.getLong("trip_id");
          if (!trips.containsKey(tripId))
            trips.put(
                tripId,
                new TripInfo(
                    tripId,
                    new Line(
                        row.getLong("line_id"),
                        row.getString("number"),
                        row.getString("line_name"),
                        row.getString("type"),
                        row.getString("color")),
                    RouteDirection.valueOf(row.getString("direction")),
                    new Stop(
                        row.getLong("dest_id"),
                        row.getString("dest_name"),
                        row.getString("dest_image")),
                    row.getString("block_ref")));
          var stop =
              new Stop(
                  row.getLong("stop_id"), row.getString("stop_name"), row.getString("stop_image"));
          stops.putIfAbsent(stop.id(), stop);
          calls
              .computeIfAbsent(tripId, id -> new ArrayList<>())
              .add(
                  new Planner.Call(
                      stop.id(), row.getInt("arrival_minute"), row.getInt("departure_minute")));
        });
    var planned =
        calls.entrySet().stream()
            .map(entry -> new Planner.Trip(entry.getKey(), List.copyOf(entry.getValue())))
            .toList();
    return new Timetable(trips, stops, planned);
  }

  private static String sql() {
    try {
      return new ClassPathResource("sql/connection/calls.sql")
          .getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read SQL resource calls.sql", exception);
    }
  }
}
