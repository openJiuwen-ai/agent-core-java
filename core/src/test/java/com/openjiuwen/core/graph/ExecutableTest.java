package com.openjiuwen.core.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link Executable}.
 *
 * <p>Mirrors Python's {@code Executable} in
 * {@code openjiuwen/core/graph/executable.py}.</p>
 */
class ExecutableTest {

    @Test
    void defaultInvokeReportsPythonOverrideContract() {
        DemoExecutable executable = new DemoExecutable();

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> executable.onInvoke(Map.of(), null, "extra"));

        assertTrue(exception.getMessage().contains("Component 'DemoExecutable'"));
        assertTrue(exception.getMessage().contains("does not implement the on_invoke method"));
        assertTrue(exception.getMessage().contains("async def on_invoke"));
    }

    @Test
    void defaultStreamReportsPythonOverrideContract() {
        DemoExecutable executable = new DemoExecutable();

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> executable.onStream(Map.of(), null));

        assertTrue(exception.getMessage().contains("does not implement the on_stream method"));
        assertTrue(exception.getMessage().contains("AsyncIterator[Output]"));
    }

    @Test
    void defaultCollectReportsPythonOverrideContract() {
        DemoExecutable executable = new DemoExecutable();

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> executable.onCollect(Map.of(), null));

        assertTrue(exception.getMessage().contains("does not implement the on_collect method"));
        assertTrue(exception.getMessage().contains("async def on_collect"));
    }

    @Test
    void defaultTransformReportsPythonOverrideContract() {
        DemoExecutable executable = new DemoExecutable();

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> executable.onTransform(Map.of(), null));

        assertTrue(exception.getMessage().contains("does not implement the on_transform method"));
        assertTrue(exception.getMessage().contains("async def on_transform"));
    }

    @Test
    void defaultFlagsMatchPythonDefaults() {
        DemoExecutable executable = new DemoExecutable();

        assertFalse(executable.skipTrace());
        assertFalse(executable.graphInvoker());
        assertTrue(executable.postCommit());
        assertEquals("", executable.componentType());
    }

    /**
     * Mirrors Python's {@code Executable} in
     * {@code openjiuwen/core/graph/executable.py}.
     */
    private static final class DemoExecutable extends Executable<Map<String, Object>, Map<String, Object>> {
    }
}
