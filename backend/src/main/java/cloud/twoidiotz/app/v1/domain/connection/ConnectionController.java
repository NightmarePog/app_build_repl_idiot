package cloud.twoidiotz.app.v1.domain.connection;

import cloud.twoidiotz.app.shared.api.ApiGet;
import cloud.twoidiotz.app.shared.error.ApiError;
import cloud.twoidiotz.app.v1.domain.trip.Clock;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Journey planner (vyhledání spojení). Parameters are read as text so every malformed value gets
 * a specific `{error}` message instead of a generic binding failure.
 */
@RestController
@RequiredArgsConstructor
public class ConnectionController {
  static final int DEFAULT_LIMIT = 5;
  static final int MAX_LIMIT = 20;
  private static final ZoneId ZONE = ZoneId.of("Europe/Prague");

  private final ConnectionService connections;

  @Tag(name = "Connections")
  @ApiResponse(
      responseCode = "400",
      description =
          "Invalid query: `Missing from stop ID`, `Invalid from stop ID`, `Missing to stop ID`,"
              + " `Invalid to stop ID`, `From and to must differ`, `Invalid time`, `Invalid mode`"
              + " or `Invalid limit`.",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The origin or destination stop does not exist (`Stop not found`).",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiGet(
      value = "/connections",
      operationId = "searchConnections",
      summary = "Search connections between two stops",
      description =
          "Plans journeys from `from` to `to` over the daily timetable with the Connection Scan"
              + " Algorithm, including changes between buses at the same stop (at least"
              + " `min_transfer_minutes`, staying on the same bus needs no time). With"
              + " `mode=departure` it returns the first `limit` connections leaving at or after"
              + " `time`; with `mode=arrival` the last `limit` connections arriving at or before"
              + " `time`. Only Pareto-optimal connections are returned: none leaves earlier,"
              + " arrives later and changes more often than another. The search looks up to 24"
              + " hours ahead (or back) and wraps past midnight; day offsets count from the search"
              + " day. Connections are ordered by departure. No connection gives an empty array.")
  public ResponseEntity<ConnectionSearchResponse> search(
      @Parameter(
              in = ParameterIn.QUERY,
              required = true,
              description = "Origin stop id.",
              schema = @Schema(type = "integer", format = "int64", minimum = "1", example = "2"))
          @RequestParam(required = false)
          String from,
      @Parameter(
              in = ParameterIn.QUERY,
              required = true,
              description = "Destination stop id; must differ from `from`.",
              schema = @Schema(type = "integer", format = "int64", minimum = "1", example = "11"))
          @RequestParam(required = false)
          String to,
      @Parameter(
              in = ParameterIn.QUERY,
              description = "Time of day `HH:MM`; defaults to the current time in Europe/Prague.",
              schema = @Schema(type = "string", pattern = Clock.PATTERN, example = "07:00"))
          @RequestParam(required = false)
          String time,
      @Parameter(
              in = ParameterIn.QUERY,
              description = "`departure` (leave at or after `time`, default) or `arrival`.",
              schema =
                  @Schema(
                      type = "string",
                      allowableValues = {"departure", "arrival"},
                      defaultValue = "departure"))
          @RequestParam(required = false)
          String mode,
      @Parameter(
              in = ParameterIn.QUERY,
              description = "Maximum number of connections, 1 to 20.",
              schema =
                  @Schema(
                      type = "integer",
                      minimum = "1",
                      maximum = "20",
                      defaultValue = "5"))
          @RequestParam(required = false)
          String limit) {
    var fromId = id(from, "from");
    var toId = id(to, "to");
    if (fromId == toId) throw new InvalidConnectionQueryException("From and to must differ");
    var body =
        connections.search(fromId, toId, minute(time), mode(mode), limit(limit));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }

  private static long id(String value, String name) {
    if (value == null || value.isEmpty())
      throw new InvalidConnectionQueryException("Missing " + name + " stop ID");
    if (!value.matches("^[1-9][0-9]{0,17}$"))
      throw new InvalidConnectionQueryException("Invalid " + name + " stop ID");
    return Long.parseLong(value);
  }

  private static int minute(String value) {
    if (value == null || value.isEmpty()) {
      var now = LocalTime.now(ZONE);
      return now.getHour() * 60 + now.getMinute();
    }
    if (!value.matches(Clock.PATTERN)) throw new InvalidConnectionQueryException("Invalid time");
    return Integer.parseInt(value.substring(0, 2)) * 60 + Integer.parseInt(value.substring(3));
  }

  private static SearchMode mode(String value) {
    if (value == null || value.isEmpty()) return SearchMode.departure;
    return switch (value) {
      case "departure" -> SearchMode.departure;
      case "arrival" -> SearchMode.arrival;
      default -> throw new InvalidConnectionQueryException("Invalid mode");
    };
  }

  private static int limit(String value) {
    if (value == null || value.isEmpty()) return DEFAULT_LIMIT;
    if (!value.matches("^[1-9][0-9]?$") || Integer.parseInt(value) > MAX_LIMIT)
      throw new InvalidConnectionQueryException("Invalid limit");
    return Integer.parseInt(value);
  }
}
