/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.operator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for operator base models.
 *
 * <p>Mirrors Python's {@code test_base.py} in
 * {@code tests.unit_tests.core.operator}.</p>
 */
class OperatorBaseTest {

    @Test
    @DisplayName("TunableSpec initializes with all params")
    void testTunableSpecAllParams() {
        TunableSpec spec = new TunableSpec(
                "temperature",
                "continuous",
                "model.temperature",
                Map.of("min", 0.0, "max", 1.0));

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
    @DisplayName("TunableSpec record rejects new fields")
    void testTunableSpecSlotsRestriction() {
        assertEquals(4, TunableSpec.class.getRecordComponents().length);
        assertFalse(hasField(TunableSpec.class, "new_attr"));
    }

    @Test
    @DisplayName("operator_id property is abstract")
    void testOperatorIdPropertyIsAbstract() throws Exception {
        Method getOperatorId = Operator.class.getDeclaredMethod("getOperatorId");
        assertTrue(Modifier.isAbstract(Operator.class.getModifiers()));
        assertTrue(Modifier.isAbstract(getOperatorId.getModifiers()));
    }

    @Test
    @DisplayName("get_tunables is abstract")
    void testGetTunablesIsAbstract() throws Exception {
        Method getTunables = Operator.class.getDeclaredMethod("getTunables");
        assertTrue(Modifier.isAbstract(getTunables.getModifiers()));
    }

    @Test
    @DisplayName("set_parameter is abstract")
    void testSetParameterIsAbstract() throws Exception {
        Method setParameter = Operator.class.getDeclaredMethod("setParameter", String.class, Object.class);
        assertTrue(Modifier.isAbstract(setParameter.getModifiers()));
    }

    @Test
    @DisplayName("get_state is abstract")
    void testGetStateIsAbstract() throws Exception {
        Method getState = Operator.class.getDeclaredMethod("getState");
        assertTrue(Modifier.isAbstract(getState.getModifiers()));
    }

    @Test
    @DisplayName("load_state is abstract")
    void testLoadStateIsAbstract() throws Exception {
        Method loadState = Operator.class.getDeclaredMethod("loadState", Map.class);
        assertTrue(Modifier.isAbstract(loadState.getModifiers()));
    }

    @Test
    @DisplayName("Operator is not executable")
    void testOperatorIsNotExecutable() {
        assertFalse(hasMethod(Operator.class, "invoke"));
        assertFalse(hasMethod(Operator.class, "stream"));
    }

    private static boolean hasMethod(Class<?> type, String name) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasField(Class<?> type, String name) {
        for (java.lang.reflect.Field field : type.getDeclaredFields()) {
            if (field.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
