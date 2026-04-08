/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.graph;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.pregel.Pregel;
import com.openjiuwen.core.graph.pregel.PregelConfig;
import com.openjiuwen.core.graph.store.InMemoryStore;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.InMemoryState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CompiledGraph}.
 */
class CompiledGraphTest {

    @Test
    @DisplayName("invoke commits user inputs and calls workflow checkpoint hooks")
    void testInvokeCommitsUserInputsAndRunsCheckpointHooks() {
        RecordingPregel pregel = new RecordingPregel(Map.of("result", 1), null);
        RecordingCheckpointer checkpointer = new RecordingCheckpointer(false);
        WorkflowSession session = new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);

        CompiledGraph graph = new CompiledGraph(pregel, checkpointer);

        Map<String, Object> result = graph.invoke(
                Map.of(Constant.INPUTS_KEY, Map.of("question", "hello")),
                session);

        assertEquals(Map.of("result", 1), result);
        assertEquals(List.of("pre", "post"), checkpointer.calls);
        assertNull(checkpointer.preWorkflowInputs);
        assertEquals("hello", session.state().getGlobal("question"));
        assertEquals("session-1", pregel.lastConfig.getSessionId());
        assertEquals("workflow-1", pregel.lastConfig.getNs());
    }

    @Test
    @DisplayName("interactive inputs are passed to checkpointer and pregel exceptions are surfaced")
    void testInvokeWithInteractiveInput() {
        RecordingPregel pregel = new RecordingPregel(null, new IllegalStateException("boom"));
        RecordingCheckpointer checkpointer = new RecordingCheckpointer(true);
        WorkflowSession session = new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);

        CompiledGraph graph = new CompiledGraph(pregel, checkpointer);
        InteractiveInput inputs = new InteractiveInput("resume");

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> graph.invoke(Map.of(Constant.INPUTS_KEY, inputs), session));

        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertSame(inputs, checkpointer.preWorkflowInputs);
        assertInstanceOf(IllegalStateException.class, checkpointer.postException);
        assertNull(session.state().getGlobal("resume"));
    }

    private static final class RecordingPregel extends Pregel {
        private final Map<String, Object> result;
        private final Exception error;
        private PregelConfig lastConfig;

        private RecordingPregel(Map<String, Object> result, Exception error) {
            super(Map.of(), List.of(), new InMemoryStore(), null);
            this.result = result;
            this.error = error;
        }

        @Override
        public Map<String, Object> run(PregelConfig config) throws Exception {
            lastConfig = config;
            if (error != null) {
                throw error;
            }
            return result;
        }
    }

    private static final class RecordingCheckpointer extends Checkpointer {
        private final boolean rethrowException;
        private final List<String> calls = new ArrayList<>();
        private final Store store = new InMemoryStore();
        private InteractiveInput preWorkflowInputs;
        private Object postResult;
        private Exception postException;

        private RecordingCheckpointer(boolean rethrowException) {
            this.rethrowException = rethrowException;
        }

        @Override
        public void preWorkflowExecute(BaseSession session, InteractiveInput inputs) {
            calls.add("pre");
            preWorkflowInputs = inputs;
        }

        @Override
        public void postWorkflowExecute(BaseSession session, Object result, Exception exception) {
            calls.add("post");
            postResult = result;
            postException = exception;
            if (rethrowException && exception != null) {
                throw new RuntimeException(exception);
            }
        }

        @Override
        public void preAgentExecute(BaseSession session, Object inputs) {
        }

        @Override
        public void interruptAgentExecute(BaseSession session) {
        }

        @Override
        public void postAgentExecute(BaseSession session) {
        }

        @Override
        public boolean sessionExists(String sessionId) {
            return false;
        }

        @Override
        public void release(String sessionId) {
        }

        @Override
        public Store graphStore() {
            return store;
        }
    }
}
