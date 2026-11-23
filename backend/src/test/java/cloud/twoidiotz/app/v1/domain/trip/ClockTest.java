package cloud.twoidiotz.app.v1.domain.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClockTest {
  @Test
  void formatsClockTimes() {
    assertEquals("00:00", Clock.time(0));
    assertEquals("23:59", Clock.time(1439));
    assertEquals("00:00", Clock.time(1440));
    assertEquals("23:59", Clock.time(2879));
    assertEquals("23:59", Clock.time(-1));
    assertEquals("23:59", Clock.time(-1441));
    assertEquals("00:14", Clock.time(24 * 60 + 14));
    assertTrue("23:59".matches(Clock.PATTERN));
    assertFalse("24:00".matches(Clock.PATTERN));
  }

  @Test
  void countsDaysFromTheTripStart() {
    assertEquals(0, Clock.dayOffset(1432));
    assertEquals(1, Clock.dayOffset(1440));
    assertEquals(1, Clock.dayOffset(2879));
    assertEquals(1, Clock.dayOffset(1450));
    assertEquals(-1, Clock.dayOffset(-1));
    // 23:59 and 24:00 sit on either side of midnight.
    assertEquals(0, Clock.dayOffset(1439));
    assertEquals(1, Clock.dayOffset(1440));
  }
}
