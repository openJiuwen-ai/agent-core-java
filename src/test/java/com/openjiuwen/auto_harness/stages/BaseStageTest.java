package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.schema.StageResult;
import com.openjiuwen.auto_harness.schema.StageSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code BaseStage}, {@code SessionStage}, and {@code TaskStage} in
 * {@code openjiuwen.auto_harness.stages.base}.
 */
class BaseStageTest {

    @Test
    void defaultMetadataMirrorsPythonClassAttributes() {
        MinimalStage stage = new MinimalStage();

        assertEquals("", stage.description());
        assertEquals(List.of(), stage.consumes());
        assertEquals(List.of(), stage.produces());
        assertEquals("session", stage.scope());

        StageSpec spec = stage.spec();
        assertEquals("minimal", spec.getName());
        assertEquals(MinimalStage.class, spec.getStageClass());
        assertEquals("", spec.getDescription());
        assertEquals(List.of(), spec.getConsumes());
        assertEquals(List.of(), spec.getProduces());
        assertEquals("session", spec.getScope());
    }

    @Test
    void scopedStageDefaultsMirrorPythonSubclasses() {
        assertEquals("session", new MinimalSessionStage().scope());
        assertEquals("task", new MinimalTaskStage().scope());
    }

    @Test
    void executeEmitsRunResultWhenPresent() {
        MinimalStage stage = new MinimalStage();
        List<Object> events = new ArrayList<>();

        stage.execute((SessionContext) null, events::add);

        assertEquals(1, events.size());
        assertEquals("success", ((StageResult) events.get(0)).getStatus());
    }

    private static class MinimalStage extends BaseStage {
        @Override
        public String name() {
            return "minimal";
        }

        @Override
        public StageResult run(Object context) {
            return new StageResult();
        }
    }

    private static final class MinimalSessionStage extends SessionStage {
        @Override
        public String name() {
            return "session";
        }

        @Override
        public StageResult run(Object context) {
            return new StageResult();
        }
    }

    private static final class MinimalTaskStage extends TaskStage {
        @Override
        public String name() {
            return "task";
        }

        @Override
        public StageResult run(Object context) {
            return new StageResult();
        }
    }
}
