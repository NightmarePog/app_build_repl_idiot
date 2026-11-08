package cloud.twoidiotz.app.v1.domain.line;

import cloud.twoidiotz.app.v1.domain.station.StationNotFoundException;
import cloud.twoidiotz.app.v1.domain.station.StationRepository;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LineService {
  private final LineRepository lines;
  private final StationRepository stations;

  public List<LineResponse> list() {
    return lines.findAll(Sort.by("id")).stream().map(LineService::toResponse).toList();
  }

  public LineDetailResponse get(long id) {
    var line = find(id);
    return new LineDetailResponse(
        line.getId(),
        line.getNumber(),
        line.getName(),
        line.getType(),
        line.getColor(),
        line.getRoutes().stream().map(LineService::toRoute).toList());
  }

  public List<LineResponse> servingStop(long stopId) {
    if (!stations.existsById(stopId)) throw new StationNotFoundException(stopId);
    return lines.findServingStop(stopId).stream().map(LineService::toResponse).toList();
  }

  @Transactional
  public LineResponse create(LineRequest input) {
    var line = new LineModel();
    apply(input, line);
    return toResponse(lines.save(line));
  }

  @Transactional
  public LineResponse update(long id, LineRequest input) {
    var line = find(id);
    apply(input, line);
    return toResponse(line);
  }

  @Transactional
  public void delete(long id) {
    lines.delete(find(id));
  }

  private LineModel find(long id) {
    return lines.findById(id).orElseThrow(() -> new LineNotFoundException(id));
  }

  private static void apply(LineRequest input, LineModel line) {
    line.setNumber(input.number());
    line.setName(input.name());
    line.setType(input.type());
    line.setColor(input.color());
  }

  private static LineResponse toResponse(LineModel line) {
    return new LineResponse(
        line.getId(), line.getNumber(), line.getName(), line.getType(), line.getColor());
  }

  private static RouteResponse toRoute(RouteModel route) {
    // Deleting a stop removes its route_stop row, which leaves a gap (null) in the ordered list.
    var present = route.getStops().stream().filter(Objects::nonNull).toList();
    var stops =
        IntStream.range(0, present.size())
            .mapToObj(
                index -> {
                  var stop = present.get(index);
                  return new RouteStopResponse(stop.getId(), stop.getName(), index + 1);
                })
            .toList();
    var headsign = present.isEmpty() ? route.getDirection().name() : present.getLast().getName();
    return new RouteResponse(route.getId(), headsign, route.getDirection(), stops);
  }
}
