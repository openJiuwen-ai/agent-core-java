/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.memory.AddMemResult;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.session.BaseSession;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for T01374.
 *
 * <p>Mirrors Python's {@code MemoryWriteCompConfig}, {@code MemoryWriteInput},
 * {@code MemoryWriteOutput}, {@code MemoryWriteExecutable}, and
 * {@code MemoryWriteComponent} in
 * {@code openjiuwen/core/workflow/components/resource/memory_write_comp.py}.</p>
 */
class T01374MemoryWriteComponentTest {

    @Test
    void configDefaultsMirrorPythonDataclass() {
        RecordingMemory memory = new RecordingMemory();
        MemoryWriteCompConfig config = new MemoryWriteCompConfig(memory);

        assertSame(memory, config.getMemory());
        assertEquals(LongTermMemory.DEFAULT_VALUE, config.getScopeId());
        assertEquals(LongTermMemory.DEFAULT_VALUE, config.getUserId());
        assertEquals(LongTermMemory.DEFAULT_VALUE, config.getSessionId());
        assertTrue(config.isGenMem());
        assertEquals(2, config.getGenMemWithHistoryMsgNum());
    }

    @Test
    void writeSuccessInvokesMemoryAndReturnsSuccessMap() {
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
        assertEquals(messages, memory.messages);
        assertEquals("test_user", memory.userId);
        assertEquals("test_scope", memory.scopeId);
        assertEquals("test_session", memory.sessionId);
        assertTrue(memory.genMem);
    }

    @Test
    void timestampIsPassedToLongTermMemory() {
        RecordingMemory memory = new RecordingMemory();
        MemoryWriteExecutable executable = new MemoryWriteExecutable(new MemoryWriteCompConfig(memory));
        ZonedDateTime timestamp = ZonedDateTime.parse("2026-06-16T10:15:30Z");
        List<BaseMessage> messages = List.of(new UserMessage("Test message"));

        Map<String, Object> output = executable.invoke(
                Map.of("messages", messages, "timestamp", timestamp),
                new TestSession(),
                null
        );

        assertEquals(Boolean.TRUE, output.get("success"));
        assertEquals(timestamp, memory.timestamp);
    }

    @Test
    void emptyMessagesRaiseInputParamError() {
        MemoryWriteExecutable executable = new MemoryWriteExecutable(new MemoryWriteCompConfig(new RecordingMemory()));

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of("messages", List.of()), new TestSession(), null));

        assertEquals(StatusCode.COMPONENT_MEMORY_WRITE_INPUT_PARAM_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("Messages list cannot be empty"));
    }

    @Test
    void missingMessagesRaiseInputParamError() {
        BaseError error = assertThrows(BaseError.class,
                () -> MemoryWriteExecutable.validateInputs(Map.of()));

        assertEquals(StatusCode.COMPONENT_MEMORY_WRITE_INPUT_PARAM_ERROR.getCode(), error.getCode());
    }

    @Test
    void memoryFailureRaisesInvokeCallFailed() {
        RecordingMemory memory = new RecordingMemory();
        memory.addMessagesError = new IllegalStateException("DB connection failed");
        MemoryWriteExecutable executable = new MemoryWriteExecutable(new MemoryWriteCompConfig(memory));

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of("messages", List.of(new UserMessage("Test"))), new TestSession(), null));

        assertEquals(StatusCode.COMPONENT_MEMORY_WRITE_INVOKE_CALL_FAILED.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("Memory write call failed"));
    }

    @Test
    void componentAddsExecutableToGraph() {
        MemoryWriteComponent component = new MemoryWriteComponent(new MemoryWriteCompConfig(new RecordingMemory()));
        RecordingGraph graph = new RecordingGraph();

        component.addComponent(graph, "memory_write_node", true);

        assertEquals("memory_write_node", graph.nodeId);
        assertTrue(graph.waitForAll);
        assertInstanceOf(MemoryWriteExecutable.class, graph.node);
    }

    private static final class RecordingMemory extends LongTermMemory {
        private boolean addMessagesCalled;
        private List<BaseMessage> messages;
        private AgentMemoryConfig agentConfig;
        private String userId;
        private String scopeId;
        private String sessionId;
        private ZonedDateTime timestamp;
        private boolean genMem;
        private int genMemWithHistoryMsgNum;
        private RuntimeException addMessagesError;

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
            this.messages = messages;
            this.agentConfig = agentConfig;
            this.userId = userId;
            this.scopeId = scopeId;
            this.sessionId = sessionId;
            this.timestamp = timestamp;
            this.genMem = genMem;
            this.genMemWithHistoryMsgNum = genMemWithHistoryMsgNum;
            if (addMessagesError != null) {
                return CompletableFuture.failedFuture(addMessagesError);
            }
            return CompletableFuture.completedFuture(new AddMemResult());
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
            return "memory_write_node";
        }
    }
}
