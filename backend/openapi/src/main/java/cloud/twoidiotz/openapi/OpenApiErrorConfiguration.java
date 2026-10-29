package cloud.twoidiotz.openapi;

import cloud.twoidiotz.app.shared.error.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

/** Documents applicable runtime advice errors using the shared CRUD conventions. */
@Configuration
public class OpenApiErrorConfiguration {
  @Bean
  OpenApiCustomizer errorSchema() {
    return api -> ModelConverters.getInstance(true).read(ApiError.class).forEach(api::schema);
  }

  @Bean
  OperationCustomizer errorResponses(ApplicationContext context) {
    var advice = AdviceErrorResponses.discover(context);
    return (operation, handler) -> {
      ResourceNames.resource(handler)
          .ifPresent(
              ignored -> {
                addBadRequestResponse(operation, handler, advice);
                addNotFoundResponse(operation, handler, advice);
              });
      return operation;
    };
  }

  private static void addBadRequestResponse(
      Operation operation, HandlerMethod handler, List<AdviceErrorResponses> advice) {
    Optional.of(handler)
        .filter(ResourceErrorRules::acceptsInput)
        .filter(value -> supports(advice, value, 400))
        .map(value -> ResourceNames.singular(value, ""))
        .map(OpenApiErrorConfiguration::badRequestDescription)
        .ifPresent(description -> addErrorResponse(operation, "400", description));
  }

  private static void addNotFoundResponse(
      Operation operation, HandlerMethod handler, List<AdviceErrorResponses> advice) {
    Optional.of(handler)
        .filter(ResourceErrorRules::looksUpResource)
        .filter(value -> supports(advice, value, 404))
        .map(value -> ResourceNames.singular(value, "") + " not found")
        .ifPresent(description -> addErrorResponse(operation, "404", description));
  }

  private static boolean supports(
      List<AdviceErrorResponses> advice, HandlerMethod handler, int status) {
    return advice.stream().anyMatch(value -> value.supports(handler.getBeanType(), status));
  }

  private static String badRequestDescription(String resource) {
    return "Invalid " + resource.toLowerCase(Locale.ROOT) + " ID or input data";
  }

  static void addErrorResponse(Operation operation, String status, String description) {
    // An explicitly documented response takes precedence over the shared default.
    responses(operation).computeIfAbsent(status, ignored -> response(description));
  }

  private static ApiResponses responses(Operation operation) {
    return Optional.ofNullable(operation.getResponses())
        .orElseGet(() -> operation.responses(new ApiResponses()).getResponses());
  }

  private static ApiResponse response(String description) {
    return new ApiResponse().description(description).content(errorContent());
  }

  private static Content errorContent() {
    return new Content()
        .addMediaType(
            "application/json",
            new MediaType().schema(new Schema<>().$ref("#/components/schemas/Error")));
  }
}
