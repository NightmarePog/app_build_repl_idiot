package cloud.twoidiotz.app.v1.domain.trip;

import cloud.twoidiotz.app.shared.api.ApiGet;
import cloud.twoidiotz.app.shared.error.ApiError;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase-4 timetable reads. The timetable is the same every day and the contract has no date
 * parameter, so nothing is filtered by date: 404 only means the line, trip or stop does not exist.
 */
@RestController
@RequiredArgsConstructor
public class TripController {
  private final TripService trips;

  @Tag(name = "Lines")
  @InvalidIdResponse
  @ApiResponse(
      responseCode = "404",
      description = "Line not found (`{\"error\": \"Line not found\"}`).",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiGet(
      value = "/lines/{lineId}/trips",
      operationId = "listLineTrips",
      summary = "List trips of a line",
      description =
          "Returns all scheduled trips of the requested line, both directions, in ascending"
              + " order by `departure_day_offset` and `departure_time` of the trip's start; trips"
              + " leaving in the same minute are ordered outbound before inbound, then by `id`."
              + " A line without trips returns an empty array.")
  public ResponseEntity<List<TripSummaryResponse>> ofLine(@PathVariable @Min(1) long lineId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(trips.ofLine(lineId));
  }

  @Tag(name = "Trips")
  @InvalidIdResponse
  @ApiResponse(
      responseCode = "404",
      description = "Trip not found (`{\"error\": \"Trip not found\"}`).",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiGet(
      value = "/trips/{tripId}",
      operationId = "getTrip",
      summary = "Get trip detail and timetable",
      description =
          "Returns the line, direction and ordered timetable of one trip. `stops` follow the"
              + " route's stop order; day offsets count from the day the trip starts, so a stop"
              + " reached after midnight has offset 1.")
  public ResponseEntity<TripDetailResponse> get(@PathVariable @Min(1) long tripId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(trips.get(tripId));
  }

  @Tag(name = "Stops")
  @InvalidIdResponse
  @ApiResponse(
      responseCode = "404",
      description = "Stop not found (`{\"error\": \"Stop not found\"}`).",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiGet(
      value = "/stops/{stopId}/departures",
      operationId = "listStopDepartures",
      summary = "List departures from a stop",
      description =
          "Returns scheduled departures from the requested stop in ascending order by"
              + " `departure_day_offset` and `departure_time`; departures in the same minute are"
              + " ordered by line number, then `trip_id`. A trip's last stop is only an arrival,"
              + " so trips ending at this stop are not listed. The whole day's timetable is"
              + " returned (there is no date parameter); a stop no trip leaves from returns an"
              + " empty array.")
  public ResponseEntity<List<DepartureResponse>> departures(@PathVariable @Min(1) long stopId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(trips.departures(stopId));
  }
}
