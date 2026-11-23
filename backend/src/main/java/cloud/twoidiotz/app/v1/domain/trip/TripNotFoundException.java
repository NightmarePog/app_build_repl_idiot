package cloud.twoidiotz.app.v1.domain.trip;

public class TripNotFoundException extends RuntimeException {
  public TripNotFoundException(long id) {
    super("Trip not found: " + id);
  }
}
