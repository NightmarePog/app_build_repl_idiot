package cloud.twoidiotz.app.v1.domain.trip;

import cloud.twoidiotz.app.shared.serialization.SnakeCase;
import io.swagger.v3.oas.annotations.media.Schema;

@SnakeCase
@Schema(
    name = "StopSummary",
    requiredProperties = {"id", "name", "image_url"})
public record StopSummaryResponse(
    @Schema(minimum = "1", example = "8") long id,
    @Schema(maxLength = 255, example = "Kernel Hub") String name,
    @Schema(
            types = {"string", "null"},
            format = "uri",
            maxLength = 255)
        String imageUrl) {}
