package cloud.twoidiotz.app.v1.domain.trip;

import cloud.twoidiotz.app.shared.serialization.SnakeCase;
import cloud.twoidiotz.app.v1.domain.line.LineResponse;
import cloud.twoidiotz.app.v1.domain.line.RouteDirection;
import io.swagger.v3.oas.annotations.media.Schema;

@SnakeCase
@Schema(
    name = "TripSummary",
    requiredProperties = {
      "id",
      "route_id",
      "line",
      "direction",
      "origin",
      "destination",
      "departure_time",
      "departure_day_offset"
    })
public record TripSummaryResponse(
    @Schema(minimum = "1", example = "201001") long id,
    @Schema(
            example = "3",
            minimum = "1",
            description = "ID of the route the trip follows, as listed in the line's routes.")
        long routeId,
    LineResponse line,
    @Schema(description = "Outbound leaves the line's first stop, inbound returns to it.")
        RouteDirection direction,
    StopSummaryResponse origin,
    StopSummaryResponse destination,
    @Schema(pattern = Clock.PATTERN, example = "07:00", description = "Departure from the origin.")
        String departureTime,
    @Schema(minimum = "0", example = "0") int departureDayOffset) {}
