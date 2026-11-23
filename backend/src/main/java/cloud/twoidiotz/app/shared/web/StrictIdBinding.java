package cloud.twoidiotz.app.shared.web;

import java.beans.PropertyEditorSupport;
import java.util.regex.Pattern;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Path ids are the contract's strict {@code Id} (int64 >= 1). Spring's default number conversion
 * trims whitespace and accepts a leading {@code +}, so {@code /lines/1%20/trips} would read as line
 * 1. This editor accepts only optional minus and digits; anything else fails binding, which the
 * domain handlers turn into 400 "Invalid ... ID". Negative and zero values still parse, so the
 * {@code @Min(1)} check reports them the same way.
 */
@ControllerAdvice
public class StrictIdBinding {
  private static final Pattern INTEGER = Pattern.compile("-?[0-9]+");

  @InitBinder
  void strictLongs(WebDataBinder binder) {
    binder.registerCustomEditor(long.class, new StrictLongEditor());
    binder.registerCustomEditor(Long.class, new StrictLongEditor());
  }

  private static final class StrictLongEditor extends PropertyEditorSupport {
    @Override
    public void setAsText(String text) {
      if (text == null || !INTEGER.matcher(text).matches()) {
        throw new IllegalArgumentException("Not an integer id: " + text);
      }
      setValue(Long.parseLong(text)); // NumberFormatException on overflow is also a mismatch.
    }
  }
}
