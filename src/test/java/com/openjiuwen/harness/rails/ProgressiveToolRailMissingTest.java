/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.prompts.SystemPromptBuilder;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code test_before_model_call_updates_builder_and_keeps_preview_messages_intact} in
 * {@code tests/unit_tests/harness/test_progressive_tool_rail.py}.</p>
 */
class ProgressiveToolRailMissingTest {

    @Test
    void testBeforeModelCallUpdatesBuilderAndKeepsPreviewMessagesIntact() {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setProgressiveToolEnabled(true);
        config.setProgressiveToolAlwaysVisibleTools(List.of("always_tool"));
        config.setLanguage("cn");

        ProgressiveToolRail rail = new ProgressiveToolRail(config);
        rail.seedCachedTools(
                Set.of("search_tools", "load_tools"),
                List.of(
                        tool("always_tool", "Always visible tool"),
                        tool("loaded_tool", "Already loaded tool"),
                        tool("hidden_tool", "Hidden tool")));

        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        builder.addSection(new com.openjiuwen.core.single_agent.prompts.PromptSection(
                "identity",
                Map.of("cn", "Base system prompt.", "en", "Base system prompt."),
                10));
        List<Object> previewMessages = new ArrayList<>(List.of(new SystemMessage("preview prompt")));
        List<Object> tools = new ArrayList<>(List.of(
                tool("search_tools", "Search tool registry"),
                tool("always_tool", "Always visible tool"),
                tool("loaded_tool", "Already loaded tool"),
                tool("hidden_tool", "Hidden tool")));

        CallbackContext ctx = new CallbackContext(null, new LinkedHashMap<>(Map.of(
                "system_prompt_builder", builder,
                "messages", previewMessages,
                "tools", tools,
                "session", new MemorySession(Map.of(
                        ProgressiveToolRail.VISIBLE_TOOLS_KEY,
                        List.of("loaded_tool"))))));

        rail.beforeModelCall(ctx);

        String prompt = builder.build();
        assertThat(prompt).contains("Base system prompt.")
                .contains("always_tool")
                .contains("load_tools");
        assertThat(builder.hasSection(SectionName.TOOL_NAVIGATION)).isTrue();
        assertThat(builder.hasSection(SectionName.PROGRESSIVE_TOOL_RULES)).isTrue();
        assertThat(((SystemMessage) previewMessages.getFirst()).getContent()).isEqualTo("preview prompt");
        assertThat(toolNames((List<?>) ctx.get("tools")))
                .containsExactly("search_tools", "always_tool", "loaded_tool");
    }

    private static ToolInfo tool(String name, String description) {
        return ToolInfo.builder()
                .name(name)
                .description(description)
                .build();
    }

    private static List<String> toolNames(List<?> tools) {
        List<String> names = new ArrayList<>();
        for (Object tool : tools) {
            names.add(((ToolInfo) tool).getName());
        }
        return names;
    }

    private static final class MemorySession implements AgentSessionApi {
        private final Map<String, Object> state = new LinkedHashMap<>();

        private MemorySession(Map<String, Object> state) {
            this.state.putAll(state);
        }

        @Override
        public String getSessionId() {
            return "progressive-tool-session";
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public Iterator<Object> streamIterator() {
            return List.of().iterator();
        }
    }
}
