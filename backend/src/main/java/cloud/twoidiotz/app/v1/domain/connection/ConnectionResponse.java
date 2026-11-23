package cloud.twoidiotz.app.v1.domain.connection;

import cloud.twoidiotz.app.shared.serialization.SnakeCase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@SnakeCase
@Schema(
    name = "Connection",
    description = "One journey from the origin to the destination, possibly with transfers.",
    requiredProperties = {
      "departure_time",
      "departure_day_offset",
      "arrival_time",
      "arrival_day_offset",
      "duration_minutes",
      "transfers",
      "legs"
    })
public record ConnectionResponse(
    @Schema(pattern = ConnectionDocs.TIME, example = "07:05") String departureTime,
    @Schema(minimum = "-1", example = "0", description = ConnectionDocs.OFFSET)
        int departureDayOffset,
    @Schema(pattern = ConnectionDocs.TIME, example = "07:34") String arrivalTime,
    @Schema(minimum = "-1", example = "0", description = ConnectionDocs.OFFSET)
        int arrivalDayOffset,
    @Schema(minimum = "0", example = "29") int durationMinutes,
    @Schema(minimum = "0", example = "1", description = "Number of changes between buses.")
        int transfers,
    List<ConnectionLegResponse> legs) {}
