package cloud.twoidiotz.app.v1.domain.connection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Connection Scan Algorithm over a timetable that repeats every day. Each trip runs on the day
 * before, the search day and the day after (minutes shifted by -1440, 0, +1440), so a trip that
 * starts at 23:52 and arrives at 24:14 is caught both late in the evening and just after midnight.
 * Changing buses at a stop needs {@link #TRANSFER_MINUTES}; staying on the same bus needs nothing,
 * so zero-minute dwells and buses that overtake each other at a stop are handled by time alone.
 * Arrival searches run the same scan on the mirrored timetable (time negated, calls reversed).
 */
final class Planner {
  static final int TRANSFER_MINUTES = 2;
  private static final int DAY = 24 * 60;
  private static final int INF = Integer.MAX_VALUE;
  private static final int MAX_SCANS = 500;

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

    String key() {
      var key = new StringBuilder();
      for (var leg : legs)
        key.append(leg.run().trip().id())
            .append('@')
            .append(leg.run().shift())
            .append(':')
            .append(leg.board())
            .append('-')
            .append(leg.alight())
            .append(';');
      return key.toString();
    }
  }

  private record Hop(int run, int index, long from, long to, int departure, int arrival) {}

  private final List<Run> runs = new ArrayList<>();
  private final List<Run> mirrored = new ArrayList<>();
  private final List<Hop> hops;
  private final List<Hop> mirroredHops;

  Planner(List<Trip> trips) {
    for (var trip : trips) {
      for (var shift : new int[] {-DAY, 0, DAY}) {
        var calls =
            trip.calls().stream()
                .map(c -> new Call(c.stopId(), c.arrival() + shift, c.departure() + shift))
                .toList();
        runs.add(new Run(trip, shift, calls));
        var reversed = new ArrayList<Call>();
        for (var call : calls.reversed())
          reversed.add(new Call(call.stopId(), -call.departure(), -call.arrival()));
        mirrored.add(new Run(trip, shift, reversed));
      }
    }
    hops = hops(runs);
    mirroredHops = hops(mirrored);
  }

  /** Up to {@code limit} Pareto-optimal journeys leaving at or after {@code time}, by departure. */
  List<Journey> departing(long from, long to, int time, int limit) {
    var found = new ArrayList<Journey>();
    var t = time;
    for (var scan = 0; scan < MAX_SCANS && found.size() < limit; scan++) {
      var journey = scan(runs, hops, from, to, t);
      if (journey.isEmpty()) break;
      add(found, journey.get());
      if (journey.get().departure() >= time + DAY) {
        // Past the window, but it still prunes e.g. riding away at 23:06 to wait for 07:00.
        found.removeIf(j -> j.departure() >= time + DAY);
        break;
      }
      t = journey.get().departure() + 1;
    }
    return found;
  }

  /** Up to {@code limit} Pareto-optimal journeys arriving at or before {@code time}. */
  List<Journey> arriving(long from, long to, int time, int limit) {
    var found = new ArrayList<Journey>();
    var t = time;
    for (var scan = 0; scan < MAX_SCANS && found.size() < limit; scan++) {
      var journey = scan(mirrored, mirroredHops, to, from, -t).map(this::unmirror);
      if (journey.isEmpty()) break;
      add(found, journey.get());
      if (journey.get().arrival() <= time - DAY) {
        found.removeIf(j -> j.arrival() <= time - DAY);
        break;
      }
      t = journey.get().arrival() - 1;
    }
    found.sort(Comparator.comparingInt(Journey::departure));
    return found;
  }

  /** Adds a journey and drops every journey another one beats (leaves later, arrives sooner). */
  private static void add(List<Journey> found, Journey journey) {
    found.add(journey);
    found.removeIf(
        x -> found.stream().anyMatch(y -> y != x && dominates(y, x) && !dominates(x, y)));
    var keys = new java.util.HashSet<String>();
    found.removeIf(x -> !keys.add(x.key()));
    found.sort(Comparator.comparingInt(Journey::departure).thenComparingInt(Journey::arrival));
  }

  private static boolean dominates(Journey y, Journey x) {
    return y.departure() >= x.departure()
        && y.arrival() <= x.arrival()
        && y.transfers() <= x.transfers();
  }

  /** Earliest arrival at {@code to} leaving {@code from} at or after {@code time}. */
  private static Optional<Journey> scan(
      List<Run> runs, List<Hop> hops, long from, long to, int time) {
    Map<Long, Integer> ready = new HashMap<>(); // earliest time a bus can be boarded at the stop
    Map<Long, Integer> arrival = new HashMap<>();
    Map<Long, Integer> legs = new HashMap<>();
    Map<Long, Leg> label = new HashMap<>();
    var boardedAt = new int[runs.size()];
    var boardedLegs = new int[runs.size()];
    java.util.Arrays.fill(boardedAt, -1);
    ready.put(from, time);
    arrival.put(from, time);
    legs.put(from, 0);
    for (var hop : hops) {
      if (hop.departure() < time) continue;
      if (hop.departure() > arrival.getOrDefault(to, INF)) break;
      if (boardedAt[hop.run()] < 0 && ready.getOrDefault(hop.from(), INF) <= hop.departure()) {
        boardedAt[hop.run()] = hop.index();
        boardedLegs[hop.run()] = legs.get(hop.from()) + 1;
      }
      if (boardedAt[hop.run()] < 0) continue;
      var count = boardedLegs[hop.run()];
      var known = arrival.getOrDefault(hop.to(), INF);
      if (hop.arrival() < known
          || (hop.arrival() == known && count < legs.getOrDefault(hop.to(), INF))) {
        arrival.put(hop.to(), hop.arrival());
        legs.put(hop.to(), count);
        label.put(hop.to(), new Leg(runs.get(hop.run()), boardedAt[hop.run()], hop.index() + 1));
        var change = hop.to() == to ? 0 : TRANSFER_MINUTES;
        ready.merge(hop.to(), hop.arrival() + change, Math::min);
      }
    }
    if (!label.containsKey(to)) return Optional.empty();
    var path = new ArrayList<Leg>();
    for (var stop = to; label.containsKey(stop) && stop != from; ) {
      var leg = label.get(stop);
      path.addFirst(leg);
      stop = leg.run().calls().get(leg.board()).stopId();
      if (path.size() > hops.size()) throw new IllegalStateException("Cyclic journey");
    }
    return Optional.of(new Journey(path));
  }

  /** Maps a journey found on the mirrored timetable back to real trips and call indices. */
  private Journey unmirror(Journey mirroredJourney) {
    var legs = new ArrayList<Leg>();
    for (var leg : mirroredJourney.legs()) {
      var real = runs.get(mirrored.indexOf(leg.run()));
      var last = real.calls().size() - 1;
      legs.addFirst(new Leg(real, last - leg.alight(), last - leg.board()));
    }
    return new Journey(legs);
  }

  private static List<Hop> hops(List<Run> runs) {
    var hops = new ArrayList<Hop>();
    for (var r = 0; r < runs.size(); r++) {
      var calls = runs.get(r).calls();
      for (var i = 0; i + 1 < calls.size(); i++)
        hops.add(
            new Hop(
                r,
                i,
                calls.get(i).stopId(),
                calls.get(i + 1).stopId(),
                calls.get(i).departure(),
                calls.get(i + 1).arrival()));
    }
    hops.sort(
        Comparator.comparingInt(Hop::departure)
            .thenComparingInt(Hop::arrival)
            .thenComparingInt(Hop::run)
            .thenComparingInt(Hop::index));
    return hops;
  }
}
