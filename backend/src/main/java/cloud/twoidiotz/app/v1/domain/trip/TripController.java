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

@RestController
@RequiredArgsConstructor
public class TripController {
  private final TripService trips;

  @Tag(name = "Lines")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid identifier",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Line not found",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiGet(
      value = "/lines/{lineId}/trips",
      operationId = "listLineTrips",
      summary = "List trips of a line",
      description = "Returns all scheduled trips of the requested line.")
  public ResponseEntity<List<TripSummaryResponse>> ofLine(@PathVariable @Min(1) long lineId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(trips.ofLine(lineId));
  }

  @Tag(name = "Trips")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid identifier",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Trip not found",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiGet(
      value = "/trips/{tripId}",
      operationId = "getTrip",
      summary = "Get trip detail and timetable",
      description = "Returns the line, direction and ordered timetable of one trip.")
  public ResponseEntity<TripDetailResponse> get(@PathVariable @Min(1) long tripId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(trips.get(tripId));
  }

  @Tag(name = "Stops")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid identifier",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Stop not found",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiGet(
      value = "/stops/{stopId}/departures",
      operationId = "listStopDepartures",
      summary = "List departures from a stop",
      description =
          "Returns scheduled departures from the requested stop in ascending order by"
              + " `departure_day_offset` and `departure_time`.")
  public ResponseEntity<List<DepartureResponse>> departures(@PathVariable @Min(1) long stopId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(trips.departures(stopId));
  }
}
