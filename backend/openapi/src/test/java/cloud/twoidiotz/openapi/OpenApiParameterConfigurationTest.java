package cloud.twoidiotz.openapi;

import static org.junit.jupiter.api.Assertions.*;

import cloud.twoidiotz.app.shared.api.ApiResource;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.HandlerMethod;

class OpenApiParameterConfigurationTest {
  @Test
  void documentsInferredAndExplicitlyNamedIdParameters() throws Exception {
    for (var method : new String[] {"get", "alias"}) {
      var parameter = id();
      customize(method, parameter);
      assertEquals("Stop ID", parameter.getDescription());
      assertEquals(BigDecimal.valueOf(12), parameter.getExample());
      assertEquals(BigDecimal.ONE, parameter.getSchema().getMinimum());
      assertNull(parameter.getSchema().getFormat());
    }
  }

  @Test
  void preservesExplicitDocumentation() throws Exception {
    var parameter = id().description("Custom ID").example(42);
    customize("custom", parameter);
    assertEquals("Custom ID", parameter.getDescription());
    assertEquals(42, parameter.getExample());
    assertEquals("int64", parameter.getSchema().getFormat());
  }

  @Test
  void respectsValidationBoundsWhenChoosingExample() throws Exception {
    var parameter = id();
    parameter.getSchema().setMinimum(BigDecimal.valueOf(20));
    customize("get", parameter);
    assertEquals(BigDecimal.valueOf(20), parameter.getExample());
    var bounded = id();
    bounded.getSchema().setMaximum(BigDecimal.TEN);
    customize("get", bounded);
    assertEquals(BigDecimal.TEN, bounded.getExample());
  }

  @Test
  void leavesOtherParameterKindsAlone() throws Exception {
    for (var parameter :
        new Parameter[] {
          id().name("parentId"), id().in("query"), id().schema(new StringSchema())
        }) {
      customize("get", parameter);
      assertNull(parameter.getDescription());
      assertNull(parameter.getExample());
    }
  }

  private Parameter id() {
    return new Parameter()
        .name("id")
        .in("path")
        .schema(new IntegerSchema().format("int64").minimum(BigDecimal.ONE));
  }

  private void customize(String method, Parameter parameter) throws Exception {
    var handler =
        new HandlerMethod(
            new StationController(), StationController.class.getMethod(method, long.class));
    new OpenApiParameterConfiguration()
        .resourceParameters()
        .customize(new Operation().addParametersItem(parameter), handler);
  }

  @ApiResource("Stop")
  static class StationController {
    public void get(@PathVariable @Min(1) long id) {}

    public void alias(@PathVariable("id") long stationId) {}

    public void custom(
        @PathVariable @io.swagger.v3.oas.annotations.Parameter(description = "Custom ID")
            long id) {}
  }
}
