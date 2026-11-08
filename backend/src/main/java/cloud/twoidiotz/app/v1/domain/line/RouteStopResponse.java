package cloud.twoidiotz.app.v1.domain.line;

import cloud.twoidiotz.app.shared.serialization.SnakeCase;
import io.swagger.v3.oas.annotations.media.Schema;

@SnakeCase
@Schema(
    name = "RouteStop",
    requiredProperties = {"id", "name", "position"})
public record RouteStopResponse(
    @Schema(description = "Stop ID", example = "4") long id,
    @Schema(example = "Byte Square") String name,
    @Schema(description = "1-based order on the route", example = "1") int position) {}
