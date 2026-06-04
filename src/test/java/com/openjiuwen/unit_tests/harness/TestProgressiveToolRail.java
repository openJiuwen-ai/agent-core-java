/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.harness.rails.ProgressiveToolRail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for progressive-tool filtering before model calls.
 *
 * <p>Mirrors Python's {@code test_progressive_tool_rail} in
 * {@code tests.unit_tests.harness.test_progressive_tool_rail}.
 */
class TestProgressiveToolRail {

    @Test
    @Tag("level0")
    @DisplayName("beforeModelCall updates prompt builder and keeps only visible tools")
    void testBeforeModelCallUpdatesBuilderAndFiltersTools() {
        ProgressiveToolRail rail = new ProgressiveToolRail(Set.of(), Set.of("always_tool"), 10);
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        builder.addSection(new PromptSection(
                "identity",
                Map.of("cn", "Base system prompt.", "en", "Base system prompt.")
        ));

        List<Object> previewMessages = new ArrayList<>();
        previewMessages.add("preview prompt");
        ModelCallInputs inputs = ModelCallInputs.builder()
                .messages(previewMessages)
                .tools(List.of(
                        tool("search_tools"),
                        tool("always_tool"),
                        tool("loaded_tool"),
                        tool("hidden_tool")
                ))
                .build();

        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(new FakeAgent(builder))
                .inputs(inputs)
                .session(new FakeSession(Map.of("__progressive_visible_tool_names__", List.of("loaded_tool"))))
                .build();

        rail.beforeModelCall(ctx);

        String prompt = builder.build();
        assertTrue(prompt.contains("Base system prompt."));
        assertTrue(prompt.contains("search_tools"));
        assertTrue(prompt.contains("load_tools"));
        assertEquals("preview prompt", inputs.getMessages().get(0));
        assertEquals(
                List.of("search_tools", "always_tool", "loaded_tool"),
                inputs.getTools().stream().map(ToolInfo::getName).toList()
        );
    }

    private static ToolInfo tool(String name) {
        return ToolInfo.builder()
                .name(name)
                .description(name + " description")
                .build();
    }

    private static final class FakeAgent {
        private final SystemPromptBuilder systemPromptBuilder;

        private FakeAgent(SystemPromptBuilder systemPromptBuilder) {
            this.systemPromptBuilder = systemPromptBuilder;
        }

        public SystemPromptBuilder getSystemPromptBuilder() {
            return systemPromptBuilder;
        }
    }

    private static final class FakeSession implements Session {
        private final Map<String, Object> state = new LinkedHashMap<>();

        private FakeSession(Map<String, Object> seed) {
            if (seed != null) {
                state.putAll(seed);
            }
        }

        @Override
        public String getSessionId() {
            return "fake-session";
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> updates) {
            if (updates != null) {
                state.putAll(updates);
            }
        }
    }
}
