/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the BaseAgent reactive wrapper against the concrete ReActAgent path.
 */
class ReActAgentReactiveTest {

    @Test
    void invokeAsyncDelegatesThroughConcreteReActAgentInvoke() throws Exception {
        ReActAgent agent = newAgent("reactive-react-invoke");
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AssistantMessage.builder().content("reactive invoke ok").build());
        agent.setLlm(model);

        StepVerifier.create(agent.invokeAsync(Map.of("query", "hello"), newSession(agent, "react-invoke-session")))
                .assertNext(result -> {
                    assertThat(result).isInstanceOf(Map.class);
                    Map<?, ?> output = (Map<?, ?>) result;
                    assertThat(output.get("output")).isEqualTo("reactive invoke ok");
                    assertThat(output.get("result_type")).isEqualTo("answer");
                })
                .verifyComplete();
    }

    @Test
    void streamAsyncDelegatesThroughConcreteReActAgentStream() throws Exception {
        ReActAgent agent = newAgent("reactive-react-stream");
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AssistantMessage.builder().content("reactive stream ok").build());
        agent.setLlm(model);

        AgentSessionApi session = new AgentSessionApi(
                "react-stream-session",
                null,
                agent.getCard(),
                List.of(StreamMode.OUTPUT));

        StepVerifier.create(agent.streamAsync(Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT)))
                .expectNextMatches(item -> item instanceof OutputSchema output
                        && "answer".equals(output.getType())
                        && String.valueOf(output.getPayload()).contains("reactive stream ok"))
                .verifyComplete();
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
        return new AgentSessionApi(sessionId, null, agent.getCard());
    }
}
