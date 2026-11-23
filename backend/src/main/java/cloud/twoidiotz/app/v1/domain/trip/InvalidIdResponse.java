package cloud.twoidiotz.app.v1.domain.trip;

import cloud.twoidiotz.app.shared.error.ApiError;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The 400 every timetable endpoint shares: its path id is not an integer of at least 1. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ApiResponse(
    responseCode = "400",
    description =
        "Invalid identifier: the path id must be a plain integer of at least 1 (no sign,"
            + " spaces or decimals). The body names the parameter, e.g. `Invalid trip ID`.",
    content = @Content(schema = @Schema(implementation = ApiError.class)))
@interface InvalidIdResponse {}
