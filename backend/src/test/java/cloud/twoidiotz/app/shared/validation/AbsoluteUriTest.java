package cloud.twoidiotz.app.shared.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cloud.twoidiotz.app.v1.domain.station.StationRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AbsoluteUriTest {
  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "https://example.com/image.png", "HTTP://localhost/image.png",
            "ftp://example.com/image.png",
        "urn:stop:123", "data:image/png;base64,AAAA", "https://[::1]/image.png"
      })
  void acceptsAbsoluteUrisAndNull(String imageUrl) {
    assertTrue(validator.validate(request(imageUrl)).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "/image.png",
        "images/image.png",
        "https://",
        "https:/image.png",
        "HTTP:image.png",
        "ftp:/image.png",
        "https://bad_host/image.png",
        "https://example.com/a b",
        "https://example.com/%zz"
      })
  void rejectsInvalidUrisOnTheImageProperty(String imageUrl) {
    var violations = validator.validate(request(imageUrl));
    assertEquals(1, violations.size());
    assertEquals("imageUrl", violations.iterator().next().getPropertyPath().toString());
  }

  private StationRequest request(String imageUrl) {
    return new StationRequest("Cejl", imageUrl, false, true, false);
  }
}
