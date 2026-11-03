package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code openjiuwen.core.common.utils.header_utils} in
 * {@code openjiuwen/core/common/utils/header_utils.py}.
 */
class HeaderUtilsTest {

    @Test
    void returnsEmptyMapForNullOrEmptyInput() {
        assertThat(HeaderUtils.sanitizeHeaders(null)).isEmpty();
        assertThat(HeaderUtils.sanitizeHeaders(Map.of())).isEmpty();
    }

    @Test
    void dropsProtectedAndBlankEntriesButPreservesStringifiedValues() {
        Map<Object, Object> headers = new LinkedHashMap<>();
        headers.put(" X-Test ", 123);
        headers.put("authorization", "secret");
        headers.put("host", "gateway.local");
        headers.put("", "bad");
        headers.put("blank", "   ");
        headers.put("nullable", null);
        headers.put(null, "value");
        headers.put("X-Trace", true);

        Map<String, String> sanitized = HeaderUtils.sanitizeHeaders(headers);

        assertThat(sanitized).containsExactly(
                Map.entry("X-Test", "123"),
                Map.entry("X-Trace", "true")
        );
    }
}
