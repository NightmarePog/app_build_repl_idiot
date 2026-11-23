package cloud.twoidiotz.app.v1.domain.connection;

import cloud.twoidiotz.app.shared.serialization.SnakeCase;
import cloud.twoidiotz.app.v1.domain.line.LineResponse;
import cloud.twoidiotz.app.v1.domain.line.RouteDirection;
import cloud.twoidiotz.app.v1.domain.trip.StopSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@SnakeCase
@Schema(
    name = "ConnectionLeg",
    description = "One ride on one trip, from boarding to alighting.",
    requiredProperties = {
      "trip_id",
      "line",
      "direction",
      "destination",
      "from",
      "to",
      "departure_time",
      "departure_day_offset",
      "arrival_time",
      "arrival_day_offset",
      "duration_minutes",
      "transfer_minutes",
      "stops"
    })
public record ConnectionLegResponse(
    @Schema(minimum = "1", example = "201001") long tripId,
    LineResponse line,
    @Schema(
            description =
                "`outbound` travels away from the line's starting stop; `inbound` travels back"
                    + " towards it.")
        RouteDirection direction,
    @Schema(description = "Final stop of the trip (its headsign).") StopSummaryResponse destination,
    @Schema(description = "Boarding stop.") StopSummaryResponse from,
    @Schema(description = "Alighting stop.") StopSummaryResponse to,
    @Schema(pattern = ConnectionDocs.TIME, example = "07:05", description = "Departure from `from`.")
        String departureTime,
    @Schema(minimum = "-1", example = "0", description = ConnectionDocs.OFFSET)
        int departureDayOffset,
    @Schema(pattern = ConnectionDocs.TIME, example = "07:11", description = "Arrival at `to`.")
        String arrivalTime,
    @Schema(minimum = "-1", example = "0", description = ConnectionDocs.OFFSET)
        int arrivalDayOffset,
    @Schema(minimum = "0", example = "6") int durationMinutes,
    @Schema(
            types = {"integer", "null"},
            minimum = "0",
            example = "9",
            description =
                "Minutes waited at `from` after the previous leg arrived; null on the first leg.")
        Integer transferMinutes,
    @Schema(description = "Every call from `from` to `to`, both included, in travel order.")
        List<ConnectionStopTimeResponse> stops) {}
