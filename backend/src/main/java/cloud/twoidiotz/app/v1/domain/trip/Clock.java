package cloud.twoidiotz.app.v1.domain.trip;

/**
 * Turns minutes after midnight of the operating day into an HH:MM clock time and a day offset
 * counted from the day on which the trip starts. The schema keeps every trip start before 24:00, so
 * the offset is the number of whole days after the operating day's midnight.
 */
public final class Clock {
  public static final String PATTERN = "^([01][0-9]|2[0-3]):[0-5][0-9]$";
  public static final int DAY = 24 * 60;

  private Clock() {}

  public static String time(int minutes) {
    var clock = Math.floorMod(minutes, DAY);
    return "%02d:%02d".formatted(clock / 60, clock % 60);
  }

  public static int dayOffset(int minutes) {
    return Math.floorDiv(minutes, DAY);
  }
}
