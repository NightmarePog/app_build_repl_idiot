package cloud.twoidiotz.app.shared.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Enables CRUD documentation defaults and optionally overrides the controller resource name. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiResource {
  String value() default "";

  /** Override for irregular plurals, for example Person / People. */
  String plural() default "";
}
