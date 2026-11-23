package cloud.twoidiotz.app.v1.domain.line;

import cloud.twoidiotz.app.shared.serialization.SnakeCase;
import io.swagger.v3.oas.annotations.media.Schema;

@SnakeCase
@Schema(
    name = "Line",
    requiredProperties = {"id", "number", "name", "type", "color"})
public record LineResponse(
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, minimum = "1", example = "1") long id,
    @Schema(maxLength = 20, example = "42") String number,
    @Schema(maxLength = 255, example = "Campus Express") String name,
    @Schema(maxLength = 50, example = "bus") String type,
    @Schema(pattern = "^#[0-9A-Fa-f]{6}$", example = "#4F46E5") String color) {}
