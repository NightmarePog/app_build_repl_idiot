package cloud.twoidiotz.app.shared.error;

import cloud.twoidiotz.app.v1.domain.line.LineNotFoundException;
import cloud.twoidiotz.app.v1.domain.station.StationNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Fallback for every controller: shared not-found errors, unknown paths and wrong methods all
 * answer with the contract's {error} body. Controller-scoped advices run first.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {
  @ExceptionHandler(LineNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiError lineNotFound() {
    return new ApiError("Line not found");
  }

  @ExceptionHandler(StationNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiError stopNotFound() {
    return new ApiError("Stop not found");
  }

  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiError noResource() {
    return new ApiError("Not found");
  }

  // E.g. a route_stop change that would break a timetable (route_stop_keep_timetable).
  @ExceptionHandler(DataIntegrityViolationException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiError conflict() {
    return new ApiError("Conflicts with existing data");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
  public ApiError methodNotAllowed() {
    return new ApiError("Method not allowed");
  }
}
