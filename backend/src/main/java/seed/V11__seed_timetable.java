package seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;

/** Loads the assignment's trips.json and stop_times.json unchanged. */
public class V11__seed_timetable extends BaseJavaMigration {
  private static final String[] FILES = {"trips.json", "stop_times.json", "insert.sql"};
  private static final String STAGE =
      """
      CREATE TEMP TABLE seed_trip (
          id BIGINT PRIMARY KEY, source_trip_id VARCHAR(50) NOT NULL, route_ref VARCHAR(20) NOT NULL,
          line_ref VARCHAR(20) NOT NULL, direction_id INT NOT NULL
      ) ON COMMIT DROP;
      CREATE TEMP TABLE seed_stop_time (
          trip_id BIGINT NOT NULL, stop_sequence INT NOT NULL, stop_name VARCHAR(255) NOT NULL,
          arrival_time VARCHAR(5) NOT NULL, departure_time VARCHAR(5) NOT NULL,
          PRIMARY KEY (trip_id, stop_sequence)
      ) ON COMMIT DROP;
      """;

  @Override
  public Integer getChecksum() {
    var checksum = new CRC32();
    checksum.update(STAGE.getBytes(StandardCharsets.UTF_8));
    for (var file : FILES) {
      checksum.update(file.getBytes(StandardCharsets.UTF_8));
      checksum.update(text(file).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
    }
    return (int) checksum.getValue();
  }

  @Override
  public void migrate(Context context) throws Exception {
    var connection = context.getConnection();
    var json = new ObjectMapper();
    try (var statement = connection.createStatement()) {
      statement.execute(STAGE);
    }
    try (var insert = connection.prepareStatement("INSERT INTO seed_trip VALUES (?, ?, ?, ?, ?)")) {
      for (JsonNode trip : json.readTree(text("trips.json")).get("trips")) {
        insert.setLong(1, required(trip, "id").longValue());
        insert.setString(2, required(trip, "source_trip_id").textValue());
        insert.setString(3, required(trip, "route_ref").textValue());
        insert.setString(4, required(trip, "line_ref").textValue());
        insert.setInt(5, required(trip, "direction_id").intValue());
        insert.addBatch();
      }
      insert.executeBatch();
    }
    try (var insert =
        connection.prepareStatement("INSERT INTO seed_stop_time VALUES (?, ?, ?, ?, ?)")) {
      for (JsonNode row : json.readTree(text("stop_times.json")).get("stop_times")) {
        insert.setLong(1, required(row, "trip_id").longValue());
        insert.setInt(2, required(row, "stop_sequence").intValue());
        insert.setString(3, required(row, "stop_name").textValue());
        insert.setString(4, required(row, "arrival_time").textValue());
        insert.setString(5, required(row, "departure_time").textValue());
        insert.addBatch();
      }
      insert.executeBatch();
    }
    try (var statement = connection.createStatement()) {
      statement.execute(text("insert.sql"));
    }
  }

  /** A field the seed needs: present, not null, and of the expected kind (number or text). */
  private static JsonNode required(JsonNode row, String field) {
    var value = row.get(field);
    if (value == null || value.isNull()) {
      throw new IllegalStateException("Timetable seed row is missing " + field + ": " + row);
    }
    if (value.isTextual() ? value.textValue().isBlank() : !value.isIntegralNumber()) {
      throw new IllegalStateException("Timetable seed row has an invalid " + field + ": " + row);
    }
    return value;
  }

  private static String text(String file) {
    try {
      return new ClassPathResource("seed/v11/" + file).getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot read the V11 seed resource " + file, exception);
    }
  }
}
