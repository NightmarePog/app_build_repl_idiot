package cloud.twoidiotz.openapi;

import cloud.twoidiotz.app.shared.api.*;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Optional;
import java.util.stream.Stream;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;

/** Supplies overridable CRUD documentation for controllers annotated with ApiResource. */
@Configuration
public class OpenApiOperationConfiguration {
  @Bean
  OperationCustomizer resourceOperations() {
    return (operation, handler) -> {
      ResourceNames.resource(handler)
          .flatMap(ignored -> documentation(handler))
          .ifPresent(defaults -> applyDefaults(operation, handler, defaults));
      return operation;
    };
  }

  private static Optional<CrudDocumentation> documentation(HandlerMethod handler) {
    var resource = ResourceNames.singular(handler, "");
    return Optional.ofNullable(handler.getMethodAnnotation(ApiGet.class))
        .map(ignored -> getDocumentation(handler, resource))
        .or(
            () ->
                Optional.ofNullable(handler.getMethodAnnotation(ApiPost.class))
                    .map(ignored -> CrudDocumentation.create(resource)))
        .or(
            () ->
                Optional.ofNullable(handler.getMethodAnnotation(ApiPut.class))
                    .map(ignored -> CrudDocumentation.update(resource)))
        .or(
            () ->
                Optional.ofNullable(handler.getMethodAnnotation(ApiPatch.class))
                    .map(ignored -> CrudDocumentation.patch(resource)))
        .or(
            () ->
                Optional.ofNullable(handler.getMethodAnnotation(ApiDelete.class))
                    .map(ignored -> CrudDocumentation.delete(resource)));
  }

  private static CrudDocumentation getDocumentation(HandlerMethod handler, String resource) {
    return Optional.of(responseType(handler))
        .filter(type -> type.isArray() || Iterable.class.isAssignableFrom(type))
        .map(ignored -> CrudDocumentation.list(ResourceNames.plural(handler)))
        .orElseGet(() -> CrudDocumentation.get(resource));
  }

  private static Class<?> responseType(HandlerMethod handler) {
    var type = ResolvableType.forMethodReturnType(handler.getMethod(), handler.getBeanType());
    return Optional.of(type)
        .filter(value -> ResponseEntity.class.isAssignableFrom(value.resolve(Object.class)))
        .map(value -> value.as(ResponseEntity.class).getGeneric(0))
        .orElse(type)
        .resolve(Object.class);
  }

  private static void applyDefaults(
      io.swagger.v3.oas.models.Operation operation,
      HandlerMethod handler,
      CrudDocumentation defaults) {
    // Springdoc already assigns a method-based ID; only an explicit annotation overrides our ID.
    operation.setOperationId(
        Optional.ofNullable(handler.getMethodAnnotation(Operation.class))
            .map(Operation::operationId)
            .filter(value -> !value.isBlank())
            .orElse(defaults.operationId()));
    operation.setSummary(nonBlank(operation.getSummary()).orElse(defaults.summary()));
    Optional.ofNullable(operation.getResponses())
        .map(responses -> responses.get(defaults.status()))
        .filter(response -> isDefaultDescription(response.getDescription()))
        .ifPresent(response -> response.setDescription(defaults.response()));
  }

  private static boolean isDefaultDescription(String description) {
    return nonBlank(description)
        .map(value -> Stream.of("OK", "Created", "No Content").anyMatch(value::equals))
        .orElse(true);
  }

  private static Optional<String> nonBlank(String value) {
    return Optional.ofNullable(value).filter(text -> !text.isBlank());
  }
}
