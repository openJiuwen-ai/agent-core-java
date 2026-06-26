/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.BaseAgent;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.rail.InvokeInputs;
import com.openjiuwen.core.single_agent.rail.ModelCallInputs;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_rl_online_rail.py} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/online/test_rl_online_rail.py}.
 */
class RLOnlineRailTest {

    @TempDir
    Path tempDir;

    @Test
    void beforeInvokeEnablesTokenCapture() {
        CollectingUploader uploader = new CollectingUploader(tempDir.resolve("wal-before"));
        RLOnlineRail rail = new RLOnlineRail("s1", "http://gateway.local", "user-1", uploader);
        MockAgent agent = new MockAgent();
        AgentCallbackContext ctx = new AgentCallbackContext(agent);

        rail.onBeforeInvoke(ctx);

        Config config = agent.react_agent.config;
        assertThat(config.llm_return_token_ids).isTrue();
        assertThat(config.llm_logprobs).isTrue();
        assertThat(config.llm_top_logprobs).isEqualTo(1);
        assertThat(config.custom_headers).containsEntry("x-user-id", "user-1");
    }

    @Test
    void safeRunEvolutionUploadsBatch() {
        CollectingUploader uploader = new CollectingUploader(tempDir.resolve("wal-safe"));
        RLOnlineRail rail = new RLOnlineRail("s1", "http://gateway.local", "user-1", uploader);
        Trajectory trajectory = Trajectory.builder()
                .executionId("traj-1")
                .sessionId("s1")
                .source("rl_online")
                .steps(List.of(llmStep("hi", "hello")))
                .build();

        rail.safeRunEvolution(Map.of("trajectory", trajectory)).toCompletableFuture().join();

        assertThat(uploader.batches).hasSize(1);
        assertThat(uploader.batches.getFirst().getTenantId()).isEqualTo("user-1");
        assertThat(uploader.batches.getFirst().getSamples().getFirst().getResponseText()).isEqualTo("hello");
    }

    @Test
    void keepsOneInvokePerUploadedBatch() {
        CollectingUploader uploader = new CollectingUploader(tempDir.resolve("wal-boundary"));
        RLOnlineRail rail = new RLOnlineRail("s1", "http://gateway.local", "user-1", uploader);
        MockAgent agent = new MockAgent();

        InvokeInputs firstInvoke = invokeInputs("same-session", "q1");
        rail.beforeInvoke(context(agent, firstInvoke)).toCompletableFuture().join();
        rail.afterModelCall(context(agent, modelInputs("q1", "a1"))).toCompletableFuture().join();
        rail.afterInvoke(context(agent, firstInvoke)).toCompletableFuture().join();

        InvokeInputs secondInvoke = invokeInputs("same-session", "q2");
        rail.beforeInvoke(context(agent, secondInvoke)).toCompletableFuture().join();
        rail.afterModelCall(context(agent, modelInputs("q2", "a2"))).toCompletableFuture().join();
        rail.afterInvoke(context(agent, secondInvoke)).toCompletableFuture().join();

        assertThat(uploader.batches).hasSize(2);
        assertThat(uploader.batches).extracting(batch -> batch.getSamples().size()).containsExactly(1, 1);
        assertThat(uploader.batches.get(1).getSamples().getFirst().getResponseText()).isEqualTo("a2");
    }

    @Test
    void uploadsFullSingleInvokeBatch() {
        CollectingUploader uploader = new CollectingUploader(tempDir.resolve("wal-full"));
        RLOnlineRail rail = new RLOnlineRail("s1", "http://gateway.local", "user-1", uploader);
        MockAgent agent = new MockAgent();

        InvokeInputs invoke = invokeInputs("same-session", "q");
        rail.beforeInvoke(context(agent, invoke)).toCompletableFuture().join();
        for (int index = 0; index < 201; index++) {
            rail.afterModelCall(context(agent, modelInputs("q" + index, "a" + index)))
                    .toCompletableFuture()
                    .join();
        }
        rail.afterInvoke(context(agent, invoke)).toCompletableFuture().join();

        assertThat(uploader.batches).hasSize(1);
        assertThat(uploader.batches.getFirst().getSamples()).hasSize(201);
        assertThat(uploader.batches.getFirst().getSamples().getFirst().getResponseText()).isEqualTo("a0");
    }

    private static AgentCallbackContext context(MockAgent agent, Object inputs) {
        AgentCallbackContext ctx = new AgentCallbackContext(agent);
        ctx.setInputs(inputs);
        return ctx;
    }

    private static InvokeInputs invokeInputs(String conversationId, String query) {
        InvokeInputs inputs = new InvokeInputs();
        inputs.setConversationId(conversationId);
        inputs.setQuery(query);
        return inputs;
    }

    private static ModelCallInputs modelInputs(String userText, String assistantText) {
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setMessages(List.of(Map.of("role", "user", "content", userText)));
        inputs.setResponse(Map.of("role", "assistant", "content", assistantText));
        return inputs;
    }

    private static TrajectoryStep llmStep(String userText, String assistantText) {
        LLMCallDetail detail = LLMCallDetail.builder()
                .model("m1")
                .messages(List.of(Map.of("role", "user", "content", userText)))
                .response(Map.of("role", "assistant", "content", assistantText))
                .build();
        return TrajectoryStep.builder().kind("llm").detail(detail).build();
    }

    private static final class CollectingUploader extends TrajectoryUploader {
        private final List<RailV1Batch> batches = new ArrayList<>();

        private CollectingUploader(Path walDir) {
            super("http://gateway.local", 8, 0, 0.0d, walDir, "",
                    (url, payload, headers) -> new ResponseSnapshot(202, ""), false);
        }

        @Override
        public CompletableFuture<Void> enqueue(Object batch) {
            batches.add((RailV1Batch) batch);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class MockAgent extends BaseAgent {
        @SuppressWarnings("checkstyle:MemberName")
        public final ReactAgent react_agent = new ReactAgent();

        private MockAgent() {
            super(new AgentCard("agent-1", "agent-1", "Agent"));
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            return List.of().iterator();
        }
    }

    private static final class ReactAgent {
        private final Config config = new Config();
    }

    @SuppressWarnings("checkstyle:MemberName")
    private static final class Config {
        private boolean llm_return_token_ids;
        private boolean llm_logprobs;
        private int llm_top_logprobs;
        private Map<String, Object> custom_headers;

        public void configure_custom_headers(Map<String, Object> headers) {
            custom_headers = new LinkedHashMap<>(headers);
        }
    }
}
