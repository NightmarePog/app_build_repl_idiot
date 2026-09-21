package cloud.twoidiotz.openapi;

import static org.junit.jupiter.api.Assertions.*;

import cloud.twoidiotz.app.shared.api.ApiGet;
import cloud.twoidiotz.app.shared.api.ApiResource;
import cloud.twoidiotz.app.v1.domain.station.StationController;
import cloud.twoidiotz.app.v1.domain.station.StationExceptionHandler;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.HandlerMethod;

class OpenApiErrorConfigurationTest {
  @Test
  void documentsApplicableRuntimeErrorsForResourceOperations() throws Exception {
    for (var method : new String[] {"get", "update", "delete"}) {
      var responses =
          customize(new StationController(null), method, new Operation(), true).getResponses();
      assertEquals("Stop not found", responses.get("404").getDescription());
      assertEquals("Invalid stop ID or input data", responses.get("400").getDescription());
      assertEquals(
          "#/components/schemas/Error",
          responses.get("404").getContent().get("application/json").getSchema().get$ref());
    }
  }

  @Test
  void doesNotAdvertiseNotFoundForCreateOrErrorsForList() throws Exception {
    var create =
        customize(new StationController(null), "create", new Operation(), true).getResponses();
    assertTrue(create.containsKey("400"));
    assertFalse(create.containsKey("404"));
    var list = new Operation().responses(new ApiResponses());
    customize(new StationController(null), "list", list, true);
    assertTrue(list.getResponses().isEmpty());
  }

  @Test
  void respectsAdviceControllerScope() throws Exception {
    var operation = new Operation().responses(new ApiResponses());
    customize(new OtherController(), "get", operation, true);
    assertTrue(operation.getResponses().isEmpty());
  }

  @Test
  void doesNotInventResponsesWithoutRuntimeAdvice() throws Exception {
    var operation = new Operation().responses(new ApiResponses());
    customize(new StationController(null), "delete", operation, false);
    assertTrue(operation.getResponses().isEmpty());
  }

  @Test
  void preservesExplicitResponses() throws Exception {
    var explicit = new ApiResponse().description("Custom missing stop response");
    var success = new ApiResponse().description("OK");
    var operation =
        new Operation()
            .responses(
                new ApiResponses().addApiResponse("404", explicit).addApiResponse("200", success));
    customize(new StationController(null), "get", operation, true);
    assertSame(explicit, operation.getResponses().get("404"));
    assertSame(success, operation.getResponses().get("200"));
    assertTrue(operation.getResponses().containsKey("400"));
  }

  private Operation customize(
      Object controller, String method, Operation operation, boolean withAdvice) throws Exception {
    try (var context = new GenericApplicationContext()) {
      if (withAdvice) {
        context.registerBean(StationExceptionHandler.class);
      }
      context.refresh();
      var reflected =
          Arrays.stream(controller.getClass().getMethods())
              .filter(value -> value.getName().equals(method))
              .findFirst()
              .orElseThrow();
      return new OpenApiErrorConfiguration()
          .errorResponses(context)
          .customize(operation, new HandlerMethod(controller, reflected));
    }
  }

  @ApiResource
  static class OtherController {
    @ApiGet("/other/{id}")
    public void get(@PathVariable long id) {}
  }
}
