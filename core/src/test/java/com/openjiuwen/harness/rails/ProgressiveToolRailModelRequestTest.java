/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentConfig;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Progressive tool disclosure must reach the real model request, not only the callback context.
 *
 * @since 2026-10-20
 */
class ProgressiveToolRailModelRequestTest {
    @Test
    void testNativeBeforeModelCallFiltersModelRequestTools() {
        ProgressiveToolRail rail = new ProgressiveToolRail(config());
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setTools(tools("read_file", "bash", "web_search"));
        AgentCallbackContext context = new AgentCallbackContext();
        context.setEvent(AgentCallbackEvent.BEFORE_MODEL_CALL);
        context.setInputs(inputs);

        rail.getCallbacks().get(AgentCallbackEvent.BEFORE_MODEL_CALL)
                .handle(context).toCompletableFuture().join();

        assertThat(inputs.getTools()).extracting(ProgressiveToolRailModelRequestTest::toolName)
                .containsExactly("read_file");
    }

    @Test
    void testNativeBeforeModelCallKeepsSessionLoadedTools() {
        ProgressiveToolRail rail = new ProgressiveToolRail(config());
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setTools(tools("read_file", "bash", "web_search"));
        AgentCallbackContext context = new AgentCallbackContext();
        context.setEvent(AgentCallbackEvent.BEFORE_MODEL_CALL);
        context.setInputs(inputs);
        context.setSession(new MemorySession(Map.of(ProgressiveToolRail.VISIBLE_TOOLS_KEY, List.of("web_search"))));

        rail.getCallbacks().get(AgentCallbackEvent.BEFORE_MODEL_CALL)
                .handle(context).toCompletableFuture().join();

        assertThat(inputs.getTools()).extracting(ProgressiveToolRailModelRequestTest::toolName)
                .containsExactly("read_file", "web_search");
        assertThat(context.getExtra()).doesNotContainKey("session");
    }

    @Test
    void testInitAddsSectionsToReactAgentPrompt() {
        DeepAgent agent = new DeepAgent(new AgentCard("deep", "deep", "test"));
        agent.configure(new DeepAgentConfig());
        ProgressiveToolRail rail = new ProgressiveToolRail(config());

        rail.init(agent);
        // ReActAgent clears transient sections at the start of every inner invocation.
        reactAgent(agent).getSystemPromptBuilder().clearTransient();
        String prompt = reactAgent(agent).getSystemPromptBuilder().build();
        assertThat(prompt).contains("search_tools").contains("load_tools");

        rail.uninit(agent);
        assertThat(reactAgent(agent).getSystemPromptBuilder().build())
                .doesNotContain("load_tools");
    }

    private static String toolName(Object tool) {
        if (tool instanceof ToolInfo info) {
            return info.getName();
        }
        throw new IllegalStateException("ToolInfo expected but got " + tool.getClass().getName());
    }

    private static ReActAgent reactAgent(DeepAgent agent) {
        if (agent.reactAgent() instanceof ReActAgent reactAgent) {
            return reactAgent;
        }
        throw new IllegalStateException("ReActAgent expected");
    }

    private static DeepAgentConfig config() {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setProgressiveToolEnabled(true);
        config.setProgressiveToolAlwaysVisibleTools(List.of("read_file"));
        config.setProgressiveToolDefaultVisibleTools(List.of());
        return config;
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
            // stream output is not observed by this test
        }

        @Override
        public Iterator<Object> streamIterator() {
            return List.<Object>of().iterator();
        }
    }

    private static List<Object> tools(String... names) {
        List<Object> tools = new ArrayList<>();
        for (String name : names) {
            ToolInfo info = new ToolInfo();
            info.setName(name);
            tools.add(info);
        }
        return tools;
    }
}
