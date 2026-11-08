package cloud.twoidiotz.app.v1.domain.line;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@Entity(name = "Line")
public class LineModel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String number;
  private String name;
  private String type;
  private String color;

  @OneToMany(mappedBy = "line", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("direction")
  private List<RouteModel> routes = new ArrayList<>();
}
