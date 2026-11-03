/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.SessionStateAccess;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code BranchRouter.__call__} in
 * {@code openjiuwen/core/workflow/components/flow/branch_router.py}.
 */
class BranchRouterStateOrderPythonParityTest {

    @Test
    void successfulRouteDoesNotWriteOrReorderSessionState() {
        RecordingSession session = new RecordingSession(sampleState());
        BranchRouter router = new BranchRouter();
        router.addBranch(() -> true, "matched", "success");
        router.setSession(session);

        assertEquals(List.of("matched"), router.route());

        assertStateUnchanged(session);
    }

    @Test
    void tracedRouteDoesNotWriteOrReorderSessionState() {
        RecordingSession session = new RecordingSession(sampleState());
        BranchRouter router = new BranchRouter(true);
        router.addBranch(() -> true, "matched", "trace-success");
        router.setSession(session);

        assertEquals(List.of("matched"), router.route());

        assertStateUnchanged(session);
    }

    @Test
    void noMatchFailureDoesNotWriteOrReorderSessionState() {
        RecordingSession session = new RecordingSession(sampleState());
        BranchRouter router = new BranchRouter();
        router.addBranch(() -> false, "never", "no-match");
        router.setSession(session);

        BaseError error = assertThrows(BaseError.class, router::route);

        assertEquals(StatusCode.COMPONENT_BRANCH_EXECUTION_ERROR, error.getStatus());
        assertStateUnchanged(session);
    }

    private static void assertStateUnchanged(RecordingSession session) {
        assertEquals(0, session.state.setStateCalls);
        assertEquals(session.state.beforeRoute, session.state.values);
        assertEquals(List.of("io_zeta", "io_alpha"), partitionOrder(session, "io_state"));
        assertEquals(List.of("global_middle", "global_alpha"), partitionOrder(session, "global_state"));
        assertEquals(List.of("comp_zeta", "comp_beta"), partitionOrder(session, "comp_state"));
        assertEquals(List.of("workflow_middle", "workflow_alpha"), partitionOrder(session, "workflow_state"));
        Map<String, Object> nested = castMap(castMap(session.state.values.get("io_state")).get("io_zeta"));
        assertEquals(List.of("nested_zeta", "nested_alpha", "items"), List.copyOf(nested.keySet()));
        List<?> items = (List<?>) nested.get("items");
        assertEquals(List.of("item_zeta", "item_alpha"), List.copyOf(castMap(items.get(0)).keySet()));
    }

    private static List<String> partitionOrder(RecordingSession session, String partition) {
        return List.copyOf(castMap(session.state.values.get(partition)).keySet());
    }

    private static Map<String, Object> sampleState() {
        return linkedMap(
                "io_state", linkedMap(
                        "io_zeta", linkedMap(
                                "nested_zeta", 1,
                                "nested_alpha", 2,
                                "items", List.of(linkedMap("item_zeta", 3, "item_alpha", 4))),
                        "io_alpha", true),
                "global_state", linkedMap("global_middle", 5, "global_alpha", 6),
                "comp_state", linkedMap("comp_zeta", 7, "comp_beta", 8),
                "workflow_state", linkedMap("workflow_middle", 9, "workflow_alpha", 10));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> linkedMap(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return result;
    }

    private static final class RecordingSession extends BaseSession {
        private final RecordingState state;
        private final RecordingTracer tracer = new RecordingTracer();

        private RecordingSession(Map<String, Object> state) {
            this.state = new RecordingState(state);
        }

        @Override
        public SessionStateAccess state() {
            return state;
        }

        @Override
        public RecordingTracer tracer() {
            return tracer;
        }
    }

    private static final class RecordingState implements SessionStateAccess {
        private final Map<String, Object> values;
        private final Map<String, Object> beforeRoute;
        private int setStateCalls;

        private RecordingState(Map<String, Object> values) {
            this.values = values;
            this.beforeRoute = deepCopyMap(values);
        }

        @Override
        public Object get(Object key) {
            return values.get(String.valueOf(key));
        }

        @Override
        public void update(Map<String, Object> data) {
            values.putAll(data);
        }

        @Override
        public Map<String, Object> getState() {
            return values;
        }

        @Override
        public void setState(Map<String, Object> state) {
            setStateCalls++;
            values.clear();
            values.putAll(state);
        }
    }

    public static final class RecordingTracer {
        public void traceComponentBegin(BaseSession session) {
        }

        public void traceComponentInputs(BaseSession session, Map<String, Object> inputs) {
        }

        public void traceComponentOutputs(BaseSession session, Map<String, Object> outputs) {
        }

        public void traceComponentDone(BaseSession session) {
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                copy.put(entry.getKey(), deepCopyMap((Map<String, Object>) map));
            } else if (value instanceof List<?> list) {
                copy.put(entry.getKey(), list.stream().map(BranchRouterStateOrderPythonParityTest::deepCopy).toList());
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            return deepCopyMap((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(BranchRouterStateOrderPythonParityTest::deepCopy).toList();
        }
        return value;
    }
}
