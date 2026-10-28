/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deepagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs native interrupts through the real DeepAgent executor, scheduler and event queue.
 *
 * @since 0.1.17
 */
@Timeout(30)
class DeepAgentTaskLoopInterruptTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void streamInterruptCompletesAndCheckpointCanResume(int count) throws Exception {
        String sessionId = UUID.randomUUID().toString();
        var card = new AgentCard("interrupt-" + sessionId, "interrupt-test", "interrupt regression");
        var config = DeepAgentConfig.builder().enableTaskLoop(true).enableTaskPlanning(false)
                .completionTimeout(1.0D).build();
        try (var agent = new DeepAgent(card, config, null)) {
            agent.setReactAgent(new InterruptingAgent(card, count), true);
            var initial = new AgentSession(sessionId, null, card);
            List<Object> outputs = collect(agent.stream(Map.of("query", "start", "conversation_id", sessionId),
                    initial, List.of(StreamMode.OUTPUT)));
            assertThat(agent.getEventHandler().getLastResult()).containsEntry("result_type", "interrupt")
                    .doesNotContainKey("error");
            assertThat(assertInstanceOf(List.class, agent.getEventHandler().getLastResult().get("state")))
                    .hasSize(count);
            assertThat(interactions(outputs)).hasSize(count);

            var resume = new InteractiveInput();
            resume.update("confirmation", "continue");
            var restored = new AgentSession(sessionId, null, card);
            List<Object> resumed = collect(agent.stream(Map.of("query", resume, "conversation_id", sessionId),
                    restored, List.of(StreamMode.OUTPUT)));
            assertThat(interactions(resumed)).hasSize(1);
            assertThat(restored.getState("resume_count")).isEqualTo(1);
            verifyFinalResume(agent, card, sessionId, resume);
        } finally {
            CheckpointerFactory.getCheckpointer().release(sessionId);
        }
    }

    private static void verifyFinalResume(DeepAgent agent, AgentCard card, String sessionId,
            InteractiveInput resume) throws Exception {
        var restored = new AgentSession(sessionId, null, card);
        List<Object> outputs = collect(agent.stream(Map.of("query", resume, "conversation_id", sessionId),
                restored, List.of(StreamMode.OUTPUT)));
        assertThat(interactions(outputs)).isEmpty();
        assertThat(restored.getState("completed")).isEqualTo(true);
        assertThat(outputs).anySatisfy(item -> {
            OutputSchema output = assertInstanceOf(OutputSchema.class, item);
            assertThat(output.getPayload().toString()).contains("resumed");
        });
    }

    private static List<Object> collect(Iterator<Object> stream) throws Exception {
        try {
            List<Object> outputs = new ArrayList<>();
            stream.forEachRemaining(outputs::add);
            return outputs;
        } finally {
            if (stream instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    private static List<OutputSchema> interactions(List<Object> outputs) {
        return outputs.stream().filter(OutputSchema.class::isInstance).map(OutputSchema.class::cast)
                .filter(output -> "__interaction__".equals(output.getType())).toList();
    }

    private static final class InterruptingAgent extends ReActAgent {
        private final int count;

        private InterruptingAgent(AgentCard card, int count) {
            super(card);
            this.count = count;
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            Object query = assertInstanceOf(Map.class, inputs).get("query");
            if (query instanceof InteractiveInput) {
                assertThat(session.getState("pending")).isEqualTo(count);
                if (session.getState("resume_count") == null) {
                    session.updateState(Map.of("resume_count", 1));
                    return List.<Object>of(new OutputSchema("__interaction__", 0,
                            new InteractionOutput("second-confirmation", "Confirm again"))).iterator();
                }
                session.updateState(Map.of("completed", true));
                return List.<Object>of(new OutputSchema("answer", 0,
                        Map.of("output", "resumed", "result_type", "answer"))).iterator();
            }
            session.updateState(Map.of("pending", count));
            List<Object> outputs = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                outputs.add(new OutputSchema("__interaction__", index,
                        new InteractionOutput("call-" + index, "Confirm " + index)));
            }
            return outputs.iterator();
        }
    }
}
