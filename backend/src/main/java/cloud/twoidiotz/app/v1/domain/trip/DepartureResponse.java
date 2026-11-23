package cloud.twoidiotz.app.v1.domain.trip;

import cloud.twoidiotz.app.shared.serialization.SnakeCase;
import cloud.twoidiotz.app.v1.domain.line.LineResponse;
import cloud.twoidiotz.app.v1.domain.line.RouteDirection;
import io.swagger.v3.oas.annotations.media.Schema;

@SnakeCase
@Schema(
    name = "Departure",
    requiredProperties = {
      "trip_id",
      "line",
      "direction",
      "destination",
      "arrival_time",
      "arrival_day_offset",
      "departure_time",
      "departure_day_offset"
    })
public record DepartureResponse(
    @Schema(minimum = "1", example = "201001") long tripId,
    LineResponse line,
    @Schema(
            description =
                "`outbound` travels away from the line's starting stop; `inbound` travels back"
                    + " towards it.")
        RouteDirection direction,
    @Schema(description = "Final stop of the trip.") StopSummaryResponse destination,
    @Schema(pattern = Clock.PATTERN, example = "07:24", description = "Arrival at this stop.")
        String arrivalTime,
    @Schema(
            minimum = "0",
            example = "0",
            description =
                "Calendar days after the day the trip starts. Times past midnight are"
                    + " shown modulo 24 h with offset 1, so 24:14 in the source is 00:14 with"
                    + " offset 1.")
        int arrivalDayOffset,
    @Schema(pattern = Clock.PATTERN, example = "07:26", description = "Departure from this stop.")
        String departureTime,
    @Schema(
            minimum = "0",
            example = "0",
            description =
                "Calendar days after the day the trip starts. Times past midnight are"
                    + " shown modulo 24 h with offset 1, so 24:14 in the source is 00:14 with"
                    + " offset 1.")
        int departureDayOffset) {}
