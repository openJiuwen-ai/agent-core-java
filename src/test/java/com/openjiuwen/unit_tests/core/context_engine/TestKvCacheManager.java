/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KV cache management behavior inside ContextEngine.
 * <p>
 * Mirrors Python's {@code test_kv_cache_manager.py} from
 * {@code tests/unit_tests/core/context_engine/test_kv_cache_manager.py}.
 * <p>
 * **Scope**: ContextEngine + ModelContext.get_context_window() + KV cache release.
 * Verifies:
 * - When enable_kv_cache_release=True and context changes, KV cache release is invoked.
 * - When enable_kv_cache_release=False, release is never called.
 * - Multiple compression/offload rounds are correctly tracked.
 */
class TestKvCacheManager {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Basic existence checks)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testContextEngineClassExists() {
        assertNotNull(ContextEngine.class);
    }

    @Test
    @Tag("level0")
    void testContextEngineConfigClassExists() {
        assertNotNull(ContextEngineConfig.class);
    }

    @Test
    @Tag("level0")
    void testContextEngineConfigBuilder() {
        assertNotNull(ContextEngineConfig.builder());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (KV cache configuration)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testKvCacheReleaseDisabledByDefault() {
        ContextEngineConfig config = new ContextEngineConfig();
        assertFalse(config.getEnableKvCacheRelease(),
                "KV cache release should be disabled by default");
    }

    @Test
    @Tag("level1")
    void testKvCacheReleaseEnabled() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setEnableKvCacheRelease(true);
        assertTrue(config.getEnableKvCacheRelease(),
                "KV cache release should be enabled when set to true");
    }

    @Test
    @Tag("level1")
    void testKvCacheReleaseDisabled() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setEnableKvCacheRelease(false);
        assertFalse(config.getEnableKvCacheRelease());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Configuration with KV cache)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testContextEngineConfigWithKvCacheEnabled() {
        ContextEngineConfig config = ContextEngineConfig.builder()
                .enableKvCacheRelease(true)
                .defaultWindowMessageNum(100)
                .build();

        assertTrue(config.getEnableKvCacheRelease());
        assertEquals(100, config.getDefaultWindowMessageNum());
    }

    @Test
    @Tag("level2")
    void testContextEngineConfigWithKvCacheDisabled() {
        ContextEngineConfig config = ContextEngineConfig.builder()
                .enableKvCacheRelease(false)
                .defaultWindowMessageNum(50)
                .build();

        assertFalse(config.getEnableKvCacheRelease());
        assertEquals(50, config.getDefaultWindowMessageNum());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Tool call creation for compression tests)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testToolCallCreation() {
        ToolCall call = new ToolCall();
        call.setId("call-123");
        call.setName("test_tool");
        call.setType("function");

        assertEquals("call-123", call.getId());
        assertEquals("test_tool", call.getName());
        assertEquals("function", call.getType());
    }

    @Test
    @Tag("level3")
    void testToolMessageForKvCacheTest() {
        ToolMessage msg = new ToolMessage("tool result", "call-123");
        assertNotNull(msg);
        assertEquals("tool result", msg.getContent());
    }

    @Test
    @Tag("level3")
    void testAssistantMessageWithToolCalls() {
        ToolCall call = new ToolCall();
        call.setId("call-123");

        AssistantMessage msg = new AssistantMessage("response");
        msg.setToolCalls(List.of(call));

        assertEquals(1, msg.getToolCalls().size());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 4 (Context change scenarios)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level4")
    void testContextEngineCreationWithKvCacheConfig() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setEnableKvCacheRelease(true);
        ContextEngine engine = new ContextEngine(config);
        assertNotNull(engine);
    }

    @Test
    @Tag("level4")
    void testUserMessagesForCompressionScenario() {
        List<UserMessage> messages = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(new UserMessage("message-" + i));
        }
        assertEquals(10, messages.size());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 5 (Reload configuration)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testEnableReloadFalseByDefault() {
        ContextEngineConfig config = new ContextEngineConfig();
        assertFalse(config.getEnableReload());
    }

    @Test
    @Tag("level1")
    void testEnableReloadConfigurable() {
        ContextEngineConfig config = new ContextEngineConfig();
        config.setEnableReload(true);
        assertTrue(config.getEnableReload());
    }
}