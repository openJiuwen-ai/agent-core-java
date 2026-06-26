/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.MemInfo;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.session.BaseSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for T01373.
 *
 * <p>Mirrors Python's {@code MemoryRetrievalCompConfig}, {@code MemoryRetrievalInput},
 * {@code MemoryRetrievalOutput}, {@code MemoryRetrievalExecutable}, and
 * {@code MemoryRetrievalComponent} in
 * {@code openjiuwen/core/workflow/components/resource/memory_retrieval_comp.py}.</p>
 */
class T01373MemoryRetrievalComponentTest {

    @Test
    void configDefaultsMirrorPythonDataclass() {
        RecordingMemory memory = new RecordingMemory();
        MemoryRetrievalCompConfig config = new MemoryRetrievalCompConfig(memory);

        assertSame(memory, config.getMemory());
        assertEquals(LongTermMemory.DEFAULT_VALUE, config.getScopeId());
        assertEquals(LongTermMemory.DEFAULT_VALUE, config.getUserId());
        assertEquals(0.3d, config.getThreshold());
    }

    @Test
    void validateInputsKeepsTopKDefaultAndAllowedExtras() {
        MemoryRetrievalInput input = MemoryRetrievalExecutable.validateInputs(Map.of(
                "query", "hello",
                "extra_field", "extra"
        ));

        assertEquals("hello", input.getQuery());
        assertEquals(5, input.getTopK());
        assertEquals("extra", input.getExtraFields().get("extra_field"));
    }

    @Test
    void memoryRetrievalSuccessReturnsFragmentAndSummaryResults() {
        RecordingMemory memory = new RecordingMemory();
        MemResult fragment = result("mem_1", "Test memory", MemoryType.USER_PROFILE, 0.85d);
        MemResult summary = result("summary_1", "Test summary", MemoryType.SUMMARY, 0.8d);
        memory.fragmentResults = List.of(fragment);
        memory.summaryResults = List.of(summary);
        MemoryRetrievalCompConfig config = new MemoryRetrievalCompConfig(memory, "test_scope", "test_user", 0.3d);
        MemoryRetrievalExecutable executable = (MemoryRetrievalExecutable)
                new MemoryRetrievalComponent(config).toExecutable();

        Map<String, Object> output = executable.invoke(
                Map.of("query", "What is my name?", "top_k", 5),
                new TestSession(),
                null
        );

        List<?> fragments = assertInstanceOf(List.class, output.get("fragment_memory_results"));
        List<?> summaries = assertInstanceOf(List.class, output.get("summary_results"));
        assertEquals(1, fragments.size());
        assertEquals(1, summaries.size());
        assertSame(fragment, fragments.get(0));
        assertSame(summary, summaries.get(0));
    }

    @Test
    void retrievalPassesTopKThresholdUserAndScope() {
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
        assertEquals(LongTermMemory.DEFAULT_VALUE, memory.searchUserMemUserId);
        assertEquals(LongTermMemory.DEFAULT_VALUE, memory.searchUserMemScopeId);
    }

    @Test
    void emptyResultsReturnEmptyLists() {
        RecordingMemory memory = new RecordingMemory();
        MemoryRetrievalCompConfig config = new MemoryRetrievalCompConfig(memory);

        Map<String, Object> output = new MemoryRetrievalExecutable(config)
                .invoke(Map.of("query", "nonexistent query"), new TestSession(), null);

        assertEquals(List.of(), output.get("fragment_memory_results"));
        assertEquals(List.of(), output.get("summary_results"));
    }

    @Test
    void emptyQueryRaisesInputParamError() {
        MemoryRetrievalExecutable executable = new MemoryRetrievalExecutable(
                new MemoryRetrievalCompConfig(new RecordingMemory())
        );

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of("query", "   "), new TestSession(), null));

        assertEquals(StatusCode.COMPONENT_MEMORY_RETRIEVAL_INPUT_PARAM_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("Query must be a non-empty string"));
    }

    @Test
    void missingQueryRaisesInputParamError() {
        BaseError error = assertThrows(BaseError.class,
                () -> MemoryRetrievalExecutable.validateInputs(Map.of()));

        assertEquals(StatusCode.COMPONENT_MEMORY_RETRIEVAL_INPUT_PARAM_ERROR.getCode(), error.getCode());
    }

    @Test
    void memoryFailureRaisesInvokeCallFailed() {
        RecordingMemory memory = new RecordingMemory();
        memory.searchUserMemError = new IllegalStateException("Search failed");
        MemoryRetrievalExecutable executable = new MemoryRetrievalExecutable(new MemoryRetrievalCompConfig(memory));

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of("query", "test query"), new TestSession(), null));

        assertEquals(StatusCode.COMPONENT_MEMORY_RETRIEVAL_INVOKE_CALL_FAILED.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("Memory retrieval call failed"));
    }

    @Test
    void componentAddsExecutableToGraph() {
        MemoryRetrievalComponent component = new MemoryRetrievalComponent(
                new MemoryRetrievalCompConfig(new RecordingMemory())
        );
        RecordingGraph graph = new RecordingGraph();

        component.addComponent(graph, "memory_node", true);

        assertEquals("memory_node", graph.nodeId);
        assertTrue(graph.waitForAll);
        assertInstanceOf(MemoryRetrievalExecutable.class, graph.node);
    }

    private static MemResult result(String memId, String content, MemoryType type, double score) {
        return new MemResult(new MemInfo(memId, content, type, null), score);
    }

    private static final class RecordingMemory extends LongTermMemory {
        private List<MemResult> fragmentResults = List.of();
        private List<MemResult> summaryResults = List.of();
        private RuntimeException searchUserMemError;
        private boolean searchUserMemCalled;
        private boolean searchUserHistorySummaryCalled;
        private int searchUserMemNum;
        private double searchUserMemThreshold;
        private String searchUserMemUserId;
        private String searchUserMemScopeId;

        @Override
        public CompletableFuture<List<MemResult>> searchUserMem(
                String query,
                int num,
                String userId,
                String scopeId,
                double threshold
        ) {
            searchUserMemCalled = true;
            searchUserMemNum = num;
            searchUserMemUserId = userId;
            searchUserMemScopeId = scopeId;
            searchUserMemThreshold = threshold;
            if (searchUserMemError != null) {
                return CompletableFuture.failedFuture(searchUserMemError);
            }
            return CompletableFuture.completedFuture(fragmentResults);
        }

        @Override
        public CompletableFuture<List<MemResult>> searchUserHistorySummary(
                String query,
                int num,
                String userId,
                String scopeId,
                double threshold
        ) {
            searchUserHistorySummaryCalled = true;
            return CompletableFuture.completedFuture(summaryResults);
        }
    }

    private static final class RecordingGraph extends Graph {
        private String nodeId;
        private Executable<?, ?> node;
        private boolean waitForAll;

        @Override
        public Graph addNode(String nodeId, Executable<?, ?> node, boolean waitForAll) {
            this.nodeId = nodeId;
            this.node = node;
            this.waitForAll = waitForAll;
            return this;
        }
    }

    private static final class TestSession extends BaseSession {
        @Override
        public String sessionId() {
            return "test_session";
        }

        public String getExecutableId() {
            return "memory_node";
        }
    }
}
