package cloud.twoidiotz.openapi;

import cloud.twoidiotz.app.shared.validation.AbsoluteUri;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;

/** Exposes custom validation constraints in OpenAPI 3.1 property schemas. */
@Configuration
public class OpenApiValidationConfiguration {
  @Bean
  PropertyCustomizer absoluteUriSchema() {
    return (schema, type) -> {
      Optional.ofNullable(schema)
          .filter(ignored -> hasAnnotation(type, AbsoluteUri.class))
          .ifPresent(value -> applyUriSchema(value, type));
      return schema;
    };
  }

  private static void applyUriSchema(Schema<?> schema, AnnotatedType type) {
    schema.setFormat("uri");
    schema.setType(null);
    schema.setNullable(null);
    schema.setTypes(new LinkedHashSet<>(stringTypes(type)));
  }

  private static List<String> stringTypes(AnnotatedType type) {
    return Stream.of(NotNull.class, NotBlank.class, NotEmpty.class)
            .anyMatch(annotation -> hasAnnotation(type, annotation))
        ? List.of("string")
        : List.of("string", "null");
  }

  private static boolean hasAnnotation(AnnotatedType type, Class<? extends Annotation> annotation) {
    return Stream.ofNullable(type.getCtxAnnotations())
        .flatMap(Stream::of)
        .anyMatch(
            value ->
                value.annotationType() == annotation
                    || AnnotatedElementUtils.hasAnnotation(value.annotationType(), annotation));
  }
}
