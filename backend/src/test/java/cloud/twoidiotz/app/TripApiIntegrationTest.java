package cloud.twoidiotz.app;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TripApiIntegrationTest {
  private static final String TIME = "^([01][0-9]|2[0-3]):[0-5][0-9]$";

  @LocalServerPort int port;
  @Autowired DataSource dataSource;

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void seedsEveryTripAndStopTime() throws Exception {
    assertEquals(16, count("SELECT count(*) FROM trip"));
    assertEquals(100, count("SELECT count(*) FROM stop_time"));
  }

  @Test
  void listsTripsOfALineByDirection() throws Exception {
    var trips = get("/lines/2/trips");
    assertEquals(6, trips.size());
    var first = trips.get(0);
    assertEquals(
        Set.of(
            "id",
            "route_id",
            "line",
            "direction",
            "origin",
            "destination",
            "departure_time",
            "departure_day_offset"),
        keys(first));
    assertEquals(Set.of("id", "number", "name", "type", "color"), keys(first.get("line")));
    assertEquals(Set.of("id", "name", "image_url"), keys(first.get("origin")));
    assertEquals("B", first.get("line").get("number").asString());
    assertEquals("Hopper Hall", first.get("origin").get("name").asString());
    assertEquals("Campus Gate", first.get("destination").get("name").asString());
    assertEquals("07:00", first.get("departure_time").asString());
    var routes = get("/lines/2").get("routes");
    for (var trip : trips) {
      assertTrue(trip.get("departure_time").asString().matches(TIME));
      var route =
          routes
              .valueStream()
              .filter(r -> r.get("id").asLong() == trip.get("route_id").asLong())
              .findFirst()
              .orElseThrow();
      assertEquals(route.get("bound").asString(), trip.get("direction").asString());
    }
    assertError(404, "/lines/999999/trips");
    for (var id : List.of("0", "-1", "abc")) assertError(400, "/lines/" + id + "/trips");
  }

  @Test
  void returnsTripDetailWithOrderedStopsAndAfterMidnightTimes() throws Exception {
    var trip = get("/trips/301007");
    assertEquals("C", trip.get("line").get("number").asString());
    assertEquals("outbound", trip.get("direction").asString());
    assertEquals("23:52", trip.get("departure_time").asString());
    assertEquals(0, trip.get("departure_day_offset").asInt());
    var stops = trip.get("stops");
    assertEquals(6, stops.size());
    for (var index = 0; index < stops.size(); index++) {
      assertEquals(index + 1, stops.get(index).get("stop_order").asInt());
    }
    var last = stops.get(5);
    assertEquals("Quantum Commons", last.get("stop").get("name").asString());
    assertEquals("00:14", last.get("arrival_time").asString());
    assertEquals(1, last.get("arrival_day_offset").asInt());
    var kernel = stops.get(2);
    assertEquals("00:00", kernel.get("arrival_time").asString());
    assertEquals(1, kernel.get("departure_day_offset").asInt());
    assertError(404, "/trips/999999");
    assertError(400, "/trips/abc");
  }

  @Test
  void directionsHaveTheirOwnTravelTimes() throws Exception {
    // Diary #9: directions are not symmetric, so inbound is not outbound mirrored.
    var outbound = duration(get("/trips/301001").get("stops"));
    var inbound = duration(get("/trips/302001").get("stops"));
    assertTrue(inbound > outbound, outbound + " vs " + inbound);
  }

  @Test
  void listsDeparturesFromAStopChronologically() throws Exception {
    var kernelHub = stopId("Kernel Hub");
    var departures = get("/stops/" + kernelHub + "/departures");
    assertFalse(departures.isEmpty());
    assertEquals(
        Set.of(
            "trip_id",
            "line",
            "direction",
            "destination",
            "arrival_time",
            "arrival_day_offset",
            "departure_time",
            "departure_day_offset"),
        keys(departures.get(0)));
    var lines = new HashSet<String>();
    var previous = -1;
    for (var departure : departures) {
      var minutes =
          departure.get("departure_day_offset").asInt() * 1440
              + minutes(departure.get("departure_time").asString());
      assertTrue(minutes >= previous, "departures must be chronological");
      previous = minutes;
      lines.add(departure.get("line").get("number").asString());
    }
    assertEquals(Set.of("B", "C"), lines);

    // A trip's last stop is an arrival, not a departure: Quantum Commons has 8 stop_times in
    // the assignment data, 7 of them trips ending there.
    var terminal = get("/stops/" + stopId("Quantum Commons") + "/departures");
    assertEquals(1, terminal.size());
    for (var name : List.of("Quantum Commons", "Turing Terminal", "Campus Gate", "Hopper Hall")) {
      for (var departure : get("/stops/" + stopId(name) + "/departures")) {
        assertNotEquals(name, departure.get("destination").get("name").asString(), name);
      }
    }
    assertError(404, "/stops/999999/departures");
    assertError(400, "/stops/abc/departures");
  }

  @Test
  void schemaRejectsInvalidTimetables() throws Exception {
    var route = "(SELECT route_id FROM trip WHERE id = 101001)";
    var stop = "(SELECT id FROM route_stop WHERE route_id = " + route + " AND position = %d)";
    var otherStop =
        "(SELECT id FROM route_stop WHERE route_id = (SELECT route_id FROM trip WHERE id ="
            + " 201001) AND position = 1)";
    for (var invalid :
        List.of(
            // Same stop twice on one trip.
            "INSERT INTO stop_time VALUES (101001, "
                + route
                + ", "
                + stop.formatted(1)
                + ", '07:00', '07:00')",
            // Arrival after departure.
            "UPDATE stop_time SET arrival_time = departure_time + INTERVAL '1 minute'"
                + " WHERE trip_id = 101001",
            // Out of range.
            "UPDATE stop_time SET departure_time = '48:00' WHERE trip_id = 101001",
            // Seconds are not part of a timetable.
            "UPDATE stop_time SET departure_time = departure_time + INTERVAL '30 seconds'"
                + " WHERE trip_id = 101001",
            // A stop of another route.
            "UPDATE stop_time SET route_stop_id = "
                + otherStop
                + " WHERE trip_id = 101001"
                + " AND route_stop_id = "
                + stop.formatted(1),
            // Going back in time: first stop at 23:00, the rest stay in the morning.
            "UPDATE stop_time SET arrival_time = '23:00', departure_time = '23:00'"
                + " WHERE trip_id = 101001 AND route_stop_id = "
                + stop.formatted(1),
            // A trip without stop times.
            "INSERT INTO trip (id, route_id, source_ref) VALUES (990001, "
                + route
                + ", 'T99000100001')",
            // A trip that starts on day +1: the whole of 101001 moved past midnight.
            "UPDATE stop_time SET arrival_time = arrival_time + INTERVAL '24 hours',"
                + " departure_time = departure_time + INTERVAL '24 hours' WHERE trip_id = 101001",
            // Exactly 24:00 is already the next day.
            "UPDATE stop_time SET arrival_time = arrival_time + INTERVAL '17 hours',"
                + " departure_time = departure_time + INTERVAL '17 hours' WHERE trip_id = 101001",
            // Empty or malformed external references.
            "UPDATE trip SET source_ref = '' WHERE id = 101001",
            "UPDATE line SET source_ref = '' WHERE number = 'A'",
            "UPDATE route SET source_ref = 'L00101' WHERE id = " + route,
            // Repointing a timetabled route stop to another station.
            "UPDATE route_stop SET stop_id = (SELECT max(id) FROM station) WHERE id = "
                + stop.formatted(2),
            // The same station twice on one route.
            "INSERT INTO route_stop (route_id, stop_id, position) SELECT route_id, stop_id, 99"
                + " FROM route_stop WHERE id = "
                + stop.formatted(1),
            // Arriving at the first stop earlier than leaving it: a trip starts on departure.
            "UPDATE stop_time SET arrival_time = '00:01' WHERE trip_id = 101001 AND route_stop_id ="
                + " "
                + stop.formatted(1),
            // Leaving the last stop later than arriving: a trip ends on arrival.
            "UPDATE stop_time SET departure_time = departure_time + INTERVAL '5 minutes'"
                + " WHERE trip_id = 101001 AND route_stop_id = (SELECT route_stop_id FROM"
                + " stop_time st JOIN route_stop rs ON rs.id = st.route_stop_id WHERE"
                + " st.trip_id = 101001 ORDER BY rs.position DESC LIMIT 1)",
            // A dwell longer than an hour at a middle stop.
            "UPDATE stop_time SET arrival_time = arrival_time - INTERVAL '2 hours'"
                + " WHERE trip_id = 101001 AND route_stop_id = "
                + stop.formatted(2),
            // Only one of a station's coordinates.
            "UPDATE station SET x = NULL, y = 1 WHERE id = (SELECT min(id) FROM station)",
            // The same run twice: a copy of 101001 under another reference.
            "WITH copy AS (INSERT INTO trip (id, route_id, source_ref) VALUES (990002, "
                + route
                + ", 'T99000200001') RETURNING id, route_id) INSERT INTO stop_time SELECT"
                + " copy.id, st.route_id, st.route_stop_id, st.arrival_time, st.departure_time"
                + " FROM stop_time st, copy WHERE st.trip_id = 101001",
            // Hours between two neighbouring stops: first stop at 00:01, next one at 07:0x.
            "UPDATE stop_time SET arrival_time = '00:01', departure_time = '00:01'"
                + " WHERE trip_id = 101001 AND route_stop_id = "
                + stop.formatted(1),
            // A new stop between timetabled stops of the route.
            "INSERT INTO route_stop (route_id, stop_id, position) SELECT "
                + route
                + ", min(id), 0 FROM station WHERE id NOT IN (SELECT stop_id FROM route_stop"
                + " WHERE route_id = "
                + route
                + ")",
            // Reordering a timetabled route stop.
            "UPDATE route_stop SET position = 99 WHERE id = " + stop.formatted(2),
            // Moving a route with trips to another line.
            "WITH other AS (INSERT INTO line (number, name, color) VALUES ('Z9', 'Z', '#000000')"
                + " RETURNING id) UPDATE route SET line_id = (SELECT id FROM other) WHERE id = "
                + route,
            // A second line with an existing number.
            "INSERT INTO line (number, name, color) SELECT ' a ', name, color FROM line WHERE"
                + " number = 'A'")) {
      try (var connection = dataSource.getConnection()) {
        connection.setAutoCommit(false);
        try (var sql = connection.createStatement()) {
          sql.execute("SET CONSTRAINTS ALL IMMEDIATE");
          assertThrows(SQLException.class, () -> sql.execute(invalid), invalid);
        } finally {
          connection.rollback();
        }
      }
    }
    assertEquals(100, count("SELECT count(*) FROM stop_time"));
  }

  @Test
  void aTripCheckedOnceIsCheckedAgainLaterInTheSameTransaction() throws Exception {
    var first =
        "(SELECT st.route_stop_id FROM stop_time st JOIN route_stop rs ON rs.id = st.route_stop_id"
            + " WHERE st.trip_id = 101001 ORDER BY rs.position LIMIT 1)";
    try (var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (var sql = connection.createStatement()) {
        sql.execute("SET CONSTRAINTS ALL IMMEDIATE");
        // A valid change checks trip 101001 once ...
        sql.execute(
            "UPDATE stop_time SET arrival_time = arrival_time, departure_time = departure_time"
                + " WHERE trip_id = 101001");
        // ... and a later invalid change to the same trip is still rejected.
        assertThrows(
            SQLException.class,
            () ->
                sql.execute(
                    "UPDATE stop_time SET arrival_time = '23:00', departure_time = '23:00'"
                        + " WHERE trip_id = 101001 AND route_stop_id = "
                        + first));
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void deletingAStopRemovesItsCallsAndTripsLeftWithoutATimetable() throws Exception {
    // Phase-3 DELETE /stops/{id} keeps working on timetabled stops (it answers 204).
    try (var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (var sql = connection.createStatement()) {
        sql.execute("DELETE FROM station WHERE name = 'Kernel Hub'");
        sql.execute("SET CONSTRAINTS ALL IMMEDIATE");
        try (var result =
            sql.executeQuery(
                "SELECT count(*) FROM stop_time st JOIN route_stop rs ON rs.id = st.route_stop_id"
                    + " JOIN station s ON s.id = rs.stop_id WHERE s.name = 'Kernel Hub'")) {
          result.next();
          assertEquals(0, result.getLong(1));
        }
        try (var result =
            sql.executeQuery(
                "SELECT count(*) FROM trip WHERE (SELECT count(*) FROM stop_time"
                    + " WHERE trip_id = trip.id) < 2")) {
          result.next();
          assertEquals(0, result.getLong(1));
        }
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void deletingATerminalStopMakesTheNextCallTheNewEnd() throws Exception {
    // Every trip of line B starts or ends at Hopper Hall; its neighbour has a dwell on some trips.
    try (var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (var sql = connection.createStatement()) {
        sql.execute("DELETE FROM station WHERE name IN ('Hopper Hall', 'Turing Terminal')");
        sql.execute("SET CONSTRAINTS ALL IMMEDIATE");
        try (var result =
            sql.executeQuery(
                "SELECT count(*) FROM trip_summary ts JOIN stop_time st ON st.trip_id = ts.trip_id"
                    + " JOIN route_stop rs ON rs.id = st.route_stop_id"
                    + " WHERE rs.stop_id IN (ts.origin_id, ts.dest_id)"
                    + " AND st.arrival_time <> st.departure_time")) {
          result.next();
          assertEquals(0, result.getLong(1));
        }
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void errorsAreJsonWhateverTheClientAccepts() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/trips/abc"))
                  .header("Accept", "text/html")
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(400, response.statusCode(), response.body());
      assertEquals("Invalid trip ID", mapper.readTree(response.body()).get("error").asString());
    }
  }

  @Test
  void sameMinuteDeparturesKeepAStableOrder() throws Exception {
    var first = get("/stops/" + stopId("Kernel Hub") + "/departures");
    var second = get("/stops/" + stopId("Kernel Hub") + "/departures");
    assertEquals(first, second);
  }

  @Test
  void departuresAfterMidnightComeLastAndKeepTheirOrder() throws Exception {
    var byteSquare = get("/stops/" + stopId("Byte Square") + "/departures");
    var last = byteSquare.get(byteSquare.size() - 1);
    assertEquals("00:09", last.get("departure_time").asString());
    assertEquals(1, last.get("departure_day_offset").asInt());
    assertEquals(301007, last.get("trip_id").asLong());

    // Diary #8: at Syntax Square a later arrival can leave first.
    var syntax = get("/stops/" + stopId("Syntax Square") + "/departures");
    var evening =
        syntax
            .valueStream()
            .filter(d -> d.get("departure_time").asString().startsWith("20:1"))
            .toList();
    assertFalse(evening.isEmpty());
    for (var i = 1; i < evening.size(); i++) {
      assertTrue(
          evening
                  .get(i - 1)
                  .get("departure_time")
                  .asString()
                  .compareTo(evening.get(i).get("departure_time").asString())
              <= 0);
    }
  }

  @Test
  void unknownPathsAndWrongMethodsUseTheErrorBody() throws Exception {
    assertError(404, "/nope");
    try (var client = HttpClient.newHttpClient()) {
      var response =
          client.send(
              HttpRequest.newBuilder(
                      URI.create("http://localhost:" + port + "/api/v1/trips/301007"))
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(405, response.statusCode(), response.body());
      assertTrue(mapper.readTree(response.body()).hasNonNull("error"));
    }
    for (var id : List.of("0", "-1", "1.5", "99999999999999999999", "1%20", "%201", "+1", "1e3")) {
      assertError(400, "/trips/" + id);
      assertError(400, "/stops/" + id + "/departures");
      assertError(400, "/lines/" + id + "/trips");
    }
    assertEquals("Invalid line ID", error("/lines/1%20/trips"));
    assertEquals("Invalid trip ID", error("/trips/+1"));
    assertEquals("Invalid stop ID", error("/stops/%201/departures"));
    assertEquals("Trip not found", error("/trips/999999"));
    assertEquals("Line not found", error("/lines/999999/trips"));
    assertEquals("Stop not found", error("/stops/999999/departures"));
    // Phase-3 endpoints get the same strict ids.
    assertError(400, "/lines/1%20");
    assertError(400, "/stops/1%20");
  }

  @Test
  void aFixtureTripCrossingMidnightKeepsItsEveningPlace() throws Exception {
    // Copy of 101001 (line A) moved to start at 23:55, so its later calls run past midnight.
    var trip = 990101L;
    try (var connection = dataSource.getConnection();
        var sql = connection.createStatement()) {
      sql.execute(
          "WITH copy AS (INSERT INTO trip (id, route_id, source_ref) SELECT "
              + trip
              + ", route_id, 'T99010100001' FROM trip WHERE id = 101001 RETURNING id)"
              + " INSERT INTO stop_time SELECT copy.id, st.route_id, st.route_stop_id,"
              + " st.arrival_time + shift.by, st.departure_time + shift.by FROM stop_time st, copy,"
              + " (SELECT INTERVAL '23 hours 55 minutes' - min(departure_time) AS by FROM stop_time"
              + " WHERE trip_id = 101001) shift WHERE st.trip_id = 101001");
    }
    try {
      var detail = get("/trips/" + trip);
      assertEquals("23:55", detail.get("departure_time").asString());
      assertEquals(0, detail.get("departure_day_offset").asInt());
      var stops = detail.get("stops");
      var last = stops.get(stops.size() - 1);
      assertEquals(1, last.get("arrival_day_offset").asInt());
      assertTrue(last.get("arrival_time").asString().startsWith("00:"));
      var previous = -1;
      for (var stop : stops) {
        var minutes =
            stop.get("arrival_day_offset").asInt() * 1440
                + minutes(stop.get("arrival_time").asString());
        assertTrue(minutes >= previous, "after-midnight calls stay after the evening ones");
        previous = minutes;
        assertTrue(stop.get("arrival_time").asString().matches(TIME));
      }

      // At a stop the fixture leaves after midnight, it comes after every same-day departure.
      var afterMidnight =
          stops
              .valueStream()
              .filter(s -> s.get("departure_day_offset").asInt() == 1)
              .filter(s -> !s.equals(last))
              .findFirst()
              .orElseThrow();
      var departures =
          get("/stops/" + afterMidnight.get("stop").get("id").asLong() + "/departures");
      var ids = departures.valueStream().map(d -> d.get("trip_id").asLong()).toList();
      var at = ids.indexOf(trip);
      assertTrue(at >= 0);
      assertEquals(1, departures.get(at).get("departure_day_offset").asInt());
      for (var i = 0; i < departures.size(); i++) {
        if (departures.get(i).get("departure_day_offset").asInt() == 0) assertTrue(i < at);
      }

      // On the line's list it starts last, at 23:55 on day 0, not first at 00:xx.
      var lineTrips = get("/lines/" + detail.get("line").get("id").asLong() + "/trips");
      assertEquals(trip, lineTrips.get(lineTrips.size() - 1).get("id").asLong());
    } finally {
      try (var connection = dataSource.getConnection();
          var sql = connection.createStatement()) {
        sql.execute("DELETE FROM trip WHERE id = " + trip);
      }
    }
  }

  private String error(String path) throws Exception {
    return mapper.readTree(send(path).body()).get("error").asString();
  }

  @Test
  void tripStartsAreChronological() throws Exception {
    var previous = -1;
    for (var trip : get("/lines/3/trips")) {
      var minutes =
          trip.get("departure_day_offset").asInt() * 1440
              + minutes(trip.get("departure_time").asString());
      assertTrue(minutes >= previous);
      previous = minutes;
    }
  }

  private int duration(JsonNode stops) {
    var first = stops.get(0);
    var last = stops.get(stops.size() - 1);
    return last.get("arrival_day_offset").asInt() * 1440
        + minutes(last.get("arrival_time").asString())
        - first.get("departure_day_offset").asInt() * 1440
        - minutes(first.get("departure_time").asString());
  }

  private static int minutes(String time) {
    var parts = time.split(":");
    return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
  }

  private long stopId(String name) throws Exception {
    return get("/stops")
        .valueStream()
        .filter(stop -> stop.get("name").asString().equals(name))
        .findFirst()
        .orElseThrow()
        .get("id")
        .asLong();
  }

  private long count(String sql) throws SQLException {
    try (var connection = dataSource.getConnection();
        var result = connection.createStatement().executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  private JsonNode get(String path) throws Exception {
    var response = send(path);
    assertEquals(200, response.statusCode(), response.body());
    return mapper.readTree(response.body());
  }

  private void assertError(int status, String path) throws Exception {
    var response = send(path);
    assertEquals(status, response.statusCode(), path + ": " + response.body());
    var body = mapper.readTree(response.body());
    assertTrue(body.hasNonNull("error"));
    assertEquals(1, body.size(), "the contract's Error body has only `error`: " + body);
  }

  private HttpResponse<String> send(String path) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }

  private static Set<String> keys(JsonNode node) {
    return new HashSet<>(node.propertyNames());
  }
}
