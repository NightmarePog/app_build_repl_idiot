package cloud.twoidiotz.app.v1.domain.trip;

import cloud.twoidiotz.app.shared.serialization.SnakeCase;
import io.swagger.v3.oas.annotations.media.Schema;

@SnakeCase
@Schema(
    name = "TripStopTime",
    requiredProperties = {
      "stop_order",
      "stop",
      "arrival_time",
      "arrival_day_offset",
      "departure_time",
      "departure_day_offset"
    })
public record TripStopTimeResponse(
    @Schema(
            minimum = "1",
            example = "2",
            description = "1-based position of the stop on the route, as in GET /lines/{id}.")
        int stopOrder,
    StopSummaryResponse stop,
    @Schema(pattern = Clock.PATTERN, example = "07:04") String arrivalTime,
    @Schema(
            minimum = "0",
            example = "0",
            description =
                "Calendar days after the day the trip starts. Times past midnight are"
                    + " shown modulo 24 h with offset 1, so 24:14 in the source is 00:14 with"
                    + " offset 1.")
        int arrivalDayOffset,
    @Schema(pattern = Clock.PATTERN, example = "07:05") String departureTime,
    @Schema(
            minimum = "0",
            example = "0",
            description =
                "Calendar days after the day the trip starts. Times past midnight are"
                    + " shown modulo 24 h with offset 1, so 24:14 in the source is 00:14 with"
                    + " offset 1.")
        int departureDayOffset) {}
