package cloud.twoidiotz.app.v1.domain.connection;

import cloud.twoidiotz.app.shared.error.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Query errors; "Stop not found" comes from the shared advice. */
@RestControllerAdvice(assignableTypes = ConnectionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConnectionExceptionHandler {
  @ExceptionHandler(InvalidConnectionQueryException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError invalid(InvalidConnectionQueryException exception) {
    return new ApiError(exception.getMessage());
  }
}
