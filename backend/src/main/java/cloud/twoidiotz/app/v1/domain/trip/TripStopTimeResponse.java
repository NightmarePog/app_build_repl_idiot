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
    @Schema(minimum = "1", example = "2", description = "1-based order on the route") int stopOrder,
    StopSummaryResponse stop,
    @Schema(pattern = Clock.PATTERN, example = "07:04") String arrivalTime,
    @Schema(minimum = "0", example = "0") int arrivalDayOffset,
    @Schema(pattern = Clock.PATTERN, example = "07:05") String departureTime,
    @Schema(minimum = "0", example = "0") int departureDayOffset) {}
