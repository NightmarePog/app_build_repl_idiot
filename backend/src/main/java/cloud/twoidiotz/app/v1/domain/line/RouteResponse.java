package cloud.twoidiotz.app.v1.domain.line;

import cloud.twoidiotz.app.shared.serialization.SnakeCase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@SnakeCase
@Schema(
    name = "Route",
    requiredProperties = {"id", "direction", "bound", "stops"})
public record RouteResponse(
    @Schema(example = "12") long id,
    @Schema(
            maxLength = 255,
            example = "Notebook Quay",
            description = "Where the route heads, i.e. the name of its last stop.")
        String direction,
    @Schema(description = "Outbound leaves the line's first stop, inbound returns to it.")
        RouteDirection bound,
    @Schema(description = "Stops in travel order.") List<RouteStopResponse> stops) {}
