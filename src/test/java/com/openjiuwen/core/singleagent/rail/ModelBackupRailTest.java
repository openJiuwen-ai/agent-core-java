/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused tests for model backup rail behavior.
 *
 * <p>Mirrors Python's {@code ModelBackupRail} in
 * {@code openjiuwen/core/single_agent/rail/model_backup.py}.</p>
 */
class ModelBackupRailTest {

    @Test
    void swapsBackupModelsInOrderAndRequestsRetry() {
        Model first = model("first");
        Model second = model("second");
        ModelBackupRail rail = new ModelBackupRail(List.of(first, second));
        TestAgent agent = new TestAgent();
        AgentCallbackContext context = new AgentCallbackContext(agent);

        rail.onModelException(context).toCompletableFuture().join();
        RetryRequest firstRetry = context.consumeRetryRequest();
        rail.onModelException(context).toCompletableFuture().join();
        RetryRequest secondRetry = context.consumeRetryRequest();
        rail.onModelException(context).toCompletableFuture().join();

        assertThat(agent.getLlm()).isSameAs(second);
        assertThat(firstRetry).isNotNull();
        assertThat(secondRetry).isNotNull();
        assertThat(context.consumeRetryRequest()).isNull();
        assertThat(rail.getIndex()).isEqualTo(2);
        assertThat(rail.getBackupModels()).containsExactly(first, second);
    }

    @Test
    void leavesContextUntouchedWhenNoBackupModelExists() {
        ModelBackupRail rail = new ModelBackupRail(List.of());
        AgentCallbackContext context = new AgentCallbackContext(new TestAgent());

        rail.onModelException(context).toCompletableFuture().join();

        assertThat(context.consumeRetryRequest()).isNull();
        assertThat(rail.getIndex()).isZero();
    }

    private static Model model(String value) {
        return new Model(new RecordingClient(value));
    }

    private static final class TestAgent extends BaseAgent {
        private Model llm;

        private TestAgent() {
            super(new AgentCard("agent", "Agent", ""));
        }

        public void setLlm(Model llm) {
            this.llm = llm;
        }

        public Model getLlm() {
            return llm;
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

    private record RecordingClient(String value) implements Model.ModelClient {
        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            return CompletableFuture.completedFuture(new AssistantMessage(value));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            return List.<AssistantMessageChunk>of().iterator();
        }
    }
}
