package cloud.twoidiotz.app.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** An absolute URI, with a host required for HTTP, HTTPS, and FTP. Null is valid. */
@Documented
@Constraint(validatedBy = AbsoluteUriValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AbsoluteUri {
  String message() default "must be an absolute URI with a valid host for HTTP, HTTPS, or FTP";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
