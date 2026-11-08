package cloud.twoidiotz.app.v1.domain.line;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LineRepository extends JpaRepository<LineModel, Long> {
  @Query(
      """
      SELECT DISTINCT line FROM Line line JOIN line.routes route JOIN route.stops stop
      WHERE stop.id = :stopId ORDER BY line.id
      """)
  List<LineModel> findServingStop(long stopId);
}
