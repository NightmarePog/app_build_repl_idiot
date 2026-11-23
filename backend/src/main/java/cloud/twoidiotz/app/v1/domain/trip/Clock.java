package cloud.twoidiotz.app.v1.domain.trip;

/**
 * Turns minutes after midnight of the operating day into an HH:MM clock time and a day offset
 * counted from the day on which the trip starts.
 */
final class Clock {
  static final String PATTERN = "^([01][0-9]|2[0-3]):[0-5][0-9]$";
  static final int DAY = 24 * 60;

  private Clock() {}

  static String time(int minutes) {
    var clock = Math.floorMod(minutes, DAY);
    return "%02d:%02d".formatted(clock / 60, clock % 60);
  }

  static int dayOffset(int minutes, int tripStart) {
    return Math.floorDiv(minutes, DAY) - Math.floorDiv(tripStart, DAY);
  }
}
