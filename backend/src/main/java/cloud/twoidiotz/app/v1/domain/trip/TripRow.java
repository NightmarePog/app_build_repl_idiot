package cloud.twoidiotz.app.v1.domain.trip;

import cloud.twoidiotz.app.v1.domain.line.RouteDirection;

/** Row records read by TripRepository; minutes count from midnight of the operating day. */
final class TripRow {
  private TripRow() {}

  record Line(long id, String number, String name, String type, String color) {}

  /** A station as stored: imageUrl may be a relative path that TripService makes absolute. */
  record Stop(long id, String name, String imageUrl) {}

  /** One trip as read from trip.sql or line-trips.sql. */
  record Trip(
      long id,
      long routeId,
      Line line,
      RouteDirection direction,
      Stop origin,
      Stop destination,
      int startMinute,
      String blockRef) {}

  /** One call of a trip as read from stops.sql. */
  record StopTime(int stopOrder, Stop stop, int arrival, int departure) {}

  /** One departure from a stop as read from departures.sql. */
  record Departure(
      long tripId,
      Line line,
      RouteDirection direction,
      Stop destination,
      int arrival,
      int departure,
      String blockRef) {}
}
