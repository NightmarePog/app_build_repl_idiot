package cloud.twoidiotz.app;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Hilbert's diary (task-40) as journey-planner cases over the seeded timetable. */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConnectionApiIntegrationTest {
  @LocalServerPort int port;
  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void directTripOnlyInTheWantedDirection() throws Exception {
    var body = search("Ada Exchange", "Campus Gate", "06:00", "departure", 20);
    assertEquals("Ada Exchange", body.get("from").get("name").asString());
    assertEquals("departure", body.get("mode").asString());
    var connections = body.get("connections");
    assertEquals(List.of("07:05", "20:04", "22:54"), times(connections, "departure_time"));
    for (var connection : connections) {
      assertEquals(0, connection.get("transfers").asInt());
      var leg = connection.get("legs").get(0);
      assertEquals("B", leg.get("line").get("number").asString());
      assertEquals("outbound", leg.get("direction").asString());
      assertTrue(leg.get("block_ref").asString().matches("^B0[0-9]$"));
      assertEquals("Campus Gate", leg.get("destination").get("name").asString());
      assertTrue(leg.get("transfer_minutes").isNull());
    }
    var first = connections.get(0);
    assertEquals("07:21", first.get("arrival_time").asString());
    assertEquals(16, first.get("duration_minutes").asInt());
    var stops = first.get("legs").get(0).get("stops");
    assertEquals(6, stops.size());
    assertEquals("Ada Exchange", stops.get(0).get("stop").get("name").asString());
    assertEquals("07:04", stops.get(0).get("arrival_time").asString());
    assertEquals("07:05", stops.get(0).get("departure_time").asString());
    assertEquals("Campus Gate", stops.get(5).get("stop").get("name").asString());
  }

  @Test
  void showsArrivalAndDepartureWhereTheBusWaits() throws Exception {
    var connections =
        search("Syntax Square", "Campus Gate", "20:00", "departure", 1).get("connections");
    var leg = connections.get(0).get("legs").get(0);
    assertEquals(201002, leg.get("trip_id").asLong());
    assertEquals("20:15", leg.get("departure_time").asString());
    assertEquals("20:13", leg.get("stops").get(0).get("arrival_time").asString());
  }

  @Test
  void changesBusesWithAMinimumTransferTime() throws Exception {
    var body = search("Ada Exchange", "Quantum Commons", "07:00", "departure", 1);
    var connection = body.get("connections").get(0);
    assertEquals("07:05", connection.get("departure_time").asString());
    assertEquals("07:34", connection.get("arrival_time").asString());
    assertEquals(1, connection.get("transfers").asInt());
    var legs = connection.get("legs");
    assertEquals(2, legs.size());
    assertEquals("C", legs.get(1).get("line").get("number").asString());
    assertEquals(
        legs.get(0).get("to").get("id").asLong(), legs.get(1).get("from").get("id").asLong());
    assertTrue(
        legs.get(1).get("transfer_minutes").asInt() >= body.get("min_transfer_minutes").asInt());
  }

  @Test
  void lastEveningTripArrivesAfterMidnight() throws Exception {
    var connection =
        search("Edison East", "Quantum Commons", "23:00", "departure", 1).get("connections").get(0);
    assertEquals("23:52", connection.get("departure_time").asString());
    assertEquals(0, connection.get("departure_day_offset").asInt());
    assertEquals("00:14", connection.get("arrival_time").asString());
    assertEquals(1, connection.get("arrival_day_offset").asInt());
    assertEquals(22, connection.get("duration_minutes").asInt());

    var justAfterMidnight =
        search("Library Loop", "Quantum Commons", "00:03", "departure", 1)
            .get("connections")
            .get(0);
    assertEquals(301007, justAfterMidnight.get("legs").get(0).get("trip_id").asLong());
    assertEquals("00:05", justAfterMidnight.get("departure_time").asString());
    assertEquals(0, justAfterMidnight.get("departure_day_offset").asInt());

    var arriving =
        search("Edison East", "Quantum Commons", "00:20", "arrival", 1).get("connections").get(0);
    assertEquals("23:52", arriving.get("departure_time").asString());
    assertEquals(-1, arriving.get("departure_day_offset").asInt());
    assertEquals(0, arriving.get("arrival_day_offset").asInt());
  }

  @Test
  void arrivalSearchReturnsTheLatestConnectionsArrivingInTime() throws Exception {
    var body = search("Ada Exchange", "Campus Gate", "21:00", "arrival", 2);
    assertEquals(List.of("07:05", "20:04"), times(body.get("connections"), "departure_time"));
  }

  @Test
  void directionsHaveTheirOwnTimes() throws Exception {
    var there =
        search("Edison East", "Quantum Commons", "15:00", "departure", 1).get("connections").get(0);
    var back =
        search("Quantum Commons", "Edison East", "15:00", "departure", 1).get("connections").get(0);
    assertEquals(21, there.get("duration_minutes").asInt());
    assertEquals(27, back.get("duration_minutes").asInt());
  }

  @Test
  void rejectsInvalidQueries() throws Exception {
    var ada = stopId("Ada Exchange");
    assertError(400, "?to=" + ada, "Missing from stop ID");
    assertError(400, "?from=abc&to=" + ada, "Invalid from stop ID");
    assertError(400, "?from=" + ada, "Missing to stop ID");
    assertError(400, "?from=" + ada + "&to=0", "Invalid to stop ID");
    assertError(400, "?from=" + ada + "&to=" + ada, "From and to must differ");
    assertError(400, "?from=" + ada + "&to=999999&time=24:00", "Invalid time");
    assertError(400, "?from=" + ada + "&to=999999&mode=later", "Invalid mode");
    assertError(400, "?from=" + ada + "&to=999999&limit=21", "Invalid limit");
    assertError(404, "?from=" + ada + "&to=999999", "Stop not found");
  }

  private JsonNode search(String from, String to, String time, String mode, int limit)
      throws Exception {
    var response =
        send(
            "?from=%d&to=%d&time=%s&mode=%s&limit=%d"
                .formatted(stopId(from), stopId(to), time, mode, limit));
    assertEquals(200, response.statusCode(), response.body());
    return mapper.readTree(response.body());
  }

  private static List<String> times(JsonNode connections, String field) {
    return connections.valueStream().map(c -> c.get(field).asString()).toList();
  }

  private void assertError(int status, String query, String message) throws Exception {
    var response = send(query);
    assertEquals(status, response.statusCode(), query + ": " + response.body());
    var body = mapper.readTree(response.body());
    assertEquals(message, body.get("error").asString());
    assertEquals(1, body.size());
  }

  private long stopId(String name) throws Exception {
    var response = get("/stops");
    return mapper
        .readTree(response.body())
        .valueStream()
        .filter(stop -> stop.get("name").asString().equals(name))
        .findFirst()
        .orElseThrow()
        .get("id")
        .asLong();
  }

  private HttpResponse<String> send(String query) throws Exception {
    return get("/connections" + query);
  }

  private HttpResponse<String> get(String path) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }
}
