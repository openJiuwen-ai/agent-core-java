/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.foundation.llm.InferenceAffinityModel;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.KvCacheReleaseRequest;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link KVCacheManager}.
 * <p>
 * Ported from Python's {@code test_kv_cache_manager.py}.
 */
class KVCacheManagerTest {
    private ContextWindow buildWindow(List<BaseMessage> systemMessages, List<BaseMessage> contextMessages,
            List<ToolInfo> tools) {
        return ContextWindow.builder().systemMessages(systemMessages != null ? systemMessages : new ArrayList<>())
                .contextMessages(contextMessages != null ? contextMessages : new ArrayList<>())
                .tools(tools != null ? tools : new ArrayList<>()).statistic(new ContextStats()).build();
    }

    @Test
    @DisplayName("first call stores window without release")
    void testFirstCallNoRelease() {
        KVCacheManager manager = new KVCacheManager("session-1");
        ContextWindow window =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("hello")), List.of());
        // Should not throw
        manager.release(window);
    }

    @Test
    @DisplayName("identical windows do not trigger release")
    void testIdenticalWindowsNoRelease() {
        KVCacheManager manager = new KVCacheManager("session-1");
        List<BaseMessage> sys = List.of(new SystemMessage("sys"));
        List<BaseMessage> ctx = List.of(new UserMessage("hello"), new AssistantMessage("hi"));

        ContextWindow w1 = buildWindow(sys, ctx, List.of());
        ContextWindow w2 = buildWindow(sys, ctx, List.of());

        manager.release(w1);
        // Second call with identical content should not throw
        manager.release(w2);
    }

    @Test
    @DisplayName("modified messages trigger release detection")
    void testModifiedMessagesTriggerRelease() {
        KVCacheManager manager = new KVCacheManager("session-1");

        ContextWindow w1 = buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("hello")), List.of());
        manager.release(w1);

        // Change content in second window
        ContextWindow w2 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("modified")), List.of());
        // Should not throw (just log)
        manager.release(w2);
    }

    @Test
    @DisplayName("modified tools trigger release detection")
    void testModifiedToolsTriggerRelease() {
        KVCacheManager manager = new KVCacheManager("session-1");

        List<ToolInfo> tools1 = List.of(ToolInfo.builder().name("tool1").description("desc1").build());
        List<ToolInfo> tools2 = List.of(ToolInfo.builder().name("tool1").description("modified desc").build());

        ContextWindow w1 = buildWindow(List.of(), List.of(new UserMessage("u")), tools1);
        manager.release(w1);

        ContextWindow w2 = buildWindow(List.of(), List.of(new UserMessage("u")), tools2);
        manager.release(w2);
    }

    @Test
    @DisplayName("multiple successive releases work correctly")
    void testMultipleSuccessiveReleases() {
        KVCacheManager manager = new KVCacheManager("session-1");

        for (int i = 0; i < 5; i++) {
            ContextWindow w =
                buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("msg-" + i)), List.of());
            manager.release(w);
        }
    }

    @Test
    @DisplayName("empty to non-empty window transition")
    void testEmptyToNonEmptyTransition() {
        KVCacheManager manager = new KVCacheManager("session-1");

        ContextWindow w1 = buildWindow(List.of(), List.of(), List.of());
        manager.release(w1);

        ContextWindow w2 = buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("hello")), List.of());
        manager.release(w2);
    }

    @Test
    @DisplayName("Path 1: InferenceAffinityModel is invoked when window changes")
    void release_whenModelIsInferenceAffinityModel_invokesPath1Release() throws Exception {
        KVCacheManager manager = new KVCacheManager("session-1");
        InferenceAffinityModel mockModel = mock(InferenceAffinityModel.class);
        when(mockModel.release(any(KvCacheReleaseRequest.class))).thenReturn(true);

        ContextWindow w1 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("hello")), List.of());
        manager.release(w1, mockModel);

        ContextWindow w2 = buildWindow(List.of(new SystemMessage("sys")),
            List.of(new UserMessage("modified"), new AssistantMessage("resp")), List.of());
        manager.release(w2, mockModel);

        // w1.getMessages() = [SystemMessage("sys"), UserMessage("hello")]
        // w2.getMessages() = [SystemMessage("sys"), UserMessage("modified"), AssistantMessage("resp")]
        // first diff at index 1 (UserMessage content changed)
        verify(mockModel).release(argThat(req -> "session-1".equals(req.sessionId())
            && w1.getMessages().equals(req.messages()) && req.messagesReleasedIndex() == 1
            && w1.getToolList().equals(req.tools()) && req.toolsReleasedIndex() == null
            && req.model() == null));
    }

    @Test
    @DisplayName("Path 2: Model with supportsKvCacheRelease=true delegates release")
    void release_whenModelSupportsKvCacheRelease_delegatesToModelRelease() throws Exception {
        KVCacheManager manager = new KVCacheManager("session-2");
        Model mockModel = mock(Model.class);
        when(mockModel.supportsKvCacheRelease()).thenReturn(true);
        when(mockModel.release(any(KvCacheReleaseRequest.class))).thenReturn(true);

        ContextWindow w1 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("hello")), List.of());
        manager.release(w1, mockModel);

        ContextWindow w2 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("changed")), List.of());
        manager.release(w2, mockModel);

        verify(mockModel).supportsKvCacheRelease();
        // first diff at index 1 (UserMessage content changed)
        verify(mockModel).release(argThat(req -> "session-2".equals(req.sessionId())
            && w1.getMessages().equals(req.messages()) && req.messagesReleasedIndex() == 1
            && w1.getToolList().equals(req.tools()) && req.toolsReleasedIndex() == null
            && req.model() == null));
    }

    @Test
    @DisplayName("Path 2: Model with supportsKvCacheRelease=false does not invoke release")
    void release_whenModelDoesNotSupportKvCacheRelease_skipsRelease() throws Exception {
        KVCacheManager manager = new KVCacheManager("session-3");
        Model mockModel = mock(Model.class);
        when(mockModel.supportsKvCacheRelease()).thenReturn(false);

        ContextWindow w1 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("hello")), List.of());
        manager.release(w1, mockModel);

        ContextWindow w2 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("changed")), List.of());
        manager.release(w2, mockModel);

        verify(mockModel).supportsKvCacheRelease();
        verify(mockModel, never()).release(any(KvCacheReleaseRequest.class));
    }

    @Test
    @DisplayName("null model does not throw and does not invoke any release")
    void release_whenModelIsNull_skipsBothPaths() {
        KVCacheManager manager = new KVCacheManager("session-4");

        ContextWindow w1 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("hello")), List.of());
        manager.release(w1, null);

        ContextWindow w2 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("changed")), List.of());
        // Should not throw
        manager.release(w2, null);
    }

    @Test
    @DisplayName("non-model object does not trigger release and does not throw")
    void release_whenModelIsUnrelatedType_skipsBothPaths() {
        KVCacheManager manager = new KVCacheManager("session-5");
        Object unrelated = "not-a-model";

        ContextWindow w1 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("hello")), List.of());
        manager.release(w1, unrelated);

        ContextWindow w2 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("changed")), List.of());
        manager.release(w2, unrelated);
    }

    @Test
    @DisplayName("Path 1 release exception is swallowed; lastContextWindow still updates")
    void release_whenPath1Throws_logsWarningAndContinues() throws Exception {
        KVCacheManager manager = new KVCacheManager("session-6");
        InferenceAffinityModel mockModel = mock(InferenceAffinityModel.class);
        when(mockModel.release(any(KvCacheReleaseRequest.class)))
            .thenThrow(new RuntimeException("vllm 404"));

        ContextWindow w1 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("hello")), List.of());
        manager.release(w1, mockModel);

        ContextWindow w2 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("changed")), List.of());
        // Should not throw despite the underlying release raising
        manager.release(w2, mockModel);

        // Third window differing from w2 — verifies the manager kept tracking lastContextWindow
        ContextWindow w3 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("again-changed")), List.of());
        manager.release(w3, mockModel);

        // release invoked twice (w2 vs w1, w3 vs w2); the first call's exception was caught
        verify(mockModel, times(2)).release(any(KvCacheReleaseRequest.class));
    }

    @Test
    @DisplayName("Path 2 release exception is swallowed; lastContextWindow still updates")
    void release_whenPath2Throws_logsWarningAndContinues() throws Exception {
        KVCacheManager manager = new KVCacheManager("session-7");
        Model mockModel = mock(Model.class);
        when(mockModel.supportsKvCacheRelease()).thenReturn(true);
        when(mockModel.release(any(KvCacheReleaseRequest.class)))
            .thenThrow(new RuntimeException("connection refused"));

        ContextWindow w1 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("hello")), List.of());
        manager.release(w1, mockModel);

        ContextWindow w2 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("changed")), List.of());
        manager.release(w2, mockModel);

        ContextWindow w3 =
            buildWindow(List.of(new SystemMessage("sys")), List.of(new UserMessage("again-changed")), List.of());
        manager.release(w3, mockModel);

        verify(mockModel, times(2)).release(any(KvCacheReleaseRequest.class));
    }

    @Test
    @DisplayName("toolsReleasedIndex is propagated when only tools differ")
    void release_whenOnlyToolsChanged_propagatesToolIdx() throws Exception {
        KVCacheManager manager = new KVCacheManager("session-8");
        InferenceAffinityModel mockModel = mock(InferenceAffinityModel.class);
        when(mockModel.release(any(KvCacheReleaseRequest.class))).thenReturn(true);

        List<ToolInfo> tools1 = List.of(ToolInfo.builder().name("t1").description("d1").build());
        List<ToolInfo> tools2 = List.of(ToolInfo.builder().name("t1").description("d2").build());

        ContextWindow w1 = buildWindow(List.of(new SystemMessage("sys")),
            List.of(new UserMessage("u")), tools1);
        manager.release(w1, mockModel);

        // Same messages (no diff), modified tool at index 0 → triggers release
        ContextWindow w2 = buildWindow(List.of(new SystemMessage("sys")),
            List.of(new UserMessage("u")), tools2);
        manager.release(w2, mockModel);

        // messages identical → msgIdx falls through to prevMsgs.size()=2 (no diff found)
        verify(mockModel).release(argThat(req -> "session-8".equals(req.sessionId())
            && w1.getMessages().equals(req.messages()) && req.messagesReleasedIndex() == 2
            && w1.getToolList().equals(req.tools()) && Integer.valueOf(0).equals(req.toolsReleasedIndex())
            && req.model() == null));
    }
}
