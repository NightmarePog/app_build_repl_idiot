package cloud.twoidiotz.app.v1.domain.trip;

import cloud.twoidiotz.app.shared.serialization.SnakeCase;
import cloud.twoidiotz.app.v1.domain.line.LineResponse;
import cloud.twoidiotz.app.v1.domain.line.RouteDirection;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@SnakeCase
@Schema(
    name = "TripDetail",
    requiredProperties = {
      "id",
      "route_id",
      "line",
      "direction",
      "origin",
      "destination",
      "departure_time",
      "departure_day_offset",
      "stops"
    })
public record TripDetailResponse(
    @Schema(minimum = "1", example = "201001") long id,
    @Schema(example = "3", minimum = "1") long routeId,
    LineResponse line,
    @Schema(
            description =
                "`outbound` travels away from the line's starting stop; `inbound` travels back"
                    + " towards it.")
        RouteDirection direction,
    StopSummaryResponse origin,
    StopSummaryResponse destination,
    @Schema(pattern = Clock.PATTERN, example = "07:00") String departureTime,
    @Schema(
            minimum = "0",
            example = "0",
            description = "Day offset of the trip start, counted from its operating day.")
        int departureDayOffset,
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        "Timetabled stops in route order (ascending `stop_order`). A trip has at"
                            + " least two stops (the contract only requires one)."),
            minItems = 1,
            schema = @Schema(implementation = TripStopTimeResponse.class))
        List<TripStopTimeResponse> stops) {}
