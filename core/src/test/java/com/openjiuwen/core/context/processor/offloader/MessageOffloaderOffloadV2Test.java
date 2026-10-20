/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.offload.api.OffloadCatalogImpl;
import com.openjiuwen.core.context.offload.api.OffloadErrorCode;
import com.openjiuwen.core.context.offload.api.OffloadException;
import com.openjiuwen.core.context.offload.api.OffloadService;
import com.openjiuwen.core.context.offload.api.OffloadServiceImpl;
import com.openjiuwen.core.context.offload.api.OffloadStore;
import com.openjiuwen.core.context.offload.cleanup.SessionOffloadGate;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadConfig;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadPayload;
import com.openjiuwen.core.context.offload.store.CompositeOffloadStore;
import com.openjiuwen.core.context.offload.store.MemoryOffloadStore;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.schema.OffloadMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v2 pipeline unit tests for {@link MessageOffloader} (架构文档 §2.2 / §7.2.1):
 * single-message threshold boundary (AC-1-03 边界=阈值即卸), the D7 recent-window
 * protection (含滑动窗口溢出恢复), the {@code openjiuwen.offload_done} anti-recursion
 * flag (共享约定 5) and the full zero-LLM offload/reload flow through the v2
 * {@link OffloadService} MEMORY backend.
 */
class MessageOffloaderOffloadV2Test {
    private static final OffloadNamespace NS = OffloadNamespace.of("t1", "u1", "s1", "c1");
    private static final String DONE_FLAG = MessageOffloader.OFFLOAD_DONE_METADATA_KEY + "=true";

    @Test
    void messageAtExactThresholdIsOffloadedAndBelowStaysInline() {
        // AC-1-03: content length == large_message_threshold qualifies (>=), 29 < 30 stays.
        MessageOffloaderConfig config = v2Config(30, 5, 0);
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));
        String below = "x".repeat(29);
        String atBoundary = "x".repeat(30);

        context.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage(below, "tc-lo"),
                new ToolMessage(atBoundary, "tc-hi")
        )).toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result.get(1)).isInstanceOf(ToolMessage.class);
        assertThat(result.get(1).getContentAsString()).isEqualTo(below);
        BaseMessage markerMessage = result.get(2);
        assertThat(markerMessage).isInstanceOf(OffloadMessage.class);
        assertThat(markerMessage.getContentAsString()).startsWith("xxxxx" + MessageOffloader.OMIT_STRING);
        assertThat(markerMessage.getContentAsString()).contains("[[OFFLOAD: handle=");
        // G.TYP.13: guard the cast with an instanceof check before downcasting
        OffloadMessage marker = markerMessage instanceof OffloadMessage om ? om : null;

        // Payload lives in the v2 store chain: reload returns the original content.
        Object rawReloader = context.reloaderTool();
        // G.TYP.13: guard the cast with an instanceof check before downcasting
        SessionModelContext.ReloaderTool reloader =
                rawReloader instanceof SessionModelContext.ReloaderTool rt ? rt : null;
        assertThat(reloader.reloadOriginalContextMessages(marker.getOffloadHandle(), marker.getOffloadType()))
                .contains(atBoundary);
    }

    @Test
    void recentProtectionWindowKeepsNewestMessagesInlineUntilWindowSlides() {
        // D7 保护窗口: with N=2 and a 3-message context everything stays inline;
        // after one more message arrives the oldest tool message becomes offloadable
        // while the newest one stays protected (滑动窗口溢出恢复).
        MessageOffloaderConfig config = v2Config(10, 5, 2);
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));

        context.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage("A".repeat(50), "tc-1"),
                new ToolMessage("B".repeat(50), "tc-2")
        )).toCompletableFuture().join();
        assertThat(context.getMessages()).noneMatch(OffloadMessage.class::isInstance);

        context.addMessages(List.of(new UserMessage("u4"))).toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result.get(1)).isInstanceOf(OffloadMessage.class);
        assertThat(result.get(2)).isInstanceOf(ToolMessage.class).isNotInstanceOf(OffloadMessage.class);
        assertThat(result.get(2).getContentAsString()).isEqualTo("B".repeat(50));
    }

    @Test
    void offloadDoneMetadataBlocksReOffloading() {
        // 共享约定 5: the flag value is the string "true" (Boolean.TRUE is honored too).
        MessageOffloaderConfig config = v2Config(10, 5, 0);
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));
        ToolMessage stringFlag = new ToolMessage("S".repeat(50), "tc-str");
        stringFlag.setMetadata(new LinkedHashMap<>(Map.of(
                MessageOffloader.OFFLOAD_DONE_METADATA_KEY, "true")));
        ToolMessage booleanFlag = new ToolMessage("B".repeat(50), "tc-bool");
        booleanFlag.setMetadata(new LinkedHashMap<>(Map.of(
                MessageOffloader.OFFLOAD_DONE_METADATA_KEY, Boolean.TRUE)));

        context.addMessages(List.of(new UserMessage("u"), stringFlag, booleanFlag))
                .toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result).noneMatch(OffloadMessage.class::isInstance);
        assertThat(result.get(1).getContentAsString()).isEqualTo("S".repeat(50));
        assertThat(result.get(2).getContentAsString()).isEqualTo("B".repeat(50));
    }

    @Test
    void zeroLlmFlowPublishesViaV2AndReloadOutputCarriesDoneFlag() {
        MessageOffloaderConfig config = v2Config(10, 5, 0);
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));
        String longContent = "zero-llm-payload-".repeat(4);

        context.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage(longContent, "tc-zero")
        )).toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result.get(1)).isInstanceOf(OffloadMessage.class);
        // G.TYP.13: guard the cast with an instanceof check before downcasting
        OffloadMessage marker = result.get(1) instanceof OffloadMessage om ? om : null;
        // Production wiring shape: both the context and the processor share one
        // OffloadService + one namespace supplier.
        assertThat(context.getOffloadService()).isSameAs(service);

        Object rawReloader = context.reloaderTool();
        // G.TYP.13: guard the cast with an instanceof check before downcasting
        SessionModelContext.ReloaderTool reloader =
                rawReloader instanceof SessionModelContext.ReloaderTool rt ? rt : null;
        String reloadOutput = reloader.reloadOriginalContextMessages(
                marker.getOffloadHandle(), marker.getOffloadType());
        assertThat(reloadOutput).contains(longContent);
        assertThat(reloadOutput).contains(DONE_FLAG);
    }

    @Test
    void v2StoreFailureKeepsMessageInline() {
        // 共享约定 13: total store failure → empty save receipt → original stays inline.
        MessageOffloaderConfig config = v2Config(10, 5, 0);
        SessionModelContext context = v2Context(config, failingService(), fixedTokenCounter(100));
        String original = "F".repeat(50);

        context.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage(original, "tc-fail")
        )).toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result.get(1)).isInstanceOf(ToolMessage.class).isNotInstanceOf(OffloadMessage.class);
        assertThat(result.get(1).getContentAsString()).isEqualTo(original);
    }

    private static MessageOffloaderConfig v2Config(int largeMessageThreshold, int trimSize,
                                                   int recentMessageProtectionCount) {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setTokensThreshold(50);
        config.setLargeMessageThreshold(largeMessageThreshold);
        config.setTrimSize(trimSize);
        config.setOffloadMessageType(List.of("tool"));
        config.setKeepLastRound(false);
        config.setRecentMessageProtectionCount(recentMessageProtectionCount);
        return config;
    }

    private static SessionModelContext v2Context(MessageOffloaderConfig config, OffloadService service,
                                                 ModelContext.TokenCounterPort tokenCounter) {
        MessageOffloader offloader = new MessageOffloader(config);
        SessionModelContext context = new SessionModelContext("ctx-v2", "session-v2", new ContextEngineConfig(),
                List.of(), List.of(offloader), tokenCounter);
        // Production wiring (ReActAgent.bindOffloadPipeline): the context and the
        // processor share one OffloadService and one namespace supplier, so the
        // save namespace and the reload namespace always match.
        java.util.function.Supplier<OffloadNamespace> namespaceSupplier = () -> NS;
        offloader.setOffloadService(service, namespaceSupplier);
        context.bindOffloadService(service, namespaceSupplier);
        return context;
    }

    private static ModelContext.TokenCounterPort fixedTokenCounter(long tokens) {
        return messages -> (int) tokens;
    }

    private static OffloadService memoryService() {
        LinkedHashMap<OffloadBackend, OffloadStore> chain = new LinkedHashMap<>();
        chain.put(OffloadBackend.MEMORY, new MemoryOffloadStore());
        return new OffloadServiceImpl(new OffloadCatalogImpl(), new CompositeOffloadStore(chain),
                new SessionOffloadGate(), new OffloadConfig());
    }

    private static OffloadService failingService() {
        OffloadStore failingStore = new OffloadStore() {
            @Override
            public OffloadBackend backend() {
                return OffloadBackend.MEMORY;
            }

            @Override
            public void save(OffloadNamespace ns, String handle, OffloadPayload payload,
                             Duration ttl) throws OffloadException {
                throw new OffloadException(OffloadErrorCode.OFFLOAD_STORE_IO, "simulated store outage");
            }

            @Override
            public OffloadPayload load(OffloadNamespace ns, String handle) throws OffloadException {
                throw new OffloadException(OffloadErrorCode.OFFLOAD_MISSING, "simulated store outage");
            }

            @Override
            public boolean delete(OffloadNamespace ns, String handle) {
                return false;
            }

            @Override
            public boolean exists(OffloadNamespace ns, String handle) {
                return false;
            }
        };
        LinkedHashMap<OffloadBackend, OffloadStore> chain = new LinkedHashMap<>();
        chain.put(OffloadBackend.MEMORY, failingStore);
        return new OffloadServiceImpl(new OffloadCatalogImpl(), new CompositeOffloadStore(chain),
                new SessionOffloadGate(), new OffloadConfig());
    }
}
