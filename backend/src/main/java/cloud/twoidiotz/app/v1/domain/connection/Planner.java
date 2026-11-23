package cloud.twoidiotz.app.v1.domain.connection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exhaustive journey search over a timetable that repeats every day. Each trip runs on five
 * consecutive days (minutes shifted by -2..+2 days), so a trip that starts at 23:52 and arrives at
 * 24:14 is caught both late in the evening and just after midnight, and a search can look a full
 * day ahead or back from any time.
 *
 * <p>The network is small, so every journey (capped at {@link #MAX_CANDIDATES}) with up to {@link
 * #MAX_LEGS} legs is enumerated instead of approximated. A journey never visits a stop twice (no
 * riding to the terminus and back), never rides past its destination and changes buses only with
 * {@link #TRANSFER_MINUTES} to spare; staying on the same bus needs nothing, so zero-minute dwells
 * and buses overtaking each other are handled by time alone. The result is the Pareto set: a
 * journey is dropped when another one leaves no earlier, arrives no later and needs no more
 * transfers.
 */
final class Planner {
  static final int TRANSFER_MINUTES = 2;
  static final int MAX_LEGS = 4;

  /**
   * Upper bound on candidate journeys per search. The enumeration is exponential in network size,
   * so a larger network stops collecting here instead of running away; the seeded network stays far
   * below it.
   */
  static final int MAX_CANDIDATES = 10_000;

  /** Longest wait at a transfer stop; longer waits are not a usable connection (no overnight stays). */
  static final int MAX_TRANSFER_WAIT_MINUTES = 180;

  private static final int DAY = 24 * 60;

  /** Journeys with the same times and transfers collapse to the one with the least waiting. */
  private record Key(int departure, int arrival, int transfers) {}

  record Call(long stopId, int arrival, int departure) {}

  record Trip(long id, List<Call> calls) {}

  /** One run of a trip on one day; the calls are already shifted to that day. */
  record Run(Trip trip, int shift, List<Call> calls) {}

  /** Ride on {@code run} from call index {@code board} to call index {@code alight}. */
  record Leg(Run run, int board, int alight) {
    int departure() {
      return run.calls().get(board).departure();
    }

    int arrival() {
      return run.calls().get(alight).arrival();
    }
  }

  record Journey(List<Leg> legs) {
    int departure() {
      return legs.getFirst().departure();
    }

    int arrival() {
      return legs.getLast().arrival();
    }

    int transfers() {
      return legs.size() - 1;
    }
  }

  /** A run can be boarded at call {@code index}. */
  private record Boarding(int run, int index) {}

  private final List<Run> runs = new ArrayList<>();
  private final Map<Long, List<Boarding>> boardings = new HashMap<>();

  Planner(List<Trip> trips) {
    for (var trip : trips)
      for (var day = -2; day <= 2; day++) {
        var shift = day * DAY;
        var calls =
            trip.calls().stream()
                .map(c -> new Call(c.stopId(), c.arrival() + shift, c.departure() + shift))
                .toList();
        var run = runs.size();
        runs.add(new Run(trip, shift, calls));
        for (var i = 0; i + 1 < calls.size(); i++)
          boardings
              .computeIfAbsent(calls.get(i).stopId(), id -> new ArrayList<>())
              .add(new Boarding(run, i));
      }
    for (var list : boardings.values())
      list.sort(Comparator.comparingInt(b -> runs.get(b.run()).calls().get(b.index()).departure()));
  }

  /** Up to {@code limit} journeys leaving in {@code [time, time + 1 day)}, by departure. */
  List<Journey> departing(long from, long to, int time, int limit) {
    // Look a second day ahead so that e.g. riding away at 23:06 to wait for 07:00 is pruned.
    return pareto(from, to, time, time + 2 * DAY).stream()
        .filter(j -> j.departure() < time + DAY)
        .limit(limit)
        .toList();
  }

  /**
   * Up to {@code limit} journeys arriving in {@code (time - 1 day, time]}: the ones arriving
   * closest to {@code time}, listed by departure.
   */
  List<Journey> arriving(long from, long to, int time, int limit) {
    var found =
        pareto(from, to, time - 2 * DAY, time + 1).stream()
            .filter(j -> j.arrival() <= time && j.arrival() > time - DAY)
            .sorted(Comparator.comparingInt(Journey::arrival).reversed())
            .limit(limit)
            .toList();
    return found.stream().sorted(order()).toList();
  }

  /** Every non-dominated journey whose first bus leaves in {@code [from, until)}. */
  private List<Journey> pareto(long fromStop, long toStop, int fromTime, int until) {
    var all = new ArrayList<Journey>();
    if (fromStop == toStop) return all;
    var visited = new HashSet<Long>();
    visited.add(fromStop);
    search(fromStop, toStop, fromTime, until, new ArrayList<>(), visited, all);
    var best = new HashMap<Key, Journey>();
    for (var journey : all)
      best.merge(
          new Key(journey.departure(), journey.arrival(), journey.transfers()),
          journey,
          (a, b) -> waiting(b) < waiting(a) ? b : a);
    var unique = new ArrayList<>(best.values());
    return unique.stream()
        .filter(x -> unique.stream().noneMatch(y -> y != x && dominates(y, x)))
        .sorted(order())
        .toList();
  }

  private void search(
      long stop,
      long target,
      int ready,
      int until,
      List<Leg> legs,
      Set<Long> visited,
      List<Journey> out) {
    for (var boarding : boardings.getOrDefault(stop, List.of())) {
      if (out.size() >= MAX_CANDIDATES) return;
      var run = runs.get(boarding.run());
      var calls = run.calls();
      var departure = calls.get(boarding.index()).departure();
      if (departure < ready) continue;
      if (legs.isEmpty() && departure >= until) break;
      if (!legs.isEmpty()
          && (departure >= until + DAY
              || departure - (ready - TRANSFER_MINUTES) > MAX_TRANSFER_WAIT_MINUTES)) break;
      if (legs.stream().anyMatch(leg -> leg.run() == run)) continue;
      var passed = new ArrayList<Long>();
      for (var j = boarding.index() + 1; j < calls.size(); j++) {
        var next = calls.get(j).stopId();
        if (visited.contains(next) || passed.contains(next)) break;
        var leg = new Leg(run, boarding.index(), j);
        if (next == target) {
          var path = new ArrayList<>(legs);
          path.add(leg);
          out.add(new Journey(List.copyOf(path)));
          break;
        }
        passed.add(next);
        if (legs.size() + 1 < MAX_LEGS) {
          legs.add(leg);
          var seen = new HashSet<>(visited);
          seen.addAll(passed);
          search(next, target, calls.get(j).arrival() + TRANSFER_MINUTES, until, legs, seen, out);
          legs.removeLast();
        }
      }
    }
  }

  private static int waiting(Journey journey) {
    var total = 0;
    for (var i = 1; i < journey.legs().size(); i++)
      total += journey.legs().get(i).departure() - journey.legs().get(i - 1).arrival();
    return total;
  }

  private static Comparator<Journey> order() {
    return Comparator.comparingInt(Journey::departure)
        .thenComparingInt(Journey::arrival)
        .thenComparingInt(Journey::transfers);
  }

  /** {@code y} leaves no earlier, arrives no later, changes no more often and differs. */
  private static boolean dominates(Journey y, Journey x) {
    return y.departure() >= x.departure()
        && y.arrival() <= x.arrival()
        && y.transfers() <= x.transfers()
        && (y.departure() > x.departure()
            || y.arrival() < x.arrival()
            || y.transfers() < x.transfers());
  }
}
