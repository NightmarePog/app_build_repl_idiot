package cloud.twoidiotz.app.v1.domain.line;

import cloud.twoidiotz.app.shared.error.ApiError;
import cloud.twoidiotz.app.v1.domain.station.StationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = LineController.class)
public class LineExceptionHandler {
  @ExceptionHandler(LineNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiError notFound() {
    return new ApiError("Line not found");
  }

  @ExceptionHandler(StationNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiError stopNotFound() {
    return new ApiError("Stop not found");
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError invalidInput() {
    return new ApiError("Invalid request data");
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    HandlerMethodValidationException.class
  })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError invalidId() {
    return new ApiError("Invalid line ID");
  }
}
