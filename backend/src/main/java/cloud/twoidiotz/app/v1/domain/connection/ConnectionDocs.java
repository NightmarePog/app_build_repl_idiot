package cloud.twoidiotz.app.v1.domain.connection;

import cloud.twoidiotz.app.v1.domain.trip.Clock;

final class ConnectionDocs {
  static final String TIME = Clock.PATTERN;
  static final String OFFSET =
      "Calendar days after the search day (-1, 0, 1 or 2). A connection found for 23:50 that"
          + " arrives at 00:14 has arrival offset 1; an arrival search for 00:20 may start the"
          + " evening before, offset -1.";

  private ConnectionDocs() {}
}
