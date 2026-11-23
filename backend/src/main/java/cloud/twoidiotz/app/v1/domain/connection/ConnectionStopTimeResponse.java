package cloud.twoidiotz.app.v1.domain.connection;

import cloud.twoidiotz.app.shared.serialization.SnakeCase;
import cloud.twoidiotz.app.v1.domain.trip.StopSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@SnakeCase
@Schema(
    name = "ConnectionStopTime",
    description = "One call of the bus during a leg, from the boarding stop to the alighting stop.",
    requiredProperties = {
      "stop",
      "arrival_time",
      "arrival_day_offset",
      "departure_time",
      "departure_day_offset"
    })
public record ConnectionStopTimeResponse(
    StopSummaryResponse stop,
    @Schema(pattern = ConnectionDocs.TIME, example = "07:04") String arrivalTime,
    @Schema(minimum = "-1", example = "0", description = ConnectionDocs.OFFSET)
        int arrivalDayOffset,
    @Schema(pattern = ConnectionDocs.TIME, example = "07:05") String departureTime,
    @Schema(minimum = "-1", example = "0", description = ConnectionDocs.OFFSET)
        int departureDayOffset) {}
