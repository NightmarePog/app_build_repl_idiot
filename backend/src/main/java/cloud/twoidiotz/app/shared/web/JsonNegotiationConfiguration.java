package cloud.twoidiotz.app.shared.web;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.accept.ContentNegotiationStrategy;
import org.springframework.web.accept.HeaderContentNegotiationStrategy;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The API only speaks JSON. A client asking for something else (Accept: text/html) still gets the
 * JSON body and status, instead of a 406 that would hide a 400 or 404.
 */
@Configuration
public class JsonNegotiationConfiguration implements WebMvcConfigurer {
  @Override
  public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
    var header = new HeaderContentNegotiationStrategy();
    ContentNegotiationStrategy jsonFallback =
        request -> {
          var requested = header.resolveMediaTypes(request);
          if (requested.stream().anyMatch(MediaType.ALL::equals)) return requested;
          var withFallback = new ArrayList<>(requested);
          withFallback.add(MediaType.ALL);
          return List.copyOf(withFallback);
        };
    configurer.strategies(List.of(jsonFallback));
  }
}
