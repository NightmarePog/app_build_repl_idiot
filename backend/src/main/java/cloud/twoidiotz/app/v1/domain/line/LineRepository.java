package cloud.twoidiotz.app.v1.domain.line;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LineRepository extends JpaRepository<LineModel, Long> {
  /** Loads routes with the line; stops follow in one batch per line (see RouteModel). */
  @Override
  @EntityGraph(attributePaths = "routes")
  Optional<LineModel> findById(Long id);

  @Query(
      """
      SELECT DISTINCT line FROM Line line JOIN line.routes route JOIN route.stops stop
      WHERE stop.id = :stopId ORDER BY line.id
      """)
  List<LineModel> findServingStop(long stopId);
}
