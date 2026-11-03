/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.offload.api.OffloadCatalogImpl;
import com.openjiuwen.core.context.offload.api.OffloadService;
import com.openjiuwen.core.context.offload.api.OffloadServiceImpl;
import com.openjiuwen.core.context.offload.api.OffloadStore;
import com.openjiuwen.core.context.offload.cleanup.SessionOffloadGate;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadConfig;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.store.CompositeOffloadStore;
import com.openjiuwen.core.context.offload.store.MemoryOffloadStore;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.schema.OffloadMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TC_121: every message in a mixed sequence is classified by exactly one
 * protection condition (recent window / protected tool / offload-done flag /
 * size) and the marker decision agrees with that classification.
 */
class MessageOffloaderProtectionInvariantTest {
    private static final OffloadNamespace NS = OffloadNamespace.of("t1", "s1", "c1");
    private static final String LONG = "L".repeat(200);

    @Test
    void mixedSequenceMessagesClassifyExclusivelyAndMarkerAgrees() {
        MessageOffloaderConfig config = v2Config(10, 5, 0);
        config.setProtectedToolNames(List.of("reload_original_context_messages", "read:secret/*"));
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));

        ToolMessage flagged = new ToolMessage(LONG, "tc-flag");
        flagged.setMetadata(new LinkedHashMap<>(Map.of(
                MessageOffloader.OFFLOAD_DONE_METADATA_KEY, "true")));
        ToolMessage patternHit = new ToolMessage(LONG, "tc-pattern");

        context.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage(LONG, "tc-plain"),                                  // unprotected -> offload
                new ToolMessage("tiny", "tc-small"),                                // below size -> inline
                assistantToolCall("tc-reload", "reload_original_context_messages", "{}"),
                new ToolMessage(LONG, "tc-reload"),                                 // exact-name protected -> inline
                assistantToolCall("tc-pattern", "read", "{\"path\":\"secret/a.txt\"}"),
                patternHit,                                                          // pattern protected -> inline
                flagged                                                              // offload-done flag -> inline
        )).toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result).hasSize(8);
        assertMarkerState(result.get(1), true, "unprotected long tool must be offloaded");
        assertMarkerState(result.get(2), false, "below-threshold message must stay inline");
        assertMarkerState(result.get(4), false, "exact-name protected tool must stay inline");
        assertMarkerState(result.get(6), false, "pattern-protected tool must stay inline");
        assertMarkerState(result.get(7), false, "offload-done flagged message must stay inline");
    }

    @Test
    void windowProtectionBeatsEverythingAndSlidesRecoverability() {
        // D7: with N=3 the newest three messages stay inline regardless of
        // other conditions; a later add slides the window and the old long
        // tool becomes offloadable (滑动窗口溢出恢复).
        MessageOffloaderConfig config = v2Config(10, 5, 3);
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));

        context.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage(LONG, "tc-old")
        )).toCompletableFuture().join();
        assertThat(context.getMessages()).noneMatch(OffloadMessage.class::isInstance);

        context.addMessages(List.of(
                new UserMessage("u2"),
                new UserMessage("u3"),
                new ToolMessage(LONG, "tc-new")
        )).toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result).hasSize(5);
        assertMarkerState(result.get(1), true, "slid-out long tool must be offloaded");
        assertMarkerState(result.get(4), false, "newest tool must stay inside the protection window");
    }

    @Test
    void noMessageMatchesTwoProtectionConditionsSimultaneously() {
        // Invariant probe: the offload-done flag and the protected-tool list
        // classify the same message identically (inline); the marker decision
        // must stay deterministic across repeated trigger scans.
        MessageOffloaderConfig config = v2Config(10, 5, 0);
        config.setProtectedToolNames(List.of("reload_original_context_messages"));
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));

        context.addMessages(List.of(
                new UserMessage("u"),
                assistantToolCall("tc-reload-a", "reload_original_context_messages", "{}"),
                new ToolMessage(LONG, "tc-reload-a"),
                assistantToolCall("tc-reload-b", "reload_original_context_messages", "{}"),
                new ToolMessage(LONG, "tc-reload-b")
        )).toCompletableFuture().join();
        List<BaseMessage> first = context.getMessages();
        assertThat(first.get(2)).isNotInstanceOf(OffloadMessage.class);
        assertThat(first.get(4)).isNotInstanceOf(OffloadMessage.class);

        context.addMessages(List.of(new UserMessage("u2"))).toCompletableFuture().join();
        List<BaseMessage> second = context.getMessages();
        assertThat(second.get(2)).isNotInstanceOf(OffloadMessage.class);
        assertThat(second.get(4)).isNotInstanceOf(OffloadMessage.class);
    }

    @Test
    void defaultProtectedListOnlyShieldsReloadToolWithoutDeclaration() {
        // TC_113-UT: 不显式声明名单时默认名单仅含 reload_original_context_messages——
        // reload 工具长结果滑出窗口后原文保留，名单外工具正常卸载（默认名单行为钉桩）。
        MessageOffloaderConfig config = v2Config(10, 5, 2);
        // 不 setProtectedToolNames：走源码默认 List.of("reload_original_context_messages")。
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));

        context.addMessages(List.of(
                new UserMessage("u"),
                assistantToolCall("tc-web", "web_search", "{}"),
                new ToolMessage(LONG, "tc-web"),                     // 默认名单外 → 卸载
                assistantToolCall("tc-reload", "reload_original_context_messages", "{}"),
                new ToolMessage(LONG, "tc-reload")                    // 默认名单内 → 原文保留
        )).toCompletableFuture().join();

        context.addMessages(List.of(new UserMessage("u2"), new UserMessage("u3")))
                .toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result.get(2)).isInstanceOf(OffloadMessage.class);
        assertThat(result.get(4)).isNotInstanceOf(OffloadMessage.class);
        assertThat(result.get(4).getContentAsString()).isEqualTo(LONG);
    }

    @Test
    void protectionWindowIntersectsWithKeepTailAndReslices() {
        // TC_114-UT: 保护窗口 × messagesToKeep 交叠——保留区间内消息不被卸，
        // 补消息后保留区间随总数重划、旧保留消息恢复可卸（区间动态推移）。
        MessageOffloaderConfig config = v2Config(10, 5, 3);
        config.setMessagesToKeep(6);
        config.setKeepLastRound(false);
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));

        context.addMessages(List.of(
                new ToolMessage("T1".repeat(25), "tc-t1"),
                new ToolMessage("T2".repeat(25), "tc-t2"),
                new ToolMessage("T3".repeat(25), "tc-t3"),
                new ToolMessage("T4".repeat(25), "tc-t4"),
                new ToolMessage("T5".repeat(25), "tc-t5"),
                new ToolMessage("T6".repeat(25), "tc-t6"),
                new ToolMessage("T7".repeat(25), "tc-t7"),
                new ToolMessage("T8".repeat(25), "tc-t8")
        )).toCompletableFuture().join();

        // 总数 8：offloadRange = 8-6 = 2 → 卸 [0..1]（T1、T2）；T3~T5 已滑出窗口 3
        // 但在保留区间内原文保留；T6~T8（窗口内）保护。
        List<BaseMessage> first = context.getMessages();
        assertThat(first.get(0)).isInstanceOf(OffloadMessage.class);
        assertThat(first.get(1)).isInstanceOf(OffloadMessage.class);
        assertThat(first.get(2)).isNotInstanceOf(OffloadMessage.class);
        assertThat(first.get(2).getContentAsString()).isEqualTo("T3".repeat(25));
        assertThat(first.get(4)).isNotInstanceOf(OffloadMessage.class);

        // 补 2 条至总数 10：offloadRange 重划为 10-6 = 4 → T3、T4 恢复可卸，T5 起保留。
        context.addMessages(List.of(new UserMessage("u1"), new UserMessage("u2")))
                .toCompletableFuture().join();
        List<BaseMessage> second = context.getMessages();
        assertThat(second.get(2)).isInstanceOf(OffloadMessage.class);
        assertThat(second.get(3)).isInstanceOf(OffloadMessage.class);
        assertThat(second.get(4)).isNotInstanceOf(OffloadMessage.class);
        assertThat(second.get(4).getContentAsString()).isEqualTo("T5".repeat(25));
    }

    @Test
    void ghostToolNameDeclarationHasNoSideEffect() {
        // TC_119-UT: 显式声明从未被调用的幽灵工具名——配置不报错、无副作用，
        // 名单外消息按窗口+阈值正常卸载不受拖累。
        MessageOffloaderConfig config = v2Config(10, 5, 2);
        config.setProtectedToolNames(List.of("ghost_tool_never_called"));
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));

        context.addMessages(List.of(
                new UserMessage("u"),
                assistantToolCall("tc-w1", "web_search", "{}"),
                new ToolMessage(LONG, "tc-w1")                       // 幽灵名不命中任何消息 → 正常卸载
        )).toCompletableFuture().join();
        assertThat(context.getMessages()).noneMatch(OffloadMessage.class::isInstance);

        // 补 2 条使大结果滑出窗口：幽灵名不产生任何干扰，照常出 marker。
        context.addMessages(List.of(new UserMessage("u2"), new UserMessage("u3")))
                .toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result.get(2)).isInstanceOf(OffloadMessage.class);
        assertThat(result.get(2).getContentAsString())
                .startsWith("LLLLL" + MessageOffloader.OMIT_STRING)
                .contains("[[OFFLOAD: handle=");
    }

    @Test
    void fourQuadrantOverlapDecisionInOneContext() {
        // TC_126-UT: 同一上下文四象限交叠整合——窗口外×超阈值×非保护（卸）、
        // 窗口内×超阈值（保护）、窗口外×未超阈值（inline）、窗口外×超阈值×保护工具（保护）。
        MessageOffloaderConfig config = v2Config(10, 5, 3);
        config.setProtectedToolNames(List.of("reload_original_context_messages", "doc_fetch"));
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));

        context.addMessages(List.of(
                assistantToolCall("tc-w1", "web_search", "{}"),
                new ToolMessage("W1".repeat(25), "tc-w1"),            // 窗口外×超阈值×非保护 → 卸
                new ToolMessage("tiny", "tc-s1"),                     // 窗口外×未超阈值 → inline
                assistantToolCall("tc-d1", "doc_fetch", "{}"),
                new ToolMessage("D1".repeat(25), "tc-d1"),            // 窗口外×超阈值×保护 → 原文
                new ToolMessage("W2".repeat(25), "tc-w2")             // 窗口内×超阈值 → 原文
        )).toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result).hasSize(6);
        assertThat(result.get(1)).isInstanceOf(OffloadMessage.class);
        assertThat(result.get(2)).isNotInstanceOf(OffloadMessage.class);
        assertThat(result.get(2).getContentAsString()).isEqualTo("tiny");
        assertThat(result.get(4)).isNotInstanceOf(OffloadMessage.class);
        assertThat(result.get(4).getContentAsString()).isEqualTo("D1".repeat(25));
        assertThat(result.get(5)).isNotInstanceOf(OffloadMessage.class);
        assertThat(result.get(5).getContentAsString()).isEqualTo("W2".repeat(25));
    }

    private static void assertMarkerState(BaseMessage message, boolean expectMarker, String hint) {
        if (expectMarker) {
            assertThat(message).isInstanceOf(OffloadMessage.class);
        } else {
            assertThat(message).isNotInstanceOf(OffloadMessage.class);
        }
    }

    private static AssistantMessage assistantToolCall(String id, String name, String arguments) {
        return AssistantMessage.builder()
                .role("assistant")
                .content("a")
                .toolCalls(List.of(ToolCall.builder()
                        .id(id)
                        .name(name)
                        .type("function")
                        .arguments(arguments)
                        .build()))
                .build();
    }

    private static MessageOffloaderConfig v2Config(int tokensThreshold, int trimSize,
                                                   int recentMessageProtectionCount) {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setTokensThreshold(tokensThreshold);
        config.setLargeMessageThreshold(10);
        config.setTrimSize(trimSize);
        config.setOffloadMessageType(List.of("tool"));
        config.setKeepLastRound(false);
        config.setRecentMessageProtectionCount(recentMessageProtectionCount);
        return config;
    }

    private static SessionModelContext v2Context(MessageOffloaderConfig config, OffloadService service,
                                                 ModelContext.TokenCounterPort tokenCounter) {
        MessageOffloader offloader = new MessageOffloader(config);
        SessionModelContext context = new SessionModelContext("ctx-tc121", "session-tc121", new ContextEngineConfig(),
                List.of(), List.of(offloader), tokenCounter);
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
}
