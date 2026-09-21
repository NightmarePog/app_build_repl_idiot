package cloud.twoidiotz.openapi;

import static org.junit.jupiter.api.Assertions.*;

import cloud.twoidiotz.app.shared.validation.AbsoluteUri;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springdoc.core.converters.PropertyCustomizingConverter;

class OpenApiValidationConfigurationTest {
  @Test
  void documentsNullableUriAndPreservesOtherConstraints() {
    var property = schema().getProperties().get("optionalUri");
    assertEquals("uri", property.getFormat());
    assertEquals(Set.of("string", "null"), property.getTypes());
    assertEquals(255, property.getMaxLength());
    assertFalse(schema().getRequired().contains("optionalUri"));
  }

  @Test
  void nullRejectingConstraintsProduceNonNullableUris() {
    var schema = schema();
    for (var name : List.of("requiredUri", "nonBlankUri", "nonEmptyUri")) {
      var property = schema.getProperties().get(name);
      assertEquals("uri", property.getFormat());
      assertEquals(Set.of("string"), property.getTypes());
    }
    assertTrue(schema.getRequired().contains("requiredUri"));
  }

  @Test
  void leavesUnrelatedPropertiesUnchanged() {
    var property = schema().getProperties().get("plainText");
    assertNull(property.getFormat());
    assertEquals(Set.of("string"), property.getTypes());
    assertEquals(40, property.getMaxLength());
  }

  private Schema<?> schema() {
    var converters = new ModelConverters(true);
    converters.addConverter(
        new PropertyCustomizingConverter(
            Optional.of(List.of(new OpenApiValidationConfiguration().absoluteUriSchema()))));
    return converters.read(UriFields.class).get("UriFields");
  }

  record UriFields(
      @AbsoluteUri @Size(max = 255) String optionalUri,
      @AbsoluteUri @NotNull String requiredUri,
      @AbsoluteUri @NotBlank String nonBlankUri,
      @AbsoluteUri @NotEmpty String nonEmptyUri,
      @Size(max = 40) String plainText) {}
}
