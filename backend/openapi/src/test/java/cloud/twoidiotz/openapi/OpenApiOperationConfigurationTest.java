package cloud.twoidiotz.openapi;

import static org.junit.jupiter.api.Assertions.*;

import cloud.twoidiotz.app.shared.api.*;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;

class OpenApiOperationConfigurationTest {
  @Test
  void distinguishesCollectionAndDetailResponses() throws Exception {
    var list = customize(new StationController(), "list", operation("200", "OK"));
    assertEquals("getStops", list.getOperationId());
    assertEquals("List all stops", list.getSummary());
    assertEquals("List of stops", list.getResponses().get("200").getDescription());
    var get = customize(new StationController(), "get", operation("200", "OK"));
    assertEquals("getStop", get.getOperationId());
    assertEquals("Get stop detail", get.getSummary());
    assertEquals("Stop detail", get.getResponses().get("200").getDescription());
  }

  @Test
  void documentsEachMutationWithoutChangingSuccessResponses() throws Exception {
    for (var method : List.of("create", "update", "patch", "delete")) {
      var status =
          switch (method) {
            case "create" -> "201";
            case "delete" -> "204";
            default -> "200";
          };
      var operation = operation(status, "OK");
      var response = operation.getResponses().get(status);
      var result = customize(new StationController(), method, operation);
      assertEquals(method + "Stop", result.getOperationId());
      assertSame(response, result.getResponses().get(status));
      assertEquals(1, result.getResponses().size());
      assertTrue(response.getDescription().startsWith("Stop "));
      assertTrue(response.getDescription().endsWith(" successfully"));
    }
  }

  @Test
  void preservesExplicitDocumentation() throws Exception {
    var operation =
        operation("200", "Custom response").summary("Custom summary").description("Custom details");
    var result = customize(new StationController(), "custom", operation);
    assertEquals("findPlatform", result.getOperationId());
    assertEquals("Custom summary", result.getSummary());
    assertEquals("Custom details", result.getDescription());
    assertEquals("Custom response", result.getResponses().get("200").getDescription());
  }

  @Test
  void infersClassNameAndSupportsIrregularPlurals() throws Exception {
    var result = customize(new PersonController(), "list", operation("200", "OK"));
    assertEquals("getPeople", result.getOperationId());
    assertEquals("List all people", result.getSummary());
  }

  @Test
  void leavesUnmarkedControllersAlone() throws Exception {
    var operation = operation("200", "OK").operationId("existing");
    assertSame(operation, customize(new HealthController(), "get", operation));
    assertEquals("existing", operation.getOperationId());
    assertNull(operation.getSummary());
    assertEquals("OK", operation.getResponses().get("200").getDescription());
  }

  private Operation operation(String status, String description) {
    return new Operation()
        .responses(
            new ApiResponses().addApiResponse(status, new ApiResponse().description(description)));
  }

  private Operation customize(Object controller, String method, Operation operation)
      throws Exception {
    return new OpenApiOperationConfiguration()
        .resourceOperations()
        .customize(
            operation, new HandlerMethod(controller, controller.getClass().getMethod(method)));
  }

  @ApiResource("Stop")
  static class StationController {
    @ApiGet("/stops")
    public ResponseEntity<List<String>> list() {
      return null;
    }

    @ApiGet("/stops/{id}")
    public String get() {
      return null;
    }

    @ApiPost("/stops")
    public void create() {}

    @ApiPut("/stops/{id}")
    public void update() {}

    @ApiPatch("/stops/{id}")
    public void patch() {}

    @ApiDelete("/stops/{id}")
    public void delete() {}

    @ApiGet(value = "/custom", operationId = "findPlatform")
    public void custom() {}
  }

  @ApiResource(plural = "People")
  static class PersonController {
    @ApiGet("/people")
    public String[] list() {
      return null;
    }
  }

  static class HealthController {
    @ApiGet("/health")
    public void get() {}
  }
}
