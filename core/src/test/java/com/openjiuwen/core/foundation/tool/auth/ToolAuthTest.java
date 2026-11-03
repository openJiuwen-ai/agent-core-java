/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolAuthTest {

    @Test
    void toolAuthConfigKeepsFields() {
        ToolAuthConfig config = new ToolAuthConfig(
                "ssl",
                Map.of("verify_switch_env", "RESTFUL_SSL_VERIFY"),
                "restful_api",
                "tool-1"
        );

        assertEquals("ssl", config.getAuthType());
        assertEquals(Map.of("verify_switch_env", "RESTFUL_SSL_VERIFY"), config.getConfig());
        assertEquals("restful_api", config.getToolType());
        assertEquals("tool-1", config.getToolId());
    }

    @Test
    void toolAuthResultKeepsFields() {
        RuntimeException error = new RuntimeException("boom");
        ToolAuthResult result = new ToolAuthResult(
                true,
                Map.of("headers", Map.of("Authorization", "Bearer token")),
                "ok",
                error
        );

        assertTrue(result.isSuccess());
        assertEquals("ok", result.getMessage());
        assertEquals(Map.of("headers", Map.of("Authorization", "Bearer token")), result.getAuthData());
        assertEquals(error, result.getError());
    }

    @Test
    void toolAuthResultDefaultsMissingMessageToEmptyString() {
        ToolAuthResult result = new ToolAuthResult(false, Map.of(), null, null);

        assertEquals("", result.getMessage());
        assertNull(result.getError());
    }
}
