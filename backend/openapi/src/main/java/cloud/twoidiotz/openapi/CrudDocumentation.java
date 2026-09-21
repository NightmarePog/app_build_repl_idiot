package cloud.twoidiotz.openapi;

import java.util.Locale;

/** Default wording for the standard resource operations. */
record CrudDocumentation(String operationId, String summary, String status, String response) {
  static CrudDocumentation list(String plural) {
    var label = plural.toLowerCase(Locale.ROOT);
    return new CrudDocumentation("get" + plural, "List all " + label, "200", "List of " + label);
  }

  static CrudDocumentation get(String resource) {
    return new CrudDocumentation(
        "get" + resource,
        "Get " + resource.toLowerCase(Locale.ROOT) + " detail",
        "200",
        resource + " detail");
  }

  static CrudDocumentation create(String resource) {
    return mutation("create", "Create a new ", "201", "created", resource);
  }

  static CrudDocumentation update(String resource) {
    return mutation("update", "Update a ", "200", "updated", resource);
  }

  static CrudDocumentation patch(String resource) {
    return mutation("patch", "Partially update a ", "200", "updated", resource);
  }

  static CrudDocumentation delete(String resource) {
    return mutation("delete", "Delete a ", "204", "deleted", resource);
  }

  private static CrudDocumentation mutation(
      String verb, String summary, String status, String result, String resource) {
    return new CrudDocumentation(
        verb + resource,
        summary + resource.toLowerCase(Locale.ROOT),
        status,
        resource + " " + result + " successfully");
  }
}
