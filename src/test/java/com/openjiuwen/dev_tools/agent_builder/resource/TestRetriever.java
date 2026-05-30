/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test resource retriever functionality.
 * <p>
 * Mirrors Python's {@code test_retriever.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/resource/test_retriever.py}.
 *
 */
class TestRetriever {

    /**
     * Test ResourceRetriever initialization.
     * <p>
     * Mirrors Python's {@code TestResourceRetrieverInit} class.
     */
    @Nested
    class TestInit {

        @Test
        void testInitSuccess() {
            Object llm = new Object();
            ResourceRetriever retriever = new ResourceRetriever(llm);

            assertSame(llm, retriever.getLlm());
            assertNotNull(retriever.getPluginDict());
            assertNotNull(retriever.getToolPluginIdMap());
        }

        @Test
        void testInitWithPlugins() {
            Object llm = new Object();
            ResourceRetriever retriever = new ResourceRetriever(llm, List.of(Map.of(
                    "plugin_id", "plugin_1",
                    "plugin_name", "Plugin 1",
                    "tools", List.of(Map.of("tool_id", "tool_1", "tool_name", "Tool 1")))));

            assertEquals(Map.of("tool_1", "plugin_1"), retriever.getToolPluginIdMap());
            assertTrue(retriever.getPluginDict().containsKey("plugin_1"));
        }
    }

    /**
     * Test ResourceRetriever.loadResources method.
     * <p>
     * Mirrors Python's {@code TestResourceRetrieverLoadResources} class.
     */
    @Nested
    class TestLoadResources {

        @Test
        void testLoadResourcesDefaultPath() {
            assertNotNull(ResourceRetriever.loadResources());
        }

        @Test
        void testLoadResourcesFileNotFound() {
            assertEquals(List.of(), ResourceRetriever.loadResources("missing/plugins.json"));
        }
    }
}
