package cloud.twoidiotz.app.v1.domain.line;

import cloud.twoidiotz.app.shared.serialization.SnakeCase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@SnakeCase
@Schema(
    name = "LineDetail",
    requiredProperties = {"id", "number", "name", "type", "color", "routes"})
public record LineDetailResponse(
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, example = "1") long id,
    @Schema(maxLength = 20, example = "42") String number,
    @Schema(maxLength = 255, example = "Campus Express") String name,
    @Schema(maxLength = 50, example = "bus") String type,
    @Schema(example = "#4F46E5") String color,
    List<RouteResponse> routes) {}
