/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies how {@link ContextEngine} merges processor configuration and
 * history presets when a pooled context is requested again.
 */
class ContextProcessorCacheTest {

    private ContextEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.clearContext();
        }
    }

    private static SessionModelContext.ContextProcessorPort countingProcessor(AtomicInteger counter) {
        return new SessionModelContext.ContextProcessorPort() {
            @Override
            public String processorType() {
                return "CountingProcessor";
            }

            @Override
            public CompletionStage<Boolean> triggerGetContextWindow(SessionModelContext context,
                    com.openjiuwen.core.context.ContextWindow window,
                    java.util.Map<String, Object> kwargs) {
                counter.incrementAndGet();
                return CompletableFuture.completedFuture(false);
            }
        };
    }

    private static List<ContextEngine.ProcessorSpec> specs(String type) {
        return List.of(new ContextEngine.ProcessorSpec(type, null));
    }

    @Test
    @DisplayName("Processors passed on a cached lookup install onto the pooled context")
    void cachedContextInstallsProcessorsPassedOnSecondCreate() {
        engine = new ContextEngine(new ContextEngineConfig());
        engine.registerProcessor("CountingProcessor", cfg -> countingProcessor(new AtomicInteger()));
        // First call seeds history without processors, like a multi-turn service preset.
        engine.createContext("ctx", (ContextEngine.SessionPort) () -> "sess-a", null,
                List.of(new UserMessage("old q"), new AssistantMessage("old a")), null);
        // Second call carries the agent's processors and hits the pooled instance.
        SessionModelContext context = (SessionModelContext) engine.createContext("ctx",
                (ContextEngine.SessionPort) () -> "sess-a", specs("CountingProcessor"), null, null);

        assertEquals(1, context.getProcessors().size());
        assertEquals("CountingProcessor", context.getProcessors().get(0).processorType());
    }

    @Test
    @DisplayName("Installed processors on a seeded context react to window builds")
    void processorsInstalledOnCacheHitTriggerOnGetContextWindow() throws Exception {
        engine = new ContextEngine(new ContextEngineConfig());
        AtomicInteger windowCalls = new AtomicInteger();
        engine.registerProcessor("CountingProcessor", cfg -> countingProcessor(windowCalls));
        engine.createContext("ctx", (ContextEngine.SessionPort) () -> "sess-b", null,
                List.of(new UserMessage("old q")), null);

        SessionModelContext context = (SessionModelContext) engine.createContext("ctx",
                (ContextEngine.SessionPort) () -> "sess-b", specs("CountingProcessor"), null, null);
        context.getContextWindow(null, null, null, null, java.util.Map.of()).toCompletableFuture().join();

        assertTrue(windowCalls.get() >= 1, "late-installed processor should observe window builds");
    }

    @Test
    @DisplayName("Mismatched processors on a cached lookup keep the installed ones")
    void mismatchedProcessorsKeepInstalledInstances() {
        engine = new ContextEngine(new ContextEngineConfig());
        engine.registerProcessor("CountingProcessor", cfg -> countingProcessor(new AtomicInteger()));
        SessionModelContext context = (SessionModelContext) engine.createContext("ctx",
                (ContextEngine.SessionPort) () -> "sess-c", specs("CountingProcessor"), null, null);
        SessionModelContext.ContextProcessorPort original = context.getProcessors().get(0);

        engine.registerProcessor("OffloaderProcessor", cfg -> countingProcessor(new AtomicInteger()));
        SessionModelContext again = (SessionModelContext) engine.createContext("ctx",
                (ContextEngine.SessionPort) () -> "sess-c", specs("OffloaderProcessor"), null, null);

        assertEquals(1, again.getProcessors().size());
        assertEquals(original, again.getProcessors().get(0),
                "existing processors must be preserved to keep internal state");
    }

    @Test
    @DisplayName("History preset on a cached empty context seeds its messages")
    void historyPresetSeedsEmptyCachedContext() {
        engine = new ContextEngine(new ContextEngineConfig());
        engine.createContext("ctx", (ContextEngine.SessionPort) () -> "sess-d", specs("CountingProcessor"),
                null, null);
        SessionModelContext context = (SessionModelContext) engine.createContext("ctx",
                (ContextEngine.SessionPort) () -> "sess-d", null,
                List.of(new UserMessage("old q"), new AssistantMessage("old a")), null);

        List<BaseMessage> messages = context.getMessages();
        assertEquals(2, messages.size());
        assertEquals("old q", messages.get(0).getContentAsString());
    }

    @Test
    @DisplayName("History preset on a non-empty cached context is ignored with a warning")
    void historyPresetOnNonEmptyCachedContextIsIgnored() {
        engine = new ContextEngine(new ContextEngineConfig());
        engine.createContext("ctx", (ContextEngine.SessionPort) () -> "sess-e", null,
                List.of(new UserMessage("first")), null);
        SessionModelContext context = (SessionModelContext) engine.createContext("ctx",
                (ContextEngine.SessionPort) () -> "sess-e", null,
                List.of(new UserMessage("second"), new AssistantMessage("reply")), null);

        List<BaseMessage> messages = context.getMessages();
        assertEquals(1, messages.size());
        assertEquals("first", messages.get(0).getContentAsString());
    }

    @Test
    @DisplayName("Context created with processors keeps them for later windows")
    void processorsAttachWhenFirstCreateCarriesThem() {
        engine = new ContextEngine(new ContextEngineConfig());
        engine.registerProcessor("CountingProcessor", cfg -> countingProcessor(new AtomicInteger()));

        SessionModelContext context = (SessionModelContext) engine.createContext("ctx",
                (ContextEngine.SessionPort) () -> "sess-f", specs("CountingProcessor"), null, null);

        assertEquals(1, context.getProcessors().size());
        assertNotNull(context.getProcessors().get(0));
        assertEquals("CountingProcessor", context.getProcessors().get(0).processorType());
    }
}
