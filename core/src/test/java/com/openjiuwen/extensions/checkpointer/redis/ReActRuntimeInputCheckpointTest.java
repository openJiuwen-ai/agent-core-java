/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.store.kv.RedisStore;
import com.openjiuwen.harness.task_loop.LoopQueues;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Runtime steering references must remain live without entering Redis checkpoints.
 */
class ReActRuntimeInputCheckpointTest {
    private ReActAgent agent;
    private DeepAgent deepAgent;

    @AfterEach
    void cleanup() {
        if (agent != null) {
            agent.getAgentCallbackManager().clear(null).toCompletableFuture().join();
        }
        if (deepAgent != null) {
            deepAgent.shutdown();
        }
        CheckpointerFactory.releaseDefaultCheckpointer();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invocationPreservesBusinessInputAndLiveSteeringAcrossRedisCheckpoint(boolean streaming) {
        var redis = new RedisCheckpointerStorageTest.FakeRedisClient();
        var checkpointer = new RedisCheckpointer(new RedisStore(redis), Map.of());
        CheckpointerFactory.setDefaultCheckpointer(checkpointer);
        String id = UUID.randomUUID().toString();
        var card = new AgentCard(id, "runtime-checkpoint", "runtime checkpoint regression");
        agent = new ReActAgent(card);
        agent.setLlm(answeringModel());
        var queues = new LoopQueues();
        // An actual process-local value makes serializing a queue snapshot fail as well.
        queues.input().add(new Object());
        agent.registerRail(new AgentRail() {
            @Override
            public void beforeInvoke(AgentCallbackContext context) {
                context.pushSteering("live instruction");
                assertThat(queues.steering()).contains("live instruction");
            }
        }).toCompletableFuture().join();
        Map<String, Object> input = Map.of("query", "remember this", "conversation_id", id,
                "business", Map.of("order", "order-742"), "loop_queues", queues,
                "_steering_queue", queues.steering());
        if (streaming) {
            var session = new AgentSession(id, null, card);
            agent.stream(input, session, List.of(StreamMode.OUTPUT)).forEachRemaining(item -> { });
            // Explicit commit also exposes background stream failures to this test.
            session.commit();
        } else {
            agent.invoke(input, (AgentSession) null);
        }
        var restored = new AgentSession(id, null, card);
        restored.preRun(Map.of());
        List<?> saved = (List<?>) restored.getInner().state().get(Constant.INTERACTIVE_INPUT);
        assertThat(saved).hasSize(1);
        Map<?, ?> savedInput = (Map<?, ?>) saved.get(0);
        assertThat(savedInput.get("query")).isEqualTo("remember this");
        assertThat(savedInput.get("business")).isEqualTo(Map.of("order", "order-742"));
        assertThat(savedInput.containsKey("loop_queues")).isFalse();
        assertThat(savedInput.containsKey("_steering_queue")).isFalse();
        assertThat(input.get("loop_queues")).isSameAs(queues);
        assertThat(input.get("_steering_queue")).isSameAs(queues.steering());
    }

    @Test
    @Timeout(20)
    void deepAgentTaskLoopStreamsAndRestoresBusinessInputWithRedis() {
        var redis = new RedisCheckpointerStorageTest.FakeRedisClient();
        CheckpointerFactory.setDefaultCheckpointer(new RedisCheckpointer(new RedisStore(redis), Map.of()));
        String id = UUID.randomUUID().toString();
        var card = new AgentCard(id, "deep-runtime-checkpoint", "DeepAgent checkpoint regression");
        deepAgent = new DeepAgent(card, DeepAgentConfig.builder().enableTaskLoop(true)
                .enableTaskPlanning(false).build(), null);
        deepAgent.getAgent().setLlm(answeringModel());
        deepAgent.stream(Map.of("query", "remember this", "conversation_id", id), null,
                List.of(StreamMode.OUTPUT)).forEachRemaining(item -> { });

        var restored = new AgentSession(id, null, card);
        restored.preRun(Map.of());
        Object saved = restored.getInner().state().get(Constant.INTERACTIVE_INPUT);
        assertThat(saved).isInstanceOf(List.class);
        assertThat(((Map<?, ?>) ((List<?>) saved).get(0)).get("query")).isEqualTo("remember this");
        assertThat(deepAgent.getEventHandler().getLastResult()).containsEntry("result_type", "answer")
                .doesNotContainKey("error");
        restored.commit();
    }

    @Test
    void unrelatedNonSerializableBusinessInputStillFailsCheckpoint() {
        var redis = new RedisCheckpointerStorageTest.FakeRedisClient();
        CheckpointerFactory.setDefaultCheckpointer(new RedisCheckpointer(new RedisStore(redis), Map.of()));
        var card = new AgentCard("invalid-business", "invalid-business", "invalid business input");
        var session = new AgentSession(UUID.randomUUID().toString(), null, card);
        session.preRun(Map.of("inputs", Map.of("business", new Object())));

        assertThatThrownBy(session::commit).hasRootCauseInstanceOf(java.io.NotSerializableException.class);
    }

    private static Model answeringModel() {
        return new Model(new Model.ModelClient() {
            @Override
            public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
                return CompletableFuture.completedFuture(new AssistantMessage("done"));
            }

            @Override
            public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
                return List.of(AssistantMessageChunk.builder().content("done").build()).iterator();
            }
        });
    }

}
