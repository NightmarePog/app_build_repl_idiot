package cloud.twoidiotz.app;

import static org.junit.jupiter.api.Assertions.*;

import cloud.twoidiotz.app.v1.domain.line.LineRepository;
import jakarta.persistence.EntityManagerFactory;
import java.net.URI;
import java.net.http.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LineApiIntegrationTest {
  @LocalServerPort int port;
  @Autowired LineRepository lines;
  @Autowired EntityManagerFactory entityManagerFactory;

  @Value("${app.admin-api-key}")
  String adminKey;

  private final JsonMapper mapper = JsonMapper.builder().build();
  private static final String INPUT =
      """
      {"number":"42","name":"Campus Express","type":"bus","color":"#4F46E5"}
      """;

  @Test
  void lineReadsDoNotQueryPerRouteOrStop() throws Exception {
    var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    for (var path : List.of("/lines/1", "/stops/9/lines", "/lines")) {
      stats.clear();
      var response = request("GET", path, null, false);
      assertEquals(200, response.statusCode(), path);
      // Line, routes (one batch), route stops with stations: a fixed handful, not one per row.
      assertTrue(
          stats.getPrepareStatementCount() <= 4,
          path + " ran " + stats.getPrepareStatementCount() + " statements");
    }
  }

  @Test
  void createsReadsReplacesAndDeletesLines() throws Exception {
    var created = request("POST", "/lines", INPUT, true);
    assertEquals(201, created.statusCode(), created.body());
    var body = mapper.readTree(created.body());
    var id = body.get("id").asLong();
    try {
      assertTrue(id >= 1);
      assertEquals(Set.of("id", "number", "name", "type", "color"), keys(body));
      assertEquals("42", body.get("number").asString());
      assertEquals("#4F46E5", body.get("color").asString());

      var detail = mapper.readTree(request("GET", "/lines/" + id, null, false).body());
      assertTrue(detail.get("routes").isArray());
      assertEquals(0, detail.get("routes").size());

      var listed = mapper.readTree(request("GET", "/lines", null, false).body());
      assertTrue(ids(listed).contains(id));

      var replaced =
          request(
              "PUT",
              "/lines/" + id,
              """
              {"number":"X1","name":"Night","type":"replacement","color":"#000000"}
              """,
              true);
      assertEquals(200, replaced.statusCode(), replaced.body());
      assertEquals("X1", mapper.readTree(replaced.body()).get("number").asString());
      assertEquals("replacement", lines.findById(id).orElseThrow().getType());

      var deleted = request("DELETE", "/lines/" + id, null, true);
      assertEquals(204, deleted.statusCode());
      assertTrue(deleted.body().isEmpty());
      assertError(404, request("GET", "/lines/" + id, null, false));
    } finally {
      lines.findById(id).ifPresent(lines::delete);
    }
  }

  @Test
  void generatesDistinctIdsAndIgnoresClientIds() throws Exception {
    var first = mapper.readTree(request("POST", "/lines", INPUT, true).body()).get("id").asLong();
    var second =
        mapper
            .readTree(request("POST", "/lines", INPUT.replace("\"42\"", "\"43\""), true).body())
            .get("id")
            .asLong();
    try {
      assertNotEquals(first, second);
      assertError(
          400,
          request(
              "POST",
              "/lines",
              """
              {"id":1,"number":"42","name":"Campus Express","type":"bus","color":"#4F46E5"}
              """,
              true));
    } finally {
      lines.deleteAllById(List.of(first, second));
    }
  }

  @Test
  void rejectsInvalidInputAndIds() throws Exception {
    var count = lines.count();
    for (var input :
        List.of(
            "{",
            "{}",
            "[]",
            "{\"number\":\"42\",\"name\":\"N\",\"type\":\"bus\"}",
            "{\"number\":\"\",\"name\":\"N\",\"type\":\"bus\",\"color\":\"#4F46E5\"}",
            "{\"number\":\""
                + "1".repeat(21)
                + "\",\"name\":\"N\",\"type\":\"bus\",\"color\":\"#4F46E5\"}",
            "{\"number\":\"42\",\"name\":\""
                + "n".repeat(256)
                + "\",\"type\":\"bus\",\"color\":\"#4F46E5\"}",
            "{\"number\":\"42\",\"name\":\"N\",\"type\":\""
                + "t".repeat(51)
                + "\",\"color\":\"#4F46E5\"}",
            "{\"number\":\"42\",\"name\":\"N\",\"type\":\"bus\",\"color\":\"red\"}",
            "{\"number\":\"42\",\"name\":\"N\",\"type\":\"bus\",\"color\":\"#4F46E5FF\"}",
            "{\"number\":42,\"name\":\"N\",\"type\":\"bus\",\"color\":\"#4F46E5\"}",
            "{\"number\":\"42\",\"name\":\"N\",\"type\":\"bus\",\"color\":\"#4F46E5\",\"extra\":1}")) {
      assertError(400, request("POST", "/lines", input, true));
      assertError(400, request("PUT", "/lines/1", input, true));
    }
    assertEquals(count, lines.count());
    for (var id : List.of("0", "-1", "abc", "1.5")) {
      assertError(400, request("GET", "/lines/" + id, null, false));
      assertError(400, request("PUT", "/lines/" + id, INPUT, true));
      assertError(400, request("DELETE", "/lines/" + id, null, true));
    }
    assertError(404, request("GET", "/lines/999999", null, false));
    assertError(404, request("PUT", "/lines/999999", INPUT, true));
    assertError(404, request("DELETE", "/lines/999999", null, true));
  }

  @Test
  void mutationsNeedTheAdminKey() throws Exception {
    var count = lines.count();
    assertError(401, request("POST", "/lines", INPUT, false));
    assertError(401, request("PUT", "/lines/1", INPUT, false));
    assertError(401, request("DELETE", "/lines/1", null, false));
    assertEquals(count, lines.count());
  }

  @Test
  void seedsTheNetworkWithBothDirectionsInOrder() throws Exception {
    var listed = mapper.readTree(request("GET", "/lines", null, false).body());
    var c = find(listed, "C");
    var detail =
        mapper.readTree(request("GET", "/lines/" + c.get("id").asLong(), null, false).body());
    var routes = detail.get("routes");
    assertEquals(2, routes.size());
    var outbound = routes.get(0);
    assertEquals("outbound", outbound.get("bound").asString());
    assertEquals("Quantum Commons", outbound.get("direction").asString());
    assertEquals(
        List.of(
            "Edison East",
            "Notebook Quay",
            "Kernel Hub",
            "Library Loop",
            "Byte Square",
            "Quantum Commons"),
        names(outbound.get("stops")));
    var inbound = routes.get(1);
    assertEquals("inbound", inbound.get("bound").asString());
    assertEquals("Edison East", inbound.get("direction").asString());
    assertEquals(names(outbound.get("stops")).reversed(), names(inbound.get("stops")));
    assertEquals(1, inbound.get("stops").get(0).get("position").asInt());

    var kernelHub = outbound.get("stops").get(2).get("id").asLong();
    var serving =
        mapper.readTree(request("GET", "/stops/" + kernelHub + "/lines", null, false).body());
    assertEquals(
        Set.of("B", "C"),
        new HashSet<>(serving.valueStream().map(line -> line.get("number").asString()).toList()));
    assertError(404, request("GET", "/stops/999999/lines", null, false));
    assertError(400, request("GET", "/stops/abc/lines", null, false));
  }

  private static JsonNode find(JsonNode lines, String number) {
    return lines
        .valueStream()
        .filter(line -> line.get("number").asString().equals(number))
        .findFirst()
        .orElseThrow();
  }

  private static List<String> names(JsonNode stops) {
    var result = new ArrayList<String>();
    stops.forEach(stop -> result.add(stop.get("name").asString()));
    return result;
  }

  private static Set<Long> ids(JsonNode lines) {
    var result = new HashSet<Long>();
    lines.forEach(line -> result.add(line.get("id").asLong()));
    return result;
  }

  private static Set<String> keys(JsonNode node) {
    return new HashSet<>(node.propertyNames());
  }

  private void assertError(int status, HttpResponse<String> response) throws Exception {
    assertEquals(status, response.statusCode(), response.body());
    var body = mapper.readTree(response.body());
    assertEquals(Set.of("error"), keys(body));
    assertFalse(body.get("error").asString().isEmpty());
  }

  private HttpResponse<String> request(String method, String path, String body, boolean admin)
      throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path));
    if (body != null) builder.header("Content-Type", "application/json");
    if (admin) builder.header("Authorization", "Bearer " + adminKey);
    return HttpClient.newHttpClient()
        .send(
            builder
                .method(
                    method,
                    body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
  }
}
