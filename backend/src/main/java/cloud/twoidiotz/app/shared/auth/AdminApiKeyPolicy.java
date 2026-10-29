package cloud.twoidiotz.app.shared.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/** Reads stay public; every request that can change data needs the admin key as a Bearer token. */
@Component
public class AdminApiKeyPolicy {
  public static final String SCHEME = "Bearer";
  private static final Set<HttpMethod> PUBLIC =
      Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

  private final byte[] key;

  public AdminApiKeyPolicy(@Value("${app.admin-api-key}") String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalStateException("app.admin-api-key must not be blank");
    }
    this.key = key.getBytes(StandardCharsets.UTF_8);
  }

  public static boolean requiresAdminKey(HttpMethod method) {
    return !PUBLIC.contains(method);
  }

  public boolean authorizes(String authorization) {
    if (authorization == null) return false;
    var parts = authorization.trim().split("\\s+", 2);
    if (parts.length != 2 || !parts[0].equalsIgnoreCase(SCHEME)) return false;
    return MessageDigest.isEqual(key, parts[1].getBytes(StandardCharsets.UTF_8));
  }
}
