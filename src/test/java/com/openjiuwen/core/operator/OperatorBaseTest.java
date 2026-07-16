/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.session.Session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;

/**
 * Port of Python operator base tests.
 */
class OperatorBaseTest {
    @Test
    @DisplayName("TunableSpec initializes with all params")
    void testTunableSpecAllParams() {
        TunableSpec spec =
            new TunableSpec("temperature", "continuous", "model.temperature", Map.of("min", 0.0, "max", 1.0));

        assertEquals("temperature", spec.name());
        assertEquals("continuous", spec.kind());
        assertEquals("model.temperature", spec.path());
        assertEquals(Map.of("min", 0.0, "max", 1.0), spec.constraint());
    }

    @Test
    @DisplayName("TunableSpec initializes with minimal params")
    void testTunableSpecMinimalParams() {
        TunableSpec spec = new TunableSpec("prompt", "prompt", "prompt");

        assertEquals("prompt", spec.name());
        assertEquals("prompt", spec.kind());
        assertEquals("prompt", spec.path());
        assertNull(spec.constraint());
    }

    @Test
    @DisplayName("Operator remains abstract and stream defaults to unsupported")
    void testOperatorAbstractContract() throws Exception {
        assertTrue(Modifier.isAbstract(Operator.class.getModifiers()));

        Method getOperatorId = Operator.class.getDeclaredMethod("getOperatorId");
        Method getTunables = Operator.class.getDeclaredMethod("getTunables");
        Method setParameter = Operator.class.getDeclaredMethod("setParameter", String.class, Object.class);
        Method getState = Operator.class.getDeclaredMethod("getState");
        Method loadState = Operator.class.getDeclaredMethod("loadState", Map.class);
        Method invoke = Operator.class.getDeclaredMethod("invoke", Map.class, Session.class, Map.class);

        assertTrue(Modifier.isAbstract(getOperatorId.getModifiers()));
        assertTrue(Modifier.isAbstract(getTunables.getModifiers()));
        assertTrue(Modifier.isAbstract(setParameter.getModifiers()));
        assertTrue(Modifier.isAbstract(getState.getModifiers()));
        assertTrue(Modifier.isAbstract(loadState.getModifiers()));
        assertTrue(Modifier.isAbstract(invoke.getModifiers()));

        ConcreteOperator operator = new ConcreteOperator();
        UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class, () -> operator
                .stream(Collections.emptyMap(), new OperatorTestSupport.TrackingSession(), Collections.emptyMap()));
        assertEquals("stream not implemented", error.getMessage());
    }

    private static final class ConcreteOperator extends Operator {
        @Override
        public String getOperatorId() {
            return "test_operator";
        }

        @Override
        public Map<String, TunableSpec> getTunables() {
            return Collections.emptyMap();
        }

        @Override
        public void setParameter(String target, Object value) {
        }

        @Override
        public Map<String, Object> getState() {
            return Collections.emptyMap();
        }

        @Override
        public void loadState(Map<String, Object> state) {
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Session session, Map<String, Object> kwargs) {
            return "result";
        }
    }
}
