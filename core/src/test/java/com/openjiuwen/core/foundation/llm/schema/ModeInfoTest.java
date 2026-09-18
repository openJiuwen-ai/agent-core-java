/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's model defaults and alias behavior for
 * {@code openjiuwen/core/foundation/llm/schema/mode_info.py}.
 */
class ModeInfoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testBaseModelInfoDefaults() {
        BaseModelInfo info = new BaseModelInfo();

        assertEquals("", info.getApiKey());
        assertNull(info.getApiBase());
        assertEquals("", info.getModelName());
        assertEquals(0.95d, info.getTemperature());
        assertEquals(0.1d, info.getTopP());
        assertFalse(info.isStreaming());
        assertEquals(60, info.getTimeout());
        assertNull(info.getCustomHeaders());
        assertTrue(info.getExtraFields().isEmpty());
    }

    @Test
    void testDeserializesAliasesAndExtraFields() throws Exception {
        String json = """
                {
                  "api_key": "key",
                  "api_base": "https://example.test",
                  "model": "gpt-demo",
                  "stream": true,
                  "timeout": 120,
                  "custom_headers": {"X-Test": "1"},
                  "vendor": "demo"
                }
                """;

        BaseModelInfo info = MAPPER.readValue(json, BaseModelInfo.class);

        assertEquals("key", info.getApiKey());
        assertEquals("https://example.test", info.getApiBase());
        assertEquals("gpt-demo", info.getModelName());
        assertTrue(info.isStreaming());
        assertEquals(120, info.getTimeout());
        assertNotNull(info.getCustomHeaders());
        assertEquals("1", info.getCustomHeaders().get("X-Test"));
        assertEquals("demo", info.getExtraFields().get("vendor"));
    }

    @Test
    void testModelConfigDefaultsModelInfo() {
        ModelConfig config = new ModelConfig();
        assertNotNull(config.getModelInfo());
    }

    @Test
    void testModelConfigRoundTrip() throws Exception {
        String json = """
                {
                  "modelProvider": "openai",
                  "modelInfo": {
                    "api_base": "https://example.test",
                    "model": "gpt-4o-mini"
                  }
                }
                """;

        ModelConfig config = MAPPER.readValue(json, ModelConfig.class);

        assertEquals("openai", config.getModelProvider());
        assertNotNull(config.getModelInfo());
        assertEquals("https://example.test", config.getModelInfo().getApiBase());
        assertEquals("gpt-4o-mini", config.getModelInfo().getModelName());
    }
}
