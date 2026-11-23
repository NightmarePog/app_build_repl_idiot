package cloud.twoidiotz.app.v1.domain.trip;

import cloud.twoidiotz.app.v1.domain.line.RouteDirection;
import cloud.twoidiotz.app.v1.domain.trip.TripRow.Departure;
import cloud.twoidiotz.app.v1.domain.trip.TripRow.Line;
import cloud.twoidiotz.app.v1.domain.trip.TripRow.Stop;
import cloud.twoidiotz.app.v1.domain.trip.TripRow.StopTime;
import cloud.twoidiotz.app.v1.domain.trip.TripRow.Trip;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Timetable reads in plain SQL (resources/sql/trip); times are INTERVALs read back as minutes. */
@Repository
class TripRepository {
  private static final String TRIP = sql("trip.sql");
  private static final String LINE_TRIPS = sql("line-trips.sql");
  private static final String STOPS = sql("stops.sql");
  private static final String DEPARTURES = sql("departures.sql");

  private final NamedParameterJdbcTemplate jdbc;

  TripRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Optional<Trip> trip(long tripId) {
    return jdbc
        .query(TRIP, new MapSqlParameterSource("tripId", tripId), (row, index) -> trip(row))
        .stream()
        .findFirst();
  }

  List<Trip> ofLine(long lineId) {
    return jdbc.query(
        LINE_TRIPS, new MapSqlParameterSource("lineId", lineId), (row, index) -> trip(row));
  }

  List<StopTime> stops(long tripId) {
    return jdbc.query(
        STOPS,
        new MapSqlParameterSource("tripId", tripId),
        (row, index) ->
            new StopTime(
                row.getInt("stop_order"),
                stop(row, "id", "name", "image_url"),
                row.getInt("arrival_minute"),
                row.getInt("departure_minute")));
  }

  List<Departure> departures(long stopId) {
    return jdbc.query(
        DEPARTURES,
        new MapSqlParameterSource("stopId", stopId),
        (row, index) ->
            new Departure(
                row.getLong("trip_id"),
                line(row),
                RouteDirection.valueOf(row.getString("direction")),
                stop(row, "dest_id", "dest_name", "dest_image"),
                row.getInt("arrival_minute"),
                row.getInt("departure_minute"),
                row.getString("block_ref")));
  }

  private static Trip trip(ResultSet row) throws SQLException {
    return new Trip(
        row.getLong("trip_id"),
        row.getLong("route_id"),
        line(row),
        RouteDirection.valueOf(row.getString("direction")),
        stop(row, "origin_id", "origin_name", "origin_image"),
        stop(row, "dest_id", "dest_name", "dest_image"),
        row.getInt("start_minute"),
        row.getString("block_ref"));
  }

  private static Line line(ResultSet row) throws SQLException {
    return new Line(
        row.getLong("line_id"),
        row.getString("number"),
        row.getString("line_name"),
        row.getString("type"),
        row.getString("color"));
  }

  private static Stop stop(ResultSet row, String id, String name, String image)
      throws SQLException {
    return new Stop(row.getLong(id), row.getString(name), row.getString(image));
  }

  private static String sql(String file) {
    try {
      return new ClassPathResource("sql/trip/" + file).getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read SQL resource " + file, exception);
    }
  }
}
