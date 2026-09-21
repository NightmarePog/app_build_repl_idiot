package cloud.twoidiotz.app.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

public class AbsoluteUriValidator implements ConstraintValidator<AbsoluteUri, String> {
  private static final Set<String> HOST_REQUIRED_SCHEMES = Set.of("http", "https", "ftp");

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) return true;

    try {
      var uri = new URI(value);
      return uri.isAbsolute()
          && (!HOST_REQUIRED_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))
              || uri.getHost() != null);
    } catch (URISyntaxException exception) {
      return false;
    }
  }
}
