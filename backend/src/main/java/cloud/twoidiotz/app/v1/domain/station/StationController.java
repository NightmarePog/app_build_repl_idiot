package cloud.twoidiotz.app.v1.domain.station;

import cloud.twoidiotz.app.shared.api.ApiDelete;
import cloud.twoidiotz.app.shared.api.ApiGet;
import cloud.twoidiotz.app.shared.api.ApiPost;
import cloud.twoidiotz.app.shared.api.ApiPut;
import cloud.twoidiotz.app.shared.api.ApiResource;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ApiResource("Stop")
@Tag(name = "Stops", description = "Stop management")
@RequiredArgsConstructor
public class StationController {
  private final StationService stations;

  @ApiGet("/stops")
  public ResponseEntity<List<StationResponse>> list() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(stations.list());
  }

  @ApiGet("/stops/{id}")
  public ResponseEntity<StationResponse> get(@PathVariable @Min(1) long id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(stations.get(id));
  }

  @ApiPost("/stops")
  public ResponseEntity<StationResponse> create(@Valid @RequestBody StationRequest input) {
    var stop = stations.create(input);
    return ResponseEntity.created(URI.create("/api/v1/stops/" + stop.id())).body(stop);
  }

  @ApiPut(
      value = "/stops/{id}",
      description =
          "Replaces the editable data of an existing stop. The complete StopInput object must be provided.")
  public StationResponse update(
      @PathVariable @Min(1) long id, @Valid @RequestBody StationRequest input) {
    return stations.update(id, input);
  }

  @ApiDelete("/stops/{id}")
  public void delete(@PathVariable @Min(1) long id) {
    stations.delete(id);
  }
}
