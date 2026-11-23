package cloud.twoidiotz.app.v1.domain.connection;

/** A connection search parameter is missing or malformed; the message is the API error text. */
class InvalidConnectionQueryException extends RuntimeException {
  InvalidConnectionQueryException(String message) {
    super(message);
  }
}
