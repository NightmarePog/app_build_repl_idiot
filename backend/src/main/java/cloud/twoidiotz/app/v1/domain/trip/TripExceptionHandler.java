package cloud.twoidiotz.app.v1.domain.trip;

import cloud.twoidiotz.app.shared.error.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Trip-specific errors; line and stop not-found come from the shared advice. */
@RestControllerAdvice(assignableTypes = TripController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TripExceptionHandler {
  @ExceptionHandler(TripNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiError tripNotFound() {
    return new ApiError("Trip not found");
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError mismatch(MethodArgumentTypeMismatchException exception) {
    return positive(exception.getName());
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError invalid(HandlerMethodValidationException exception) {
    var name =
        exception.getParameterValidationResults().stream()
            .map(result -> result.getMethodParameter().getParameterName())
            .findFirst()
            .orElse("");
    return positive(name);
  }

  // Same wording as the phase-3 handlers: lines say "Invalid line ID", stops "Invalid stop ID".
  // Every TripController path variable is named, so the neutral fallback is only a safety net.
  private static ApiError positive(String name) {
    return new ApiError(
        switch (name) {
          case "stopId" -> "Invalid stop ID";
          case "tripId" -> "Invalid trip ID";
          case "lineId" -> "Invalid line ID";
          default -> "Invalid ID";
        });
  }
}
