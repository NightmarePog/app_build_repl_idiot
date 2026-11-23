package cloud.twoidiotz.app.shared.web;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Turns stored paths into the absolute URIs the public contract exposes. */
public final class PublicUrls {
  private PublicUrls() {}

  // Seeded images are stored as local paths; the public contract exposes absolute URIs.
  public static String imageUrl(String imageUrl) {
    if (imageUrl == null || !imageUrl.startsWith("/")) return imageUrl;
    return ServletUriComponentsBuilder.fromCurrentRequestUri()
        .replacePath(imageUrl)
        .replaceQuery(null)
        .build()
        .toUriString();
  }
}
