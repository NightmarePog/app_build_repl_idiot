package cloud.twoidiotz.app.v1.domain.line;

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
@ApiResource("Line")
@Tag(name = "Lines", description = "Operations related to public transport lines")
@RequiredArgsConstructor
public class LineController {
  private final LineService lines;

  @ApiGet("/lines")
  public ResponseEntity<List<LineResponse>> list() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(lines.list());
  }

  @ApiGet(
      value = "/lines/{id}",
      description =
          "Returns the details of a specific line, including its routes (one per direction).")
  public ResponseEntity<LineDetailResponse> get(@PathVariable @Min(1) long id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(lines.get(id));
  }

  @ApiGet(
      value = "/stops/{id}/lines",
      operationId = "getStopLines",
      summary = "List lines serving a stop",
      description = "Returns every line with a route that passes through the stop.")
  public ResponseEntity<List<LineResponse>> servingStop(@PathVariable @Min(1) long id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(lines.servingStop(id));
  }

  @ApiPost("/lines")
  public ResponseEntity<LineResponse> create(@Valid @RequestBody LineRequest input) {
    var line = lines.create(input);
    return ResponseEntity.created(URI.create("/api/v1/lines/" + line.id())).body(line);
  }

  @ApiPut(value = "/lines/{id}", description = "Replaces the data of an existing line.")
  public LineResponse update(@PathVariable @Min(1) long id, @Valid @RequestBody LineRequest input) {
    return lines.update(id, input);
  }

  @ApiDelete("/lines/{id}")
  public void delete(@PathVariable @Min(1) long id) {
    lines.delete(id);
  }
}
