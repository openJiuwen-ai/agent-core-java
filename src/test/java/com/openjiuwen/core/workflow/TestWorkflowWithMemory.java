/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.MemInfo;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.workflow.component.resource.MemoryRetrievalCompConfig;
import com.openjiuwen.core.workflow.component.resource.MemoryRetrievalExecutable;
import com.openjiuwen.core.workflow.component.resource.MemoryRetrievalOutput;
import com.openjiuwen.core.workflow.component.resource.MemoryWriteCompConfig;
import com.openjiuwen.core.workflow.component.resource.MemoryWriteExecutable;
import com.openjiuwen.core.workflow.component.resource.MemoryWriteOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for memory workflow components.
 *
 * <p>Mirrors Python's {@code test_workflow_with_memory.py} in
 * {@code tests/unit_tests/core/workflow/test_workflow_with_memory.py}.</p>
 */
@DisplayName("TestWorkflowWithMemory")
class TestWorkflowWithMemory {

    @Test
    void testMemoryWriteSuccess() {
        LongTermMemory memory = mock(LongTermMemory.class);
        MemoryWriteExecutable executable = new MemoryWriteExecutable(MemoryWriteCompConfig.builder()
                .memory(memory)
                .scopeId("test_scope")
                .userId("test_user")
                .sessionId("test_session")
                .genMem(true)
                .build());
        List<BaseMessage> messages = List.of(new UserMessage("Hello"), new AssistantMessage("Hi there!"));

        MemoryWriteOutput result = (MemoryWriteOutput) executable.invoke(
                Map.of("messages", messages), fakeSession(), null);

        assertTrue(result.isSuccess());
        verify(memory).addMessages(eq(messages), any(AgentMemoryConfig.class), eq("test_user"),
                eq("test_scope"), eq("test_session"), isNull(), eq(true), eq(2));
    }

    @Test
    void testMemoryWriteWithTimestamp() {
        LongTermMemory memory = mock(LongTermMemory.class);
        MemoryWriteExecutable executable = new MemoryWriteExecutable(
                MemoryWriteCompConfig.builder().memory(memory).build());
        OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);
        List<BaseMessage> messages = List.of(new UserMessage("Test message"));

        MemoryWriteOutput result = (MemoryWriteOutput) executable.invoke(
                Map.of("messages", messages, "timestamp", timestamp), fakeSession(), null);

        assertTrue(result.isSuccess());
        verify(memory).addMessages(eq(messages), any(AgentMemoryConfig.class), eq(LongTermMemory.DEFAULT_VALUE),
                eq(LongTermMemory.DEFAULT_VALUE), eq(LongTermMemory.DEFAULT_VALUE), eq(timestamp), eq(true), eq(2));
    }

    @Test
    void testMemoryWriteEmptyMessagesError() {
        LongTermMemory memory = mock(LongTermMemory.class);
        MemoryWriteExecutable executable = new MemoryWriteExecutable(
                MemoryWriteCompConfig.builder().memory(memory).build());

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of("messages", List.of()), fakeSession(), null));

        assertEquals(StatusCode.COMPONENT_MEMORY_WRITE_INPUT_PARAM_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("Messages list cannot be empty"));
    }

    @Test
    void testMemoryWriteMissingMessagesError() {
        LongTermMemory memory = mock(LongTermMemory.class);
        MemoryWriteExecutable executable = new MemoryWriteExecutable(
                MemoryWriteCompConfig.builder().memory(memory).build());

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of(), fakeSession(), null));

        assertEquals(StatusCode.COMPONENT_MEMORY_WRITE_INPUT_PARAM_ERROR.getCode(), error.getCode());
    }

    @Test
    void testMemoryWriteInvokeCallFailed() {
        LongTermMemory memory = mock(LongTermMemory.class);
        List<BaseMessage> messages = List.of(new UserMessage("Test"));
        doThrow(new RuntimeException("DB connection failed")).when(memory).addMessages(
                eq(messages), any(AgentMemoryConfig.class), eq(LongTermMemory.DEFAULT_VALUE),
                eq(LongTermMemory.DEFAULT_VALUE), eq(LongTermMemory.DEFAULT_VALUE), isNull(), eq(true), eq(2));
        MemoryWriteExecutable executable = new MemoryWriteExecutable(
                MemoryWriteCompConfig.builder().memory(memory).build());

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of("messages", messages), fakeSession(), null));

        assertEquals(StatusCode.COMPONENT_MEMORY_WRITE_INVOKE_CALL_FAILED.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("Memory write call failed"));
    }

    @Test
    void testMemoryRetrievalSuccess() {
        LongTermMemory memory = mock(LongTermMemory.class);
        MemResult fragment = memResult("mem_1", "Test memory", MemoryType.FRAGMENT_MEMORY, 0.85);
        MemResult summary = memResult("summary_1", "Test summary", MemoryType.SUMMARY, 0.8);
        when(memory.searchUserMem(eq("What is my name?"), eq(5), eq("test_user"), eq("test_scope"), eq(0.3)))
                .thenReturn(List.of(fragment));
        when(memory.searchUserHistorySummary(eq("What is my name?"), eq(5), eq("test_user"), eq("test_scope"),
                eq(0.3))).thenReturn(List.of(summary));
        MemoryRetrievalExecutable executable = new MemoryRetrievalExecutable(MemoryRetrievalCompConfig.builder()
                .memory(memory)
                .scopeId("test_scope")
                .userId("test_user")
                .threshold(0.3)
                .build());

        MemoryRetrievalOutput result = (MemoryRetrievalOutput) executable.invoke(
                Map.of("query", "What is my name?", "top_k", 5), fakeSession(), null);

        assertEquals(1, result.getFragmentMemoryResults().size());
        assertSame(fragment, result.getFragmentMemoryResults().get(0));
        assertEquals(1, result.getSummaryResults().size());
        assertSame(summary, result.getSummaryResults().get(0));
    }

    @Test
    void testMemoryRetrievalMultipleResults() {
        LongTermMemory memory = mock(LongTermMemory.class);
        List<MemResult> fragments = List.of(
                memResult("mem_1", "Memory 1", MemoryType.FRAGMENT_MEMORY, 0.9),
                memResult("mem_2", "Memory 2", MemoryType.FRAGMENT_MEMORY, 0.7));
        List<MemResult> summaries = List.of(memResult("summary_1", "Summary 1", MemoryType.SUMMARY, 0.88));
        when(memory.searchUserMem(eq("test query"), eq(10), eq(LongTermMemory.DEFAULT_VALUE),
                eq(LongTermMemory.DEFAULT_VALUE), eq(0.5))).thenReturn(fragments);
        when(memory.searchUserHistorySummary(eq("test query"), eq(10), eq(LongTermMemory.DEFAULT_VALUE),
                eq(LongTermMemory.DEFAULT_VALUE), eq(0.5))).thenReturn(summaries);
        MemoryRetrievalExecutable executable = new MemoryRetrievalExecutable(
                MemoryRetrievalCompConfig.builder().memory(memory).threshold(0.5).build());

        MemoryRetrievalOutput result = (MemoryRetrievalOutput) executable.invoke(
                Map.of("query", "test query", "top_k", 10), fakeSession(), null);

        assertEquals(2, result.getFragmentMemoryResults().size());
        assertEquals(1, result.getSummaryResults().size());
        verify(memory).searchUserMem(eq("test query"), eq(10), eq(LongTermMemory.DEFAULT_VALUE),
                eq(LongTermMemory.DEFAULT_VALUE), eq(0.5));
    }

    @Test
    void testMemoryRetrievalEmptyResults() {
        LongTermMemory memory = mock(LongTermMemory.class);
        when(memory.searchUserMem(eq("nonexistent query"), eq(5), eq(LongTermMemory.DEFAULT_VALUE),
                eq(LongTermMemory.DEFAULT_VALUE), eq(0.3))).thenReturn(List.of());
        when(memory.searchUserHistorySummary(eq("nonexistent query"), eq(5), eq(LongTermMemory.DEFAULT_VALUE),
                eq(LongTermMemory.DEFAULT_VALUE), eq(0.3))).thenReturn(List.of());
        MemoryRetrievalExecutable executable = new MemoryRetrievalExecutable(
                MemoryRetrievalCompConfig.builder().memory(memory).build());

        MemoryRetrievalOutput result = (MemoryRetrievalOutput) executable.invoke(
                Map.of("query", "nonexistent query"), fakeSession(), null);

        assertTrue(result.getFragmentMemoryResults().isEmpty());
        assertTrue(result.getSummaryResults().isEmpty());
    }

    @Test
    void testMemoryRetrievalEmptyQueryError() {
        LongTermMemory memory = mock(LongTermMemory.class);
        MemoryRetrievalExecutable executable = new MemoryRetrievalExecutable(
                MemoryRetrievalCompConfig.builder().memory(memory).build());

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of("query", "   "), fakeSession(), null));

        assertEquals(StatusCode.COMPONENT_MEMORY_RETRIEVAL_INPUT_PARAM_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("Query must be a non-empty string"));
    }

    @Test
    void testMemoryRetrievalMissingQueryError() {
        LongTermMemory memory = mock(LongTermMemory.class);
        MemoryRetrievalExecutable executable = new MemoryRetrievalExecutable(
                MemoryRetrievalCompConfig.builder().memory(memory).build());

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of(), fakeSession(), null));

        assertEquals(StatusCode.COMPONENT_MEMORY_RETRIEVAL_INPUT_PARAM_ERROR.getCode(), error.getCode());
    }

    @Test
    void testMemoryRetrievalInvokeCallFailed() {
        LongTermMemory memory = mock(LongTermMemory.class);
        when(memory.searchUserMem(eq("test query"), eq(5), eq(LongTermMemory.DEFAULT_VALUE),
                eq(LongTermMemory.DEFAULT_VALUE), eq(0.3))).thenThrow(new RuntimeException("Search failed"));
        MemoryRetrievalExecutable executable = new MemoryRetrievalExecutable(
                MemoryRetrievalCompConfig.builder().memory(memory).build());

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of("query", "test query"), fakeSession(), null));

        assertEquals(StatusCode.COMPONENT_MEMORY_RETRIEVAL_INVOKE_CALL_FAILED.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("Memory retrieval call failed"));
    }

    private static NodeSessionApi fakeSession() {
        return new NodeSessionApi(new NodeSession(new WorkflowSession("memory_test"), "test_component"));
    }

    private static MemResult memResult(String id, String content, MemoryType type, double score) {
        return MemResult.builder()
                .memInfo(MemInfo.builder().memId(id).content(content).type(type).build())
                .score(score)
                .build();
    }
}
