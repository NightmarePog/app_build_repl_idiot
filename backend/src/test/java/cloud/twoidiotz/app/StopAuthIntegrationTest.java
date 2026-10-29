package cloud.twoidiotz.app;

import static org.junit.jupiter.api.Assertions.*;

import cloud.twoidiotz.app.v1.domain.station.StationModel;
import cloud.twoidiotz.app.v1.domain.station.StationRepository;
import java.net.URI;
import java.net.http.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StopAuthIntegrationTest {
  @LocalServerPort int port;
  @Autowired StationRepository stations;

  @Value("${app.admin-api-key}")
  String adminKey;

  private final JsonMapper mapper = JsonMapper.builder().build();
  private static final String INPUT =
      """
      {"name":"Cejl","wheelchair_accessible":true,"has_shelter":true,"has_ticket_machine":false}
      """;

  @Test
  void rejectsMutationsWithoutTheAdminKeyBeforeTouchingTheDatabase() throws Exception {
    var station = new StationModel();
    station.setName("Protected station");
    var id = stations.saveAndFlush(station).getId();
    var count = stations.count();
    try {
      var headers =
          Arrays.asList(
              null,
              "",
              "Bearer",
              "Bearer ",
              "Bearer wrong",
              "Bearer " + adminKey + " extra",
              "Bearer " + adminKey.substring(1),
              "Basic " + adminKey,
              "Token " + adminKey,
              adminKey);
      for (var header : headers) {
        assertUnauthorized(request("POST", "/stops", INPUT, header));
        assertUnauthorized(request("PUT", "/stops/" + id, INPUT, header));
        assertUnauthorized(request("DELETE", "/stops/" + id, null, header));
        // Authorization runs first: invalid input or IDs never reach validation.
        assertUnauthorized(request("PUT", "/stops/" + id, "{", header));
        assertUnauthorized(request("PUT", "/stops/abc", INPUT, header));
        assertUnauthorized(request("DELETE", "/stops/0", null, header));
        assertUnauthorized(request("DELETE", "/stops/9223372036854775807", null, header));
      }
      assertEquals(count, stations.count());
      assertEquals("Protected station", stations.findById(id).orElseThrow().getName());
    } finally {
      stations.deleteById(id);
    }
  }

  @Test
  void readsStayPublicRegardlessOfTheHeader() throws Exception {
    for (var header : Arrays.asList(null, "Bearer wrong", "Bearer " + adminKey)) {
      var list = request("GET", "/stops", null, header);
      assertEquals(200, list.statusCode(), list.body());
      var first = mapper.readTree(list.body()).get(0).get("id").asLong();
      assertEquals(200, request("GET", "/stops/" + first, null, header).statusCode());
      assertEquals(200, request("GET", "/health", null, header).statusCode());
    }
  }

  @Test
  void acceptsTheAdminKeyAsBearerToken() throws Exception {
    for (var scheme : List.of("Bearer", "bearer", "BEARER")) {
      var created = request("POST", "/stops", INPUT, scheme + " " + adminKey);
      assertEquals(201, created.statusCode(), created.body());
      var id = mapper.readTree(created.body()).get("id").asLong();
      try {
        var updated =
            request("PUT", "/stops/" + id, INPUT.replace("Cejl", "Renamed"), "Bearer " + adminKey);
        assertEquals(200, updated.statusCode(), updated.body());
        assertEquals("Renamed", stations.findById(id).orElseThrow().getName());
        var deleted = request("DELETE", "/stops/" + id, null, "Bearer " + adminKey);
        assertEquals(204, deleted.statusCode());
        assertFalse(stations.existsById(id));
      } finally {
        stations.deleteById(id);
      }
    }
  }

  private void assertUnauthorized(HttpResponse<String> response) throws Exception {
    assertEquals(401, response.statusCode(), response.body());
    assertEquals("Bearer", response.headers().firstValue("WWW-Authenticate").orElseThrow());
    assertTrue(
        response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
    var body = mapper.readTree(response.body());
    assertEquals(Set.of("error"), new HashSet<>(body.propertyNames()));
    assertFalse(body.get("error").asString().isEmpty());
  }

  private HttpResponse<String> request(String method, String path, String body, String auth)
      throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path));
    if (body != null) builder.header("Content-Type", "application/json");
    if (auth != null) builder.header("Authorization", auth);
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
