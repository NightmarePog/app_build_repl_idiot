package cloud.twoidiotz.app.v1.domain.trip;

import static cloud.twoidiotz.app.shared.web.PublicUrls.imageUrl;

import cloud.twoidiotz.app.v1.domain.line.LineNotFoundException;
import cloud.twoidiotz.app.v1.domain.line.LineRepository;
import cloud.twoidiotz.app.v1.domain.line.LineResponse;
import cloud.twoidiotz.app.v1.domain.station.StationNotFoundException;
import cloud.twoidiotz.app.v1.domain.station.StationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TripService {
  private final TripRepository trips;
  private final LineRepository lines;
  private final StationRepository stations;

  /** Trips of a line by start (day offset, then time); same-minute ties by direction. */
  public List<TripSummaryResponse> ofLine(long lineId) {
    if (!lines.existsById(lineId)) throw new LineNotFoundException(lineId);
    return trips.ofLine(lineId).stream().map(TripService::summary).toList();
  }

  /** Header and stop list are read in one snapshot so they always agree. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public TripDetailResponse get(long tripId) {
    var trip = trips.trip(tripId).orElseThrow(() -> new TripNotFoundException(tripId));
    var start = trip.startMinute();
    var stops =
        trips.stops(tripId).stream()
            .map(
                row ->
                    new TripStopTimeResponse(
                        row.stopOrder(),
                        stop(row.stop()),
                        Clock.time(row.arrival()),
                        Clock.dayOffset(row.arrival()),
                        Clock.time(row.departure()),
                        Clock.dayOffset(row.departure())))
            .toList();
    return new TripDetailResponse(
        trip.id(),
        trip.routeId(),
        line(trip.line()),
        trip.direction(),
        stop(trip.origin()),
        stop(trip.destination()),
        Clock.time(start),
        Clock.dayOffset(start),
        stops);
  }

  public List<DepartureResponse> departures(long stopId) {
    if (!stations.existsById(stopId)) throw new StationNotFoundException(stopId);
    return trips.departures(stopId).stream()
        .map(
            row ->
                new DepartureResponse(
                    row.tripId(),
                    line(row.line()),
                    row.direction(),
                    stop(row.destination()),
                    Clock.time(row.arrival()),
                    Clock.dayOffset(row.arrival()),
                    Clock.time(row.departure()),
                    Clock.dayOffset(row.departure())))
        .toList();
  }

  private static TripSummaryResponse summary(TripRow.Trip trip) {
    return new TripSummaryResponse(
        trip.id(),
        trip.routeId(),
        line(trip.line()),
        trip.direction(),
        stop(trip.origin()),
        stop(trip.destination()),
        Clock.time(trip.startMinute()),
        Clock.dayOffset(trip.startMinute()));
  }

  private static LineResponse line(TripRow.Line line) {
    return new LineResponse(line.id(), line.number(), line.name(), line.type(), line.color());
  }

  private static StopSummaryResponse stop(TripRow.Stop stop) {
    return new StopSummaryResponse(stop.id(), stop.name(), imageUrl(stop.imageUrl()));
  }
}
