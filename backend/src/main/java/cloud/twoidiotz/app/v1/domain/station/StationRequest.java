package cloud.twoidiotz.app.v1.domain.station;

import cloud.twoidiotz.app.shared.serialization.SnakeCase;
import cloud.twoidiotz.app.shared.validation.AbsoluteUri;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@SnakeCase
@Schema(name = "StopInput", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record StationRequest(
    @NotNull @Size(min = 1, max = 255) String name,
    @AbsoluteUri @Size(max = 255) String imageUrl,
    @NotNull Boolean wheelchairAccessible,
    @NotNull Boolean hasShelter,
    @NotNull Boolean hasTicketMachine) {}
