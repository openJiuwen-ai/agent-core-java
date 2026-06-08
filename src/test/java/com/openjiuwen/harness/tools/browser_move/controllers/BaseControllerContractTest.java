/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BaseControllerContractTest {

    @Test
    void contractSupportsRegistrationAndDispatch() {
        TestController controller = new TestController();
        controller.registerActionSpec("snapshot", "take snapshot", "capture browser state", Map.of("depth", "int"));
        controller.registerAction("snapshot", Map.of("ok", true), true);

        assertEquals(List.of("snapshot"), controller.listActions());
        assertTrue(controller.describeActions().containsKey("snapshot"));
        assertEquals(Map.of("ok", true), controller.runAction("snapshot", "session-1", "req-1", Map.of()));
    }

    private static final class TestController implements BaseController {

        private final Map<String, Object> handlers = new LinkedHashMap<>();
        private final Map<String, Map<String, Object>> specs = new LinkedHashMap<>();

        @Override
        public void bindRuntime(Object runtime) {
        }

        @Override
        public void bindRuntimeRunner(Object runner) {
        }

        @Override
        public void clearRuntimeRunner() {
        }

        @Override
        public void bindCodeExecutor(Object executor) {
        }

        @Override
        public void clearCodeExecutor() {
        }

        @Override
        public void registerAction(String name, Object handler, boolean overwrite) {
            if (overwrite || !handlers.containsKey(name)) {
                handlers.put(name, handler);
            }
        }

        @Override
        public void registerActionSpec(String name, String summary, String whenToUse, Map<String, String> params) {
            Map<String, Object> spec = new HashMap<>();
            spec.put("summary", summary);
            spec.put("when_to_use", whenToUse);
            spec.put("params", params);
            specs.put(name, spec);
        }

        @Override
        public List<String> listActions() {
            return new ArrayList<>(handlers.keySet());
        }

        @Override
        public Map<String, Map<String, Object>> describeActions() {
            return Map.copyOf(specs);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> runAction(String action, String sessionId, String requestId, Map<String, Object> kwargs) {
            return (Map<String, Object>) handlers.get(action);
        }
    }
}
