-- Stop images are an app-relative path or an absolute http(s)/ftp URL, the forms the API
-- accepts, so a write that skips the API cannot store e.g. 'javascript:x'.
-- line.type stays free text on purpose: the API accepts any operator-defined type
-- (e.g. 'replacement' for rail replacement buses); only blank is rejected (V7).
ALTER TABLE station
    ADD CONSTRAINT station_image_url_form
        CHECK (image_url IS NULL OR image_url ~ '^(/|(https?|ftp)://)[^[:space:]]+$');
