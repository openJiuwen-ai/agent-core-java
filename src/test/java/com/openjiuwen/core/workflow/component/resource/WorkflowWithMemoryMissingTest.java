/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.common.async.FutureList;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.memory.AddMemResult;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.MemInfo;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.session.BaseSession;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code test_workflow_with_memory} module in
 * {@code tests/unit_tests/core/workflow/test_workflow_with_memory.py}.</p>
 */
class WorkflowWithMemoryMissingTest {

    @Test
    void memoryWriteSuccess() {
        RecordingMemory memory = new RecordingMemory();
        MemoryWriteCompConfig config = MemoryWriteCompConfig.builder()
                .memory(memory)
                .scopeId("test_scope")
                .userId("test_user")
                .sessionId("test_session")
                .genMem(true)
                .build();
        MemoryWriteExecutable executable = (MemoryWriteExecutable) new MemoryWriteComponent(config).toExecutable();
        List<BaseMessage> messages = List.of(new UserMessage("Hello"), new AssistantMessage("Hi there!"));

        Map<String, Object> output = executable.invoke(Map.of("messages", messages), new TestSession(), null);

        assertEquals(Boolean.TRUE, output.get("success"));
        assertTrue(memory.addMessagesCalled);
    }

    @Test
    void memoryWriteWithTimestamp() {
        RecordingMemory memory = new RecordingMemory();
        MemoryWriteExecutable executable = (MemoryWriteExecutable)
                new MemoryWriteComponent(new MemoryWriteCompConfig(memory)).toExecutable();
        ZonedDateTime timestamp = ZonedDateTime.parse("2026-06-16T10:15:30Z");

        Map<String, Object> output = executable.invoke(
                Map.of("messages", List.of(new UserMessage("Test message")), "timestamp", timestamp),
                new TestSession(),
                null
        );

        assertEquals(Boolean.TRUE, output.get("success"));
        assertEquals(timestamp, memory.timestamp);
    }

    @Test
    void memoryWriteEmptyMessagesError() {
        MemoryWriteExecutable executable = new MemoryWriteExecutable(new MemoryWriteCompConfig(new RecordingMemory()));

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of("messages", List.of()), new TestSession(), null));

        assertEquals(StatusCode.COMPONENT_MEMORY_WRITE_INPUT_PARAM_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("Messages list cannot be empty"));
    }

    @Test
    void memoryWriteMissingMessagesError() {
        BaseError error = assertThrows(BaseError.class, () -> MemoryWriteExecutable.validateInputs(Map.of()));

        assertEquals(StatusCode.COMPONENT_MEMORY_WRITE_INPUT_PARAM_ERROR.getCode(), error.getCode());
    }

    @Test
    void memoryWriteInvokeCallFailed() {
        RecordingMemory memory = new RecordingMemory();
        memory.addMessagesError = new IllegalStateException("DB connection failed");
        MemoryWriteExecutable executable = new MemoryWriteExecutable(new MemoryWriteCompConfig(memory));

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of("messages", List.of(new UserMessage("Test"))), new TestSession(), null));

        assertEquals(StatusCode.COMPONENT_MEMORY_WRITE_INVOKE_CALL_FAILED.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("Memory write call failed"));
    }

    @Test
    void memoryRetrievalSuccess() {
        MemResult fragment = result("mem_1", "Test memory", MemoryType.USER_PROFILE, 0.85d);
        MemResult summary = result("summary_1", "Test summary", MemoryType.SUMMARY, 0.8d);
        RecordingMemory memory = new RecordingMemory();
        memory.fragmentResults = List.of(fragment);
        memory.summaryResults = List.of(summary);
        MemoryRetrievalCompConfig config = MemoryRetrievalCompConfig.builder()
                .memory(memory)
                .scopeId("test_scope")
                .userId("test_user")
                .threshold(0.3d)
                .build();
        MemoryRetrievalExecutable executable = (MemoryRetrievalExecutable)
                new MemoryRetrievalComponent(config).toExecutable();

        Map<String, Object> output = executable.invoke(
                Map.of("query", "What is my name?", "top_k", 5),
                new TestSession(),
                null
        );

        List<?> fragments = (List<?>) output.get("fragment_memory_results");
        List<?> summaries = (List<?>) output.get("summary_results");
        assertEquals(1, fragments.size());
        assertSame(fragment, fragments.get(0));
        assertEquals(1, summaries.size());
        assertSame(summary, summaries.get(0));
    }

    @Test
    void memoryRetrievalMultipleResults() {
        RecordingMemory memory = new RecordingMemory();
        memory.fragmentResults = List.of(
                result("mem_1", "Memory 1", MemoryType.USER_PROFILE, 0.9d),
                result("mem_2", "Memory 2", MemoryType.USER_PROFILE, 0.7d)
        );
        memory.summaryResults = List.of(result("summary_1", "Summary 1", MemoryType.SUMMARY, 0.88d));
        MemoryRetrievalCompConfig config = MemoryRetrievalCompConfig.builder()
                .memory(memory)
                .threshold(0.5d)
                .build();

        Map<String, Object> output = new MemoryRetrievalExecutable(config)
                .invoke(Map.of("query", "test query", "top_k", 10), new TestSession(), null);

        assertEquals(2, ((List<?>) output.get("fragment_memory_results")).size());
        assertEquals(1, ((List<?>) output.get("summary_results")).size());
        assertTrue(memory.searchUserMemCalled);
        assertTrue(memory.searchUserHistorySummaryCalled);
        assertEquals(10, memory.searchUserMemNum);
        assertEquals(0.5d, memory.searchUserMemThreshold);
    }

    @Test
    void memoryRetrievalEmptyResults() {
        Map<String, Object> output = new MemoryRetrievalExecutable(new MemoryRetrievalCompConfig(new RecordingMemory()))
                .invoke(Map.of("query", "nonexistent query"), new TestSession(), null);

        assertEquals(List.of(), output.get("fragment_memory_results"));
        assertEquals(List.of(), output.get("summary_results"));
    }

    @Test
    void memoryRetrievalEmptyQueryError() {
        MemoryRetrievalExecutable executable = new MemoryRetrievalExecutable(
                new MemoryRetrievalCompConfig(new RecordingMemory())
        );

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of("query", "   "), new TestSession(), null));

        assertEquals(StatusCode.COMPONENT_MEMORY_RETRIEVAL_INPUT_PARAM_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("Query must be a non-empty string"));
    }

    @Test
    void memoryRetrievalMissingQueryError() {
        BaseError error = assertThrows(BaseError.class, () -> MemoryRetrievalExecutable.validateInputs(Map.of()));

        assertEquals(StatusCode.COMPONENT_MEMORY_RETRIEVAL_INPUT_PARAM_ERROR.getCode(), error.getCode());
    }

    @Test
    void memoryRetrievalInvokeCallFailed() {
        RecordingMemory memory = new RecordingMemory();
        memory.searchUserMemError = new IllegalStateException("Search failed");
        MemoryRetrievalExecutable executable = new MemoryRetrievalExecutable(new MemoryRetrievalCompConfig(memory));

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of("query", "test query"), new TestSession(), null));

        assertEquals(StatusCode.COMPONENT_MEMORY_RETRIEVAL_INVOKE_CALL_FAILED.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("Memory retrieval call failed"));
    }

    private static MemResult result(String memId, String content, MemoryType type, double score) {
        return new MemResult(new MemInfo(memId, content, type, null), score);
    }

    private static final class RecordingMemory extends LongTermMemory {
        private boolean addMessagesCalled;
        private ZonedDateTime timestamp;
        private RuntimeException addMessagesError;
        private List<MemResult> fragmentResults = List.of();
        private List<MemResult> summaryResults = List.of();
        private RuntimeException searchUserMemError;
        private boolean searchUserMemCalled;
        private boolean searchUserHistorySummaryCalled;
        private int searchUserMemNum;
        private double searchUserMemThreshold;

        @Override
        public CompletableFuture<AddMemResult> addMessages(
                List<BaseMessage> messages,
                AgentMemoryConfig agentConfig,
                String userId,
                String scopeId,
                String sessionId,
                ZonedDateTime timestamp,
                boolean genMem,
                int genMemWithHistoryMsgNum
        ) {
            addMessagesCalled = true;
            this.timestamp = timestamp;
            if (addMessagesError != null) {
                return CompletableFuture.failedFuture(addMessagesError);
            }
            return CompletableFuture.completedFuture(new AddMemResult());
        }

        @Override
        public FutureList<MemResult> searchUserMem(
                String query,
                int num,
                String userId,
                String scopeId,
                double threshold
        ) {
            searchUserMemCalled = true;
            searchUserMemNum = num;
            searchUserMemThreshold = threshold;
            if (searchUserMemError != null) {
                return FutureList.fromFuture(CompletableFuture.failedFuture(searchUserMemError));
            }
            return FutureList.completed(fragmentResults);
        }

        @Override
        public FutureList<MemResult> searchUserHistorySummary(
                String query,
                int num,
                String userId,
                String scopeId,
                double threshold
        ) {
            searchUserHistorySummaryCalled = true;
            return FutureList.completed(summaryResults);
        }
    }

    private static final class TestSession extends BaseSession {
        @Override
        public String sessionId() {
            return "test_session";
        }

        public String getExecutableId() {
            return "test_component";
        }
    }
}
