/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.core.foundation.tool.test_validation} in
 * {@code tests/unit_tests/core/foundation/tool/test_validation.py}.
 */
class AuthHeaderAndQueryProviderValidationPythonParityTest {

    @Test
    void testAuthHeaderAndQueryProvider() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer test_token");
        headers.put("X-Custom-Header", "test_value");
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("api_key", "test_key");
        queryParams.put("version", "v1");

        AuthHeaderAndQueryProvider provider = new AuthHeaderAndQueryProvider(headers, queryParams);
        HttpRequest request = provider.apply(
                        HttpRequest.newBuilder(),
                        URI.create("https://api.example.com/users"))
                .GET()
                .build();

        assertEquals("Bearer test_token", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("test_value", request.headers().firstValue("X-Custom-Header").orElseThrow());
        assertTrue(request.uri().toString().contains("api_key=test_key"));
        assertTrue(request.uri().toString().contains("version=v1"));
    }
}
