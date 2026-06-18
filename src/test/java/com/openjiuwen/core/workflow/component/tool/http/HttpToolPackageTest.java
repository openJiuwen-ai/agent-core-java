/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool.http;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity checks for {@link HttpToolPackage}.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.workflow.components.tool.http} in
 * {@code openjiuwen/core/workflow/components/tool/http/__init__.py}.</p>
 */
class HttpToolPackageTest {

    @Test
    void exportedSymbolsMatchPythonAll() {
        List<String> expected = List.of(
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

        assertEquals(expected, HttpToolPackage.EXPORTED_SYMBOLS);
        assertEquals("openjiuwen/core/workflow/components/tool/http/__init__.py", HttpToolPackage.PYTHON_MODULE);
    }

    @Test
    void exportedTypesPointToHttpComponentClasses() {
        assertTrue(HttpToolPackage.exports("HTTPRequestComponent"));
        assertSame(HTTPRequestComponent.class, HttpToolPackage.EXPORTED_TYPES.get("HTTPRequestComponent"));
        assertSame(HttpComponentConfig.class, HttpToolPackage.EXPORTED_TYPES.get("HttpComponentConfig"));
        assertSame(HttpAuthType.class, HttpToolPackage.EXPORTED_TYPES.get("HttpAuthType"));
        assertSame(HttpContentType.class, HttpToolPackage.EXPORTED_TYPES.get("HttpContentType"));
        assertSame(HttpResponseFormat.class, HttpToolPackage.EXPORTED_TYPES.get("HttpResponseFormat"));
    }
}
