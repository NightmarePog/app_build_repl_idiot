package cloud.twoidiotz.app.v1.domain.line;

public class LineNotFoundException extends RuntimeException {
  public LineNotFoundException(long id) {
    super("Line not found: " + id);
  }
}
