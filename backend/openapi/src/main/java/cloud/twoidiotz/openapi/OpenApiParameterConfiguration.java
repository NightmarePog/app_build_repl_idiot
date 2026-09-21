package cloud.twoidiotz.openapi;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.HandlerMethod;

/** Documents numeric resource IDs while Springdoc infers their type and validation constraints. */
@Configuration
public class OpenApiParameterConfiguration {
  @Bean
  OperationCustomizer resourceParameters() {
    return (operation, handler) -> {
      ResourceNames.resource(handler).ifPresent(ignored -> documentId(operation, handler));
      return operation;
    };
  }

  private static void documentId(Operation operation, HandlerMethod handler) {
    Arrays.stream(handler.getMethodParameters())
        .filter(ResourceErrorRules::isId)
        .forEach(
            methodParameter ->
                Optional.ofNullable(operation.getParameters()).orElseGet(List::of).stream()
                    .filter(
                        parameter ->
                            "path".equals(parameter.getIn()) && "id".equals(parameter.getName()))
                    .forEach(parameter -> applyDefaults(parameter, methodParameter, handler)));
  }

  private static void applyDefaults(
      Parameter parameter, MethodParameter methodParameter, HandlerMethod handler) {
    Optional.ofNullable(parameter.getSchema())
        .filter(OpenApiParameterConfiguration::isInteger)
        .ifPresent(
            schema -> {
              parameter.setDescription(
                  Optional.ofNullable(parameter.getDescription())
                      .filter(value -> !value.isBlank())
                      .orElseGet(() -> ResourceNames.singular(handler, "") + " ID"));
              // Keep the public integer contract; explicit schema annotations retain their format.
              Optional.of(methodParameter)
                  .filter(
                      value ->
                          !value.hasParameterAnnotation(
                              io.swagger.v3.oas.annotations.Parameter.class))
                  .filter(
                      value ->
                          !value.hasParameterAnnotation(
                              io.swagger.v3.oas.annotations.media.Schema.class))
                  .ifPresent(ignored -> schema.setFormat(null));
              Optional.of(parameter)
                  .filter(
                      value ->
                          value.getExample() == null
                              && value.getExamples() == null
                              && schema.getExample() == null)
                  .ifPresent(value -> value.setExample(example(schema)));
            });
  }

  private static boolean isInteger(Schema<?> schema) {
    return "integer".equals(schema.getType())
        || Optional.ofNullable(schema.getTypes())
            .map(types -> types.contains("integer"))
            .orElse(false);
  }

  private static BigDecimal example(Schema<?> schema) {
    var lowerBound = Optional.ofNullable(schema.getMinimum()).orElse(BigDecimal.ONE);
    var candidate = BigDecimal.valueOf(12).max(lowerBound);
    return Optional.ofNullable(schema.getMaximum()).map(candidate::min).orElse(candidate);
  }
}
