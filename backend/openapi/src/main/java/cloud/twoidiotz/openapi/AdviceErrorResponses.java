package cloud.twoidiotz.openapi;

import cloud.twoidiotz.app.shared.error.ApiError;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.ControllerAdviceBean;

/** Reads status codes from advice methods that return the shared runtime error body. */
record AdviceErrorResponses(ControllerAdviceBean advice, Set<Integer> statuses) {
  static List<AdviceErrorResponses> discover(ApplicationContext context) {
    return ControllerAdviceBean.findAnnotatedBeans(context).stream()
        .filter(advice -> advice.getBeanType() != null)
        .map(advice -> new AdviceErrorResponses(advice, statuses(advice.getBeanType())))
        .toList();
  }

  boolean supports(Class<?> controller, int status) {
    return advice.isApplicableToBeanType(controller) && statuses.contains(status);
  }

  private static Set<Integer> statuses(Class<?> adviceType) {
    return Arrays.stream(ReflectionUtils.getUniqueDeclaredMethods(adviceType))
        .filter(method -> method.getReturnType() == ApiError.class)
        .filter(method -> AnnotatedElementUtils.hasAnnotation(method, ExceptionHandler.class))
        .map(method -> AnnotatedElementUtils.findMergedAnnotation(method, ResponseStatus.class))
        .filter(java.util.Objects::nonNull)
        .map(status -> status.code().value())
        .collect(Collectors.toUnmodifiableSet());
  }
}
