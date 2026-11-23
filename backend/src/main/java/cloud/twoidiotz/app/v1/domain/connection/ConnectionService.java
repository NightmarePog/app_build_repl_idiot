package cloud.twoidiotz.app.v1.domain.connection;

import static cloud.twoidiotz.app.shared.web.PublicUrls.imageUrl;

import cloud.twoidiotz.app.v1.domain.line.LineResponse;
import cloud.twoidiotz.app.v1.domain.station.StationNotFoundException;
import cloud.twoidiotz.app.v1.domain.station.StationRepository;
import cloud.twoidiotz.app.v1.domain.trip.Clock;
import cloud.twoidiotz.app.v1.domain.trip.StopSummaryResponse;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Journey planning over one REPEATABLE_READ snapshot of the timetable. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
@RequiredArgsConstructor
public class ConnectionService {
  private final ConnectionRepository repository;
  private final StationRepository stations;

  public ConnectionSearchResponse search(
      long fromId, long toId, int minute, SearchMode mode, int limit) {
    var from = stations.findById(fromId).orElseThrow(() -> new StationNotFoundException(fromId));
    var to = stations.findById(toId).orElseThrow(() -> new StationNotFoundException(toId));
    var timetable = repository.timetable();
    var planner = new Planner(timetable.calls());
    var journeys =
        switch (mode) {
          case departure -> planner.departing(fromId, toId, minute, limit);
          case arrival -> planner.arriving(fromId, toId, minute, limit);
        };
    var connections = journeys.stream().map(journey -> connection(journey, timetable)).toList();
    return new ConnectionSearchResponse(
        new StopSummaryResponse(from.getId(), from.getName(), imageUrl(from.getImageUrl())),
        new StopSummaryResponse(to.getId(), to.getName(), imageUrl(to.getImageUrl())),
        Clock.time(minute),
        mode,
        Planner.TRANSFER_MINUTES,
        connections);
  }

  private static ConnectionResponse connection(
      Planner.Journey journey, ConnectionRepository.Timetable timetable) {
    var legs = new ArrayList<ConnectionLegResponse>();
    Integer previousArrival = null;
    for (var leg : journey.legs()) {
      var info = timetable.trips().get(leg.run().trip().id());
      var calls = leg.run().calls().subList(leg.board(), leg.alight() + 1);
      var stops =
          calls.stream()
              .map(
                  call ->
                      new ConnectionStopTimeResponse(
                          stop(timetable.stops().get(call.stopId())),
                          Clock.time(call.arrival()),
                          Clock.dayOffset(call.arrival()),
                          Clock.time(call.departure()),
                          Clock.dayOffset(call.departure())))
              .toList();
      legs.add(
          new ConnectionLegResponse(
              info.id(),
              line(info.line()),
              info.direction(),
              stop(info.destination()),
              stops.getFirst().stop(),
              stops.getLast().stop(),
              Clock.time(leg.departure()),
              Clock.dayOffset(leg.departure()),
              Clock.time(leg.arrival()),
              Clock.dayOffset(leg.arrival()),
              leg.arrival() - leg.departure(),
              previousArrival == null ? null : leg.departure() - previousArrival,
              stops,
              info.blockRef()));
      previousArrival = leg.arrival();
    }
    return new ConnectionResponse(
        Clock.time(journey.departure()),
        Clock.dayOffset(journey.departure()),
        Clock.time(journey.arrival()),
        Clock.dayOffset(journey.arrival()),
        journey.arrival() - journey.departure(),
        journey.transfers(),
        legs);
  }

  private static LineResponse line(ConnectionRepository.Line line) {
    return new LineResponse(line.id(), line.number(), line.name(), line.type(), line.color());
  }

  private static StopSummaryResponse stop(ConnectionRepository.Stop stop) {
    return new StopSummaryResponse(stop.id(), stop.name(), imageUrl(stop.imageUrl()));
  }
}
