package cloud.twoidiotz.app.v1.domain.health;

import cloud.twoidiotz.app.shared.api.ApiGet;
import cloud.twoidiotz.app.shared.api.ApiResource;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiResource("Health")
@Tag(name = "Health", description = "Service health")
public class HealthController {
  @ApiGet("/health")
  public ResponseEntity<HealthResponse> get() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new HealthResponse(HealthResponse.Status.OK));
  }
}
