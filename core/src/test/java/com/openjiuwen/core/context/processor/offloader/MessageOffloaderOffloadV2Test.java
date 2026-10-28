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
    private static final OffloadNamespace NS = OffloadNamespace.of("t1", "s1", "c1");
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

        // TC_089: 取回结果作为新消息追加（运行时以携带防重标记的 ToolMessage 落上下文）
        // 且原 marker 消息内容与句柄保持不变。
        ToolMessage reloadResult = new ToolMessage(reloadOutput, "tc-zero-reload");
        reloadResult.setMetadata(new LinkedHashMap<>(Map.of(
                MessageOffloader.OFFLOAD_DONE_METADATA_KEY, "true")));
        String markerContentBefore = context.getMessages().get(1).getContentAsString();
        context.addMessages(List.of(reloadResult)).toCompletableFuture().join();
        List<BaseMessage> afterReload = context.getMessages();
        assertThat(afterReload.size()).isGreaterThanOrEqualTo(3);
        assertThat(afterReload.get(2).getContentAsString()).isEqualTo(reloadOutput);
        assertThat(afterReload.get(2)).isInstanceOf(ToolMessage.class)
                .isNotInstanceOf(OffloadMessage.class);
        BaseMessage originalMarker = afterReload.get(1);
        assertThat(originalMarker).isInstanceOf(OffloadMessage.class);
        assertThat(((OffloadMessage) originalMarker).getOffloadHandle())
                .isEqualTo(marker.getOffloadHandle());
        assertThat(originalMarker.getContentAsString()).isEqualTo(markerContentBefore);
    }

    @Test
    void configMutationHotUpdatesThresholds() {
        // TC_004: 构造后突变 config 对象即热更新生效——同一 offloader 的
        // 单条触发决策按新阈值判定（config 为 final 引用，阈值逐次 live 读取）。
        MessageOffloaderConfig config = v2Config(10, 5, 0);
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));
        context.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage("H".repeat(50), "tc-hot1")
        )).toCompletableFuture().join();
        assertThat(context.getMessages().get(1)).isInstanceOf(OffloadMessage.class);

        // 热更新：把单条触发阈值抬到 1000，50 字符消息不再满足 >= 阈值。
        config.setLargeMessageThreshold(1000);
        context.addMessages(List.of(
                new UserMessage("u2"),
                new ToolMessage("K".repeat(50), "tc-hot2")
        )).toCompletableFuture().join();
        List<BaseMessage> result = context.getMessages();
        assertThat(result.get(result.size() - 1)).isInstanceOf(ToolMessage.class)
                .isNotInstanceOf(OffloadMessage.class);
        assertThat(result.get(result.size() - 1).getContentAsString()).isEqualTo("K".repeat(50));
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

    @Test
    void tokenThresholdExactlyAtValueTriggersAndBelowDoesNot() {
        // TC_008: tokensThreshold 侧恰等边界 — == 阈值触发（含等号），
        // 阈值-1 不触发（AC-1-02/03 对照组）。
        // counter 对存量与新增消息各计一次，固定端口取阈值一半构造恰等/差一场景。
        MessageOffloaderConfig atBoundary = v2Config(10, 5, 0);
        OffloadService atService = memoryService();
        SessionModelContext atContext = v2Context(atBoundary, atService, fixedTokenCounter(25));
        atContext.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage("A".repeat(50), "tc-eq")
        )).toCompletableFuture().join();
        assertThat(atContext.getMessages().get(1)).isInstanceOf(OffloadMessage.class);

        MessageOffloaderConfig belowBoundary = v2Config(10, 5, 0);
        SessionModelContext belowContext = v2Context(belowBoundary, memoryService(), fixedTokenCounter(24));
        belowContext.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage("B".repeat(50), "tc-below")
        )).toCompletableFuture().join();
        assertThat(belowContext.getMessages().get(1))
                .isInstanceOf(ToolMessage.class).isNotInstanceOf(OffloadMessage.class);
        assertThat(belowContext.getMessages().get(1).getContentAsString()).isEqualTo("B".repeat(50));
    }

    @Test
    void trimSizeCapsPreviewWithEllipsisSuffix() {
        // TC_009: v2 校验约束 trimSize 必须小于 large_message_threshold，
        // trimSize 是卸载预览的截断上限——预览=前 trimSize 字符+省略符。
        MessageOffloaderConfig config = v2Config(50, 40, 0);
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));
        String exactLength = "E".repeat(60);

        context.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage(exactLength, "tc-min")
        )).toCompletableFuture().join();

        BaseMessage marker = context.getMessages().get(1);
        assertThat(marker).isInstanceOf(OffloadMessage.class);
        assertThat(marker.getContentAsString()).startsWith("E".repeat(40) + MessageOffloader.OMIT_STRING);
        assertThat(marker.getContentAsString()).doesNotContain("E".repeat(41));
    }

    @Test
    void windowLargerThanContextShrinksAndNeverOffloads() {
        // TC_116: 窗口(50) ≥ 消息总数时收缩至可用范围——全部候选在窗口内零卸载，
        // 保护优先于阈值触发，补消息后总数仍小于窗口则持续零卸载。
        MessageOffloaderConfig config = v2Config(10, 5, 50);
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));

        context.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage("A".repeat(50), "tc-big1"),
                new ToolMessage("B".repeat(50), "tc-big2"),
                new ToolMessage("C".repeat(50), "tc-big3"),
                new ToolMessage("D".repeat(50), "tc-big4"),
                new ToolMessage("E".repeat(50), "tc-big5")
        )).toCompletableFuture().join();
        assertThat(context.getMessages()).noneMatch(OffloadMessage.class::isInstance);

        // 补消息至总数 11（仍 < 窗口 50）：持续零卸载、对话不阻塞。
        context.addMessages(List.of(
                new UserMessage("u2"),
                new UserMessage("u3"),
                new ToolMessage("F".repeat(50), "tc-big6"),
                new UserMessage("u4"),
                new UserMessage("u5")
        )).toCompletableFuture().join();
        assertThat(context.getMessages()).hasSize(11);
        assertThat(context.getMessages()).noneMatch(OffloadMessage.class::isInstance);
        assertThat(context.getMessages().get(1).getContentAsString()).isEqualTo("A".repeat(50));
    }

    @Test
    void windowOverflowTwoStageCriticalRecovery() {
        // TC_117: 窗口容量即条数上限（实现无独立上限配置项）——3 条占满窗口全保护，
        // 总数 5=上限-1 时最早两条恢复，总数 6=恰达上限时最新条也恢复（分阶段 2+1）。
        MessageOffloaderConfig config = v2Config(10, 5, 3);
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));

        context.addMessages(List.of(
                new ToolMessage("L1".repeat(25), "tc-l1"),
                new ToolMessage("L2".repeat(25), "tc-l2"),
                new ToolMessage("L3".repeat(25), "tc-l3")
        )).toCompletableFuture().join();
        assertThat(context.getMessages()).noneMatch(OffloadMessage.class::isInstance);

        context.addMessages(List.of(new UserMessage("u4"), new UserMessage("u5")))
                .toCompletableFuture().join();
        List<BaseMessage> stageOne = context.getMessages();
        assertThat(stageOne.get(0)).isInstanceOf(OffloadMessage.class);
        assertThat(stageOne.get(1)).isInstanceOf(OffloadMessage.class);
        assertThat(stageOne.get(2)).isInstanceOf(ToolMessage.class).isNotInstanceOf(OffloadMessage.class);
        assertThat(stageOne.get(2).getContentAsString()).isEqualTo("L3".repeat(25));

        context.addMessages(List.of(new UserMessage("u6"))).toCompletableFuture().join();
        List<BaseMessage> stageTwo = context.getMessages();
        assertThat(stageTwo.get(2)).isInstanceOf(OffloadMessage.class);
        assertThat(stageTwo.stream().filter(OffloadMessage.class::isInstance)).hasSize(3);
    }

    @Test
    void storeFailureRetriesOnNextTrigger() {
        // TC_120: store 失败不吞原文不写假标记，恢复后下次触发重新判定并成功卸载取回。
        MessageOffloaderConfig config = v2Config(10, 5, 0);
        java.util.concurrent.atomic.AtomicBoolean storeDown =
                new java.util.concurrent.atomic.AtomicBoolean(true);
        MemoryOffloadStore memory = new MemoryOffloadStore();
        OffloadStore flakyStore = new OffloadStore() {
            @Override
            public OffloadBackend backend() {
                return OffloadBackend.MEMORY;
            }

            @Override
            public void save(OffloadNamespace ns, String handle, OffloadPayload payload,
                             Duration ttl) throws OffloadException {
                if (storeDown.get()) {
                    throw new OffloadException(OffloadErrorCode.OFFLOAD_STORE_IO, "simulated outage");
                }
                memory.save(ns, handle, payload, ttl);
            }

            @Override
            public OffloadPayload load(OffloadNamespace ns, String handle) throws OffloadException {
                return memory.load(ns, handle);
            }

            @Override
            public boolean delete(OffloadNamespace ns, String handle) {
                return memory.delete(ns, handle);
            }

            @Override
            public boolean exists(OffloadNamespace ns, String handle) {
                return memory.exists(ns, handle);
            }
        };
        LinkedHashMap<OffloadBackend, OffloadStore> chain = new LinkedHashMap<>();
        chain.put(OffloadBackend.MEMORY, flakyStore);
        OffloadService service = new OffloadServiceImpl(new OffloadCatalogImpl(),
                new CompositeOffloadStore(chain), new SessionOffloadGate(), new OffloadConfig());
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));
        String original = "F".repeat(50);

        // 第一次触发：store 失败 → 空收据 → 原文 inline，无 marker 无异常。
        context.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage(original, "tc-flaky")
        )).toCompletableFuture().join();
        assertThat(context.getMessages().get(1))
                .isInstanceOf(ToolMessage.class).isNotInstanceOf(OffloadMessage.class);
        assertThat(context.getMessages().get(1).getContentAsString()).isEqualTo(original);

        // 存储恢复后补消息再次触发：重新尝试写入 → 同一批旧消息成功卸载且可取回。
        storeDown.set(false);
        context.addMessages(List.of(new UserMessage("u2"))).toCompletableFuture().join();
        List<BaseMessage> result = context.getMessages();
        assertThat(result.get(1)).isInstanceOf(OffloadMessage.class);
        OffloadMessage marker = result.get(1) instanceof OffloadMessage om ? om : null;
        Object rawReloader = context.reloaderTool();
        SessionModelContext.ReloaderTool reloader =
                rawReloader instanceof SessionModelContext.ReloaderTool rt ? rt : null;
        assertThat(reloader.reloadOriginalContextMessages(marker.getOffloadHandle(), marker.getOffloadType()))
                .contains(original);
    }

    @Test
    void protectionWindowRecoversOldestFirstInOrder() {
        // TC_124: L1→L2→L3 逐条滑出——每轮恰一条最早条目恢复标记，恢复序列严格按时间序。
        MessageOffloaderConfig config = v2Config(10, 5, 3);
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(config, service, fixedTokenCounter(100));

        context.addMessages(List.of(
                new ToolMessage("L1".repeat(25), "tc-l1"),
                new ToolMessage("L2".repeat(25), "tc-l2"),
                new ToolMessage("L3".repeat(25), "tc-l3")
        )).toCompletableFuture().join();
        assertThat(context.getMessages()).noneMatch(OffloadMessage.class::isInstance);

        context.addMessages(List.of(new UserMessage("u1"))).toCompletableFuture().join();
        List<BaseMessage> round1 = context.getMessages();
        assertThat(round1.get(0)).isInstanceOf(OffloadMessage.class);
        assertThat(round1.get(1)).isInstanceOf(ToolMessage.class).isNotInstanceOf(OffloadMessage.class);
        assertThat(round1.get(2)).isInstanceOf(ToolMessage.class).isNotInstanceOf(OffloadMessage.class);

        context.addMessages(List.of(new UserMessage("u2"))).toCompletableFuture().join();
        List<BaseMessage> round2 = context.getMessages();
        assertThat(round2.get(1)).isInstanceOf(OffloadMessage.class);
        assertThat(round2.get(2)).isInstanceOf(ToolMessage.class).isNotInstanceOf(OffloadMessage.class);

        context.addMessages(List.of(new UserMessage("u3"))).toCompletableFuture().join();
        List<BaseMessage> round3 = context.getMessages();
        assertThat(round3.get(2)).isInstanceOf(OffloadMessage.class);
        assertThat(round3.stream().filter(OffloadMessage.class::isInstance)).hasSize(3);
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
