/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextEngineConfigTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("defaults mirror the Python config model")
    void testDefaults() {
        ContextEngineConfig config = new ContextEngineConfig();

        assertEquals(3.0, config.getOpenrouterRequestTimeout());
        assertFalse(config.isEnableKvCacheRelease());
        assertFalse(config.isEnableReload());
        assertFalse(config.isEnableTiktokenCounter());
        assertFalse(config.isEnableOpenrouterModelContextWindowTokens());
    }

    @Test
    @DisplayName("positive-only fields reject non-positive values")
    void testPositiveValidation() {
        ContextEngineConfig config = new ContextEngineConfig();

        assertThrows(IllegalArgumentException.class, () -> config.setMaxContextMessageNum(0));
        assertThrows(IllegalArgumentException.class, () -> config.setDefaultWindowMessageNum(-1));
        assertThrows(IllegalArgumentException.class, () -> config.setDefaultWindowRoundNum(0));
        assertThrows(IllegalArgumentException.class, () -> config.setContextWindowTokens(-5));
        assertThrows(IllegalArgumentException.class, () -> config.setOpenrouterRequestTimeout(0));
    }

    @Test
    @DisplayName("serialization keeps snake_case field names")
    void testSerializationAliases() throws Exception {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setMaxContextMessageNum(32);
        config.setDefaultWindowMessageNum(8);
        config.setEnableReload(true);
        config.setEnableOpenrouterModelContextWindowTokens(true);
        config.setModelName("gpt-x");
        config.setModelContextWindowTokens(Map.of("gpt-x", 128000));
        config.setOpenrouterRequestTimeout(4.5);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = MAPPER.readValue(MAPPER.writeValueAsBytes(config), Map.class);
        assertEquals(32, payload.get("max_context_message_num"));
        assertEquals(8, payload.get("default_window_message_num"));
        assertEquals(true, payload.get("enable_reload"));
        assertEquals(true, payload.get("enable_openrouter_model_context_window_tokens"));
        assertEquals("gpt-x", payload.get("model_name"));
        assertEquals(4.5, payload.get("openrouter_request_timeout"));
        assertTrue(payload.containsKey("model_context_window_tokens"));
    }
}
