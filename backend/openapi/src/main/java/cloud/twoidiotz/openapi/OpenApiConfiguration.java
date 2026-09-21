package cloud.twoidiotz.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@OpenAPIDefinition(
    info =
        @Info(
            title = "Think different Transit API",
            version = "1.1.0",
            description = "API for managing public transport stops in Think different Academy."),
    servers = @Server(url = "/api/v1"))
@Configuration(proxyBeanMethods = false)
@Import({
  OpenApiOperationConfiguration.class,
  OpenApiErrorConfiguration.class,
  OpenApiParameterConfiguration.class,
  OpenApiValidationConfiguration.class
})
public class OpenApiConfiguration {}
