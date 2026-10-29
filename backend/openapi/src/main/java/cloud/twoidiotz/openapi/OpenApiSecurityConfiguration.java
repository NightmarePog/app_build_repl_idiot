package cloud.twoidiotz.openapi;

import cloud.twoidiotz.app.shared.auth.AdminApiKeyPolicy;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Arrays;
import java.util.Optional;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;

/** Documents the admin API key exactly where the runtime filter enforces it. */
@Configuration
public class OpenApiSecurityConfiguration {
  static final String SCHEME = "bearerAuth";

  @Bean
  OpenApiCustomizer adminApiKeyScheme() {
    return api ->
        Optional.ofNullable(api.getComponents())
            .orElseGet(() -> api.components(new Components()).getComponents())
            .addSecuritySchemes(
                SCHEME,
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .description("Admin API key"));
  }

  @Bean
  OperationCustomizer adminApiKeyRequirement() {
    return (operation, handler) -> {
      if (requiresAdminKey(handler)) {
        operation.addSecurityItem(new SecurityRequirement().addList(SCHEME));
        OpenApiErrorConfiguration.addErrorResponse(
            operation, "401", "Missing or invalid admin API key");
      }
      return operation;
    };
  }

  private static boolean requiresAdminKey(HandlerMethod handler) {
    return Optional.ofNullable(handler.getMethodAnnotation(RequestMapping.class))
        .map(RequestMapping::method)
        .stream()
        .flatMap(Arrays::stream)
        .map(RequestMethod::asHttpMethod)
        .anyMatch(AdminApiKeyPolicy::requiresAdminKey);
  }
}
