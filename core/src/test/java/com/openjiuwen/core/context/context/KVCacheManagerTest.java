/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Focused parity tests for KV cache release decisions.
 *
 * <p>Mirrors Python's {@code KVCacheManager} in
 * {@code openjiuwen/core/context_engine/context/kv_cache_manager.py}.</p>
 */
class KVCacheManagerTest {

    @Test
    void firstChangedDecisionReportsRemovedMessageSuffix() {
        BaseMessage retained = new BaseMessage("user", "keep");
        ContextWindow previous = window(List.of(retained, new BaseMessage("user", "remove")), List.of());
        ContextWindow next = window(List.of(retained), List.of());

        assertThat(KVCacheManager.firstChangedDecision(previous, next))
                .contains(new KVCacheManager.ReleaseDecision(true, 1, null));
    }

    @Test
    void firstChangedDecisionReportsRemovedToolSuffix() {
        ToolInfo retained = ToolInfo.builder().name("search").build();
        ContextWindow previous = window(List.of(), List.of(retained, ToolInfo.builder().name("lookup").build()));
        ContextWindow next = window(List.of(), List.of(retained));

        assertThat(KVCacheManager.firstChangedDecision(previous, next))
                .contains(new KVCacheManager.ReleaseDecision(true, null, 1));
    }

    @Test
    void firstChangedDecisionHandlesEmptyAndAbsentNextWindow() {
        ContextWindow previous = window(List.of(new BaseMessage("user", "remove")),
                List.of(ToolInfo.builder().name("search").build()));

        assertThat(KVCacheManager.firstChangedDecision(previous, window(List.of(), List.of())))
                .contains(new KVCacheManager.ReleaseDecision(true, 0, 0));
        assertThat(KVCacheManager.firstChangedDecision(previous, null))
                .contains(new KVCacheManager.ReleaseDecision(true, 0, 0));
    }

    @Test
    void releaseWithoutModelReturnsWithoutRecordingWindow() {
        KVCacheManager manager = new KVCacheManager("session-a");

        manager.release(window(List.of(new BaseMessage("user", "hello")), List.of()), null);

        assertThat(manager.lastContextWindow()).isNull();
    }

    @Test
    void firstReleasableWindowOnlyRecordsPreviousWindow() {
        KVCacheManager manager = new KVCacheManager("session-a");
        RecordingModel model = new RecordingModel();
        ContextWindow firstWindow = window(List.of(new BaseMessage("user", "hello")), List.of());

        manager.release(firstWindow, model);

        assertThat(model.calls).isEmpty();
        assertThat(manager.lastContextWindow()).isEqualTo(firstWindow);
    }

    @Test
    void changedMessageInvokesReleaseWithPreviousMessagesAndChangedIndex() {
        KVCacheManager manager = new KVCacheManager("session-a");
        RecordingModel model = new RecordingModel();
        ContextWindow previous = window(List.of(
                new BaseMessage("user", "q"),
                new BaseMessage("assistant", "a")
        ), List.of());
        ContextWindow current = window(List.of(
                new BaseMessage("user", "q"),
                new BaseMessage("assistant", "changed")
        ), List.of());

        manager.release(previous, model);
        manager.release(current, model);

        assertThat(model.calls).hasSize(1);
        ReleaseCall call = model.calls.get(0);
        assertThat(call.sessionId).isEqualTo("session-a");
        assertThat(call.messages).extracting(BaseMessage::getContent).containsExactly("q", "a");
        assertThat(call.messagesReleasedIndex).isEqualTo(1);
        assertThat(call.tools).isNull();
        assertThat(call.toolsReleasedIndex).isNull();
        assertThat(manager.lastContextWindow()).isEqualTo(current);
    }

    @Test
    void changedToolInvokesReleaseWithPreviousToolsAndToolIndex() {
        KVCacheManager manager = new KVCacheManager("session-a");
        RecordingModel model = new RecordingModel();
        ContextWindow previous = window(List.of(new BaseMessage("user", "q")),
                List.of(ToolInfo.builder().name("search").description("Search").build()));
        ContextWindow current = window(List.of(new BaseMessage("user", "q")),
                List.of(ToolInfo.builder().name("lookup").description("Lookup").build()));

        manager.release(previous, model);
        manager.release(current, model);

        assertThat(model.calls).hasSize(1);
        ReleaseCall call = model.calls.get(0);
        assertThat(call.messagesReleasedIndex).isEqualTo(1);
        assertThat(call.tools).extracting(ToolInfo::getName).containsExactly("search");
        assertThat(call.toolsReleasedIndex).isZero();
    }

    @Test
    void unchangedWindowDoesNotRelease() {
        KVCacheManager manager = new KVCacheManager("session-a");
        RecordingModel model = new RecordingModel();
        ContextWindow previous = window(List.of(new BaseMessage("user", "q")), List.of());
        ContextWindow current = window(List.of(new BaseMessage("user", "q")), List.of());

        manager.release(previous, model);
        manager.release(current, model);

        assertThat(model.calls).isEmpty();
    }

    @Test
    void reflectionReleaseMethodIsAcceptedLikePythonGetattr() {
        KVCacheManager manager = new KVCacheManager("session-a");
        ReflectiveModel model = new ReflectiveModel();
        ContextWindow previous = window(List.of(new BaseMessage("user", "q")), List.of());
        ContextWindow current = window(List.of(new BaseMessage("user", "changed")), List.of());

        manager.release(previous, model);
        manager.release(current, model);

        assertThat(model.calls).hasSize(1);
        assertThat(model.calls.get(0).messagesReleasedIndex).isZero();
    }

    private static ContextWindow window(List<BaseMessage> messages, List<ToolInfo> tools) {
        return new ContextWindow(List.of(), messages, tools, null);
    }

    /**
     * Release-capable model test double.
     *
     * <p>Mirrors Python's {@code InferenceAffinityModel} release collaborator in
     * {@code openjiuwen/core/context_engine/context/kv_cache_manager.py}.</p>
     */
    private static final class RecordingModel implements KVCacheManager.ReleaseCapableModel {
        private final List<ReleaseCall> calls = new ArrayList<>();

        @Override
        public CompletionStage<Boolean> release(String sessionId, List<BaseMessage> messages,
                                                Integer messagesReleasedIndex, List<ToolInfo> tools,
                                                Integer toolsReleasedIndex) {
            calls.add(new ReleaseCall(sessionId, messages, messagesReleasedIndex, tools, toolsReleasedIndex));
            return CompletableFuture.completedFuture(true);
        }
    }

    /**
     * Reflective model test double.
     *
     * <p>Mirrors Python's generic {@code getattr(model, "release", None)} branch in
     * {@code openjiuwen/core/context_engine/context/kv_cache_manager.py}.</p>
     */
    public static final class ReflectiveModel {
        private final List<ReleaseCall> calls = new ArrayList<>();

        public boolean release(String sessionId, List<BaseMessage> messages, Integer messagesReleasedIndex,
                               List<ToolInfo> tools, Integer toolsReleasedIndex) {
            calls.add(new ReleaseCall(sessionId, messages, messagesReleasedIndex, tools, toolsReleasedIndex));
            return true;
        }
    }

    /**
     * Captured release invocation.
     *
     * <p>Mirrors Python's keyword arguments passed to model release in
     * {@code openjiuwen/core/context_engine/context/kv_cache_manager.py}.</p>
     */
    private record ReleaseCall(String sessionId, List<BaseMessage> messages, Integer messagesReleasedIndex,
                               List<ToolInfo> tools, Integer toolsReleasedIndex) {
    }
}
