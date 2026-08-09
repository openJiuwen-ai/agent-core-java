/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the BaseAgent invoke/stream against the concrete ReActAgent path.
 */
class ReActAgentReactiveTest {

    @Test
    void invokeDelegatesThroughConcreteReActAgentInvoke() throws Exception {
        ReActAgent agent = newAgent("reactive-react-invoke");
        Model model = mock(Model.class);
        when(model.supportsKvCacheRelease()).thenReturn(false);
        when(model.buildKvCacheInvokeKwargs(any(), any(Boolean.class))).thenReturn(Map.of());
        when(model.invoke(anyList(), any(ModelInvokeOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        AssistantMessage.builder().content("reactive invoke ok").build()));
        agent.setLlm(model);

        AgentSessionApi session = newSession(agent, "react-invoke-session");
        Object result = agent.invoke(Map.of("query", "hello"), session).toCompletableFuture().join();

        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> output = (Map<?, ?>) result;
        assertThat(output.get("output")).isEqualTo("reactive invoke ok");
        assertThat(output.get("result_type")).isEqualTo("answer");
    }

    @Test
    void streamDelegatesThroughConcreteReActAgentStream() throws Exception {
        ReActAgent agent = newAgent("reactive-react-stream");
        Model model = mock(Model.class);
        when(model.supportsKvCacheRelease()).thenReturn(false);
        when(model.buildKvCacheInvokeKwargs(any(), any(Boolean.class))).thenReturn(Map.of());
        when(model.stream(anyList(), any(ModelInvokeOptions.class)))
                .thenReturn(List.of(
                        AssistantMessageChunk.builder().content("reactive stream ok").build()
                ).iterator());
        agent.setLlm(model);

        AgentSessionApi session = new AgentSession("react-stream-session", null, agent.getCard());

        Iterator<Object> iterator = agent.stream(
                Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT));

        List<Object> collected = new ArrayList<>();
        while (iterator.hasNext()) {
            collected.add(iterator.next());
        }

        assertThat(collected).isNotEmpty();
        boolean hasAnswer = collected.stream()
                .filter(OutputSchema.class::isInstance)
                .map(OutputSchema.class::cast)
                .anyMatch(output -> "answer".equals(output.getType())
                        && String.valueOf(output.getPayload()).contains("reactive stream ok"));
        assertThat(hasAnswer).isTrue();
    }

    private static ReActAgent newAgent(String id) {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id(id)
                .name(id)
                .description(id)
                .build());
        agent.configure(ReActAgentConfig.builder().maxIterations(2).build());
        return agent;
    }

    private static AgentSessionApi newSession(ReActAgent agent, String sessionId) {
        return new AgentSession(sessionId, null, agent.getCard());
    }
}
