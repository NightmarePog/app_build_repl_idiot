package cloud.twoidiotz.app.v1.domain.connection;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.twoidiotz.app.v1.domain.connection.Planner.Call;
import cloud.twoidiotz.app.v1.domain.connection.Planner.Journey;
import cloud.twoidiotz.app.v1.domain.connection.Planner.Trip;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlannerTest {
  private static Trip trip(long id, long[] stops, int... minutes) {
    var calls = new ArrayList<Call>();
    for (var i = 0; i < stops.length; i++) calls.add(new Call(stops[i], minutes[i], minutes[i]));
    return new Trip(id, calls);
  }

  private static List<Long> trips(Journey journey) {
    return journey.legs().stream().map(l -> l.run().trip().id()).toList();
  }

  @Test
  void boardsLaterBusAtTransferStopInsteadOfRidingToTerminusAndBack() {
    var planner =
        new Planner(
            List.of(
                trip(10, new long[] {1, 2}, 420, 430),
                trip(20, new long[] {2, 3}, 440, 450),
                trip(30, new long[] {3, 2, 4}, 500, 510, 520)));
    var found = planner.departing(1, 4, 420, 5);
    assertThat(found).hasSize(1);
    assertThat(trips(found.getFirst())).containsExactly(10L, 30L);
    assertThat(found.getFirst().legs().getLast().departure()).isEqualTo(510);
  }

  @Test
  void dropsTransfersWithOvernightWait() {
    var planner =
        new Planner(
            List.of(trip(10, new long[] {1, 2}, 900, 910), trip(20, new long[] {2, 3}, 420, 440)));
    assertThat(planner.departing(1, 3, 420, 5)).isEmpty();
  }

  @Test
  void keepsLongSameDayTransferWhenItIsTheOnlyJourney() {
    var planner =
        new Planner(
            List.of(
                trip(10, new long[] {1, 2}, 930, 946), trip(20, new long[] {2, 3}, 1217, 1227)));
    var found = planner.departing(1, 3, 420, 5);
    assertThat(found).hasSize(1);
    assertThat(trips(found.getFirst())).containsExactly(10L, 20L);
  }

  @Test
  void prefersFewerTransfersAtEqualArrival() {
    var planner =
        new Planner(
            List.of(
                trip(10, new long[] {1, 2, 3}, 420, 430, 440),
                trip(20, new long[] {2, 3}, 432, 440)));
    var found = planner.departing(1, 3, 400, 5);
    assertThat(found).hasSize(1);
    assertThat(found.getFirst().transfers()).isZero();
  }

  @Test
  void takesOvertakingBusWhenItArrivesSooner() {
    var planner =
        new Planner(
            List.of(
                trip(10, new long[] {1, 2, 3}, 420, 425, 470),
                trip(20, new long[] {2, 3}, 430, 440)));
    var found = planner.departing(1, 3, 400, 5);
    // The faster bus wins on time, the direct one stays as the no-transfer option.
    assertThat(found).extracting(Journey::arrival).containsExactlyInAnyOrder(440, 470);
  }

  @Test
  void wrapsPastMidnightAndKeepsWholeDayWindow() {
    var planner = new Planner(List.of(trip(10, new long[] {1, 2}, 420, 430)));
    var found = planner.departing(1, 2, 430, 5);
    assertThat(found).hasSize(1);
    assertThat(found.getFirst().departure()).isEqualTo(420 + 1440);
    var late = new Planner(List.of(trip(11, new long[] {1, 2}, 1430, 1450)));
    assertThat(late.departing(1, 2, 5, 5).getFirst().arrival()).isEqualTo(1450);
  }

  @Test
  void arrivalModeListsClosestFirstWithoutDetours() {
    var planner =
        new Planner(
            List.of(
                trip(10, new long[] {1, 2, 3}, 600, 610, 620),
                trip(20, new long[] {1, 2, 3}, 700, 710, 720),
                trip(30, new long[] {2, 4}, 615, 630),
                trip(40, new long[] {4, 2, 3}, 640, 650, 660)));
    var found = planner.arriving(1, 3, 730, 1);
    assertThat(found).hasSize(1);
    assertThat(found.getFirst().arrival()).isEqualTo(720);
    assertThat(planner.arriving(1, 3, 730, 5))
        .allSatisfy(j -> assertThat(trips(j)).doesNotContain(30L, 40L));
  }

  @Test
  void neverRidesPastDestinationOrRevisitsStops() {
    var planner = new Planner(List.of(trip(10, new long[] {1, 2, 3, 2, 4}, 0, 5, 10, 15, 20)));
    var found = planner.departing(1, 2, 0, 5);
    assertThat(found.getFirst().legs().getFirst().alight()).isEqualTo(1);
    // The same-day ride to 4 loops through 2 again, so it is never offered.
    assertThat(planner.departing(1, 4, 0, 5))
        .allSatisfy(j -> assertThat(j.legs().getFirst().alight()).isEqualTo(1));
  }
}
