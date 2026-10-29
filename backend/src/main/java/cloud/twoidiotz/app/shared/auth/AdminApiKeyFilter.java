package cloud.twoidiotz.app.shared.auth;

import cloud.twoidiotz.app.shared.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/** Rejects unauthorized mutations before any controller, validation or database work runs. */
@Component
@RequiredArgsConstructor
public class AdminApiKeyFilter extends OncePerRequestFilter {
  private final AdminApiKeyPolicy policy;
  private final JsonMapper mapper;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !AdminApiKeyPolicy.requiresAdminKey(HttpMethod.valueOf(request.getMethod()));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (policy.authorizes(request.getHeader(HttpHeaders.AUTHORIZATION))) {
      chain.doFilter(request, response);
      return;
    }
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, AdminApiKeyPolicy.SCHEME);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    mapper.writeValue(response.getOutputStream(), new ApiError("Unauthorized"));
  }
}
