package cloud.twoidiotz.app.v1.domain.line;

import cloud.twoidiotz.app.shared.serialization.SnakeCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@SnakeCase
@Schema(name = "LineInput", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record LineRequest(
    @NotBlank @Size(max = 20) @Schema(example = "42") String number,
    @NotBlank @Size(max = 255) @Schema(example = "Campus Express") String name,
    @NotBlank @Size(max = 50) @Schema(example = "bus") String type,
    @NotNull @Pattern(regexp = LineRequest.COLOR) @Schema(example = "#4F46E5") String color) {
  public static final String COLOR = "^#[0-9A-Fa-f]{6}$";
}
