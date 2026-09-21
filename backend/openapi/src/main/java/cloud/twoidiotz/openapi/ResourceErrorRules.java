package cloud.twoidiotz.openapi;

import jakarta.validation.Constraint;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

/**
 * CRUD convention: input can fail with 400; reading, updating or deleting an ID can fail with 404.
 */
final class ResourceErrorRules {
  private ResourceErrorRules() {}

  static boolean acceptsInput(HandlerMethod handler) {
    return Arrays.stream(handler.getMethodParameters())
        .anyMatch(ResourceErrorRules::canRejectInput);
  }

  static boolean looksUpResource(HandlerMethod handler) {
    return Optional.ofNullable(handler.getMethodAnnotation(RequestMapping.class))
            .map(
                mapping ->
                    Arrays.stream(mapping.method())
                        .anyMatch(
                            method ->
                                switch (method) {
                                  case GET, PUT, PATCH, DELETE -> true;
                                  default -> false;
                                }))
            .orElse(false)
        && Arrays.stream(handler.getMethodParameters()).anyMatch(ResourceErrorRules::isId);
  }

  static boolean isId(MethodParameter parameter) {
    return Optional.ofNullable(parameter.getParameterAnnotation(PathVariable.class))
        .map(
            path ->
                Stream.of(path.name(), path.value())
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .orElseGet(() -> parameter.getParameter().getName()))
        .filter("id"::equals)
        .isPresent();
  }

  private static boolean canRejectInput(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(RequestBody.class)
        || (parameter.hasParameterAnnotation(PathVariable.class)
            && parameter.getParameterType() != String.class)
        || Arrays.stream(parameter.getParameterAnnotations())
            .anyMatch(
                annotation ->
                    AnnotatedElementUtils.hasAnnotation(
                        annotation.annotationType(), Constraint.class));
  }
}
