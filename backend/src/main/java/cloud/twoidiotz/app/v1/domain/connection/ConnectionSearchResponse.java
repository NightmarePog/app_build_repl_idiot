package cloud.twoidiotz.app.v1.domain.connection;

import cloud.twoidiotz.app.shared.serialization.SnakeCase;
import cloud.twoidiotz.app.v1.domain.trip.StopSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@SnakeCase
@Schema(
    name = "ConnectionSearch",
    requiredProperties = {
      "from",
      "to",
      "time",
      "mode",
      "min_transfer_minutes",
      "connections"
    })
public record ConnectionSearchResponse(
    StopSummaryResponse from,
    StopSummaryResponse to,
    @Schema(
            pattern = ConnectionDocs.TIME,
            example = "07:00",
            description = "The searched time (the request's, or the current Prague time).")
        String time,
    SearchMode mode,
    @Schema(minimum = "0", example = "2", description = "Minimum time for changing buses.")
        int minTransferMinutes,
    @Schema(description = "Connections in ascending order of departure; may be empty.")
        List<ConnectionResponse> connections) {}
