package cloud.twoidiotz.app.v1.domain.line;

import cloud.twoidiotz.app.v1.domain.station.StationModel;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.ListIndexBase;
import org.hibernate.type.SqlTypes;

@Data
@Entity(name = "Route")
public class RouteModel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "line_id")
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private LineModel line;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  private RouteDirection direction;

  /** Stops in travel order; route_stop.position is 1-based. */
  @ManyToMany
  @JoinTable(
      name = "route_stop",
      joinColumns = @JoinColumn(name = "route_id"),
      inverseJoinColumns = @JoinColumn(name = "stop_id"))
  @OrderColumn(name = "position")
  @ListIndexBase(1)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private List<StationModel> stops = new ArrayList<>();
}
