package cloud.twoidiotz.openapi;

import cloud.twoidiotz.app.shared.api.ApiResource;
import java.util.Optional;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

/** Shared resource naming for operation and error documentation. */
final class ResourceNames {
  private ResourceNames() {}

  static Optional<ApiResource> resource(HandlerMethod handler) {
    return Optional.ofNullable(
        AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), ApiResource.class));
  }

  static String plural(HandlerMethod handler) {
    return resource(handler)
        .map(ApiResource::plural)
        .flatMap(ResourceNames::nonBlank)
        .orElseGet(() -> singular(handler, "") + "s");
  }

  static String singular(HandlerMethod handler, String override) {
    return nonBlank(override)
        .or(() -> declaredResourceName(handler))
        .orElseGet(() -> controllerResourceName(handler.getBeanType().getSimpleName()));
  }

  private static Optional<String> declaredResourceName(HandlerMethod handler) {
    return Optional.ofNullable(
            AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), ApiResource.class))
        .map(ApiResource::value)
        .flatMap(ResourceNames::nonBlank);
  }

  private static Optional<String> nonBlank(String value) {
    return Optional.ofNullable(value).filter(text -> !text.isBlank());
  }

  private static String controllerResourceName(String name) {
    return Optional.of(name)
        .filter(value -> value.endsWith("Controller"))
        .map(value -> value.substring(0, value.length() - "Controller".length()))
        .orElse(name);
  }
}
