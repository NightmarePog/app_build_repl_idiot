package cloud.twoidiotz.app.v1.domain.station;

import cloud.twoidiotz.app.shared.serialization.SnakeCase;
import cloud.twoidiotz.app.shared.validation.AbsoluteUri;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@SnakeCase
@Schema(
    name = "Stop",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
    requiredProperties = {
      "id",
      "name",
      "image_url",
      "wheelchair_accessible",
      "has_shelter",
      "has_ticket_machine"
    })
public record StationResponse(
    @Schema(accessMode = Schema.AccessMode.READ_ONLY) long id,
    @Schema(minLength = 1, maxLength = 255) String name,
    @AbsoluteUri @Size(max = 255) String imageUrl,
    boolean wheelchairAccessible,
    boolean hasShelter,
    boolean hasTicketMachine) {}
