/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.offload.api.OffloadService;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadConfig;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies an unusable offload backend chain degrades the agent to legacy mode instead of failing.
 */
class ReActAgentOffloadPipelineDegradationTest {

    @Test
    void unusableOffloadChainFallsBackToLegacyModeAndKeepsAgentAlive() {
        CountingModelClient client = new CountingModelClient();
        ReActAgent agent = new ReActAgent(new AgentCard("offload-degrade", "test", "desc"));
        ReActAgentConfig config = new ReActAgentConfig();
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        config.setMaxIterations(2);
        config.setContextEngineConfig(ContextEngineConfig.builder()
                .enableOffload(true)
                .offload(OffloadConfig.builder()
                        .primaryBackend(OffloadBackend.REDIS)
                        .fallbackBackend(OffloadBackend.REDIS)
                        .finalFallback(OffloadBackend.REDIS)
                        .build())
                .build());
        agent.configure(config);
        agent.setLlm(new Model(client));

        Object result = agent.invoke(Map.of("query", "hi"), new MemorySessionStub("offload-degrade"));

        assertNull(agent.getOffloadService(),
                "an unusable chain must leave the shared offload service unwired");
        assertEquals("done", ((Map<?, ?>) result).get("output"),
                "the agent loop must keep answering in legacy mode");
        assertTrue(client.invocations.get() > 0, "the legacy loop must still drive the model");

        ModelContext context = agent.getContextEngine()
                .getContext(null, "offload-degrade");
        assertNull(((SessionModelContext) context).getOffloadService(),
                "no offload service may be reachable from the degraded context");
    }

    private static final class CountingModelClient implements Model.ModelClient {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invocations.incrementAndGet();
            return CompletableFuture.completedFuture(new AssistantMessage("done"));
        }
    }

    /** Minimal in-memory session double mirroring the existing parity-test stub. */
    private static final class MemorySessionStub
            implements com.openjiuwen.core.session.AgentSessionApi,
            com.openjiuwen.core.context.ContextEngine.SessionPort {
        private final String sessionId;

        private MemorySessionStub(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> data) {
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
