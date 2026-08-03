/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool.http;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for HTTP workflow tool component exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.workflow.components.tool.http} in
 * {@code openjiuwen/core/workflow/components/tool/http/__init__.py}.</p>
 */
public final class HttpToolPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/workflow/components/tool/http/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "HTTPRequestComponent",
            "HttpComponentConfig",
            "HttpRequestParamConfig",
            "HttpAuthConfig",
            "HttpRequestBodyConfig",
            "HttpResponseHandlingConfig",
            "HttpAdvancedOptionsConfig",
            "HttpRetryConfig",
            "HttpRateLimitConfig",
            "HttpAuthType",
            "HttpContentType",
            "HttpResponseFormat"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private HttpToolPackage() {
    }

    public static boolean exports(String symbol) {
        return EXPORTED_SYMBOLS.contains(symbol);
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("HTTPRequestComponent", HTTPRequestComponent.class);
        exports.put("HttpComponentConfig", HttpComponentConfig.class);
        exports.put("HttpRequestParamConfig", HttpRequestParamConfig.class);
        exports.put("HttpAuthConfig", HttpAuthConfig.class);
        exports.put("HttpRequestBodyConfig", HttpRequestBodyConfig.class);
        exports.put("HttpResponseHandlingConfig", HttpResponseHandlingConfig.class);
        exports.put("HttpAdvancedOptionsConfig", HttpAdvancedOptionsConfig.class);
        exports.put("HttpRetryConfig", HttpRetryConfig.class);
        exports.put("HttpRateLimitConfig", HttpRateLimitConfig.class);
        exports.put("HttpAuthType", HttpAuthType.class);
        exports.put("HttpContentType", HttpContentType.class);
        exports.put("HttpResponseFormat", HttpResponseFormat.class);
        return Map.copyOf(exports);
    }
}
