package cloud.twoidiotz.app.shared.error;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "Error",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
    requiredProperties = {"error"})
public record ApiError(@Schema(example = "Stop not found") String error) {}
