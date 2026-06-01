package com.openjiuwen.auto_harness.pipelines;

import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.schema.PipelineSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code BasePipeline} in {@code openjiuwen.auto_harness.pipelines.base}.
 */
class BasePipelineTest {

    @Test
    void defaultMetadataMirrorsPythonClassAttributes() {
        MinimalPipeline pipeline = new MinimalPipeline();

        assertEquals("", pipeline.description());
        assertEquals(List.of(), pipeline.expectedOutputs());

        PipelineSpec spec = pipeline.spec();
        assertEquals("minimal", spec.getName());
        assertEquals(MinimalPipeline.class, spec.getPipelineClass());
        assertEquals("", spec.getDescription());
        assertEquals(List.of(), spec.getExpectedOutputs());
    }

    @Test
    void baseExecuteRaisesLikePythonNotImplementedStream() {
        MinimalPipeline pipeline = new MinimalPipeline();

        assertThrows(UnsupportedOperationException.class,
                () -> pipeline.execute((SessionContext) null, ignored -> { }));
        assertThrows(UnsupportedOperationException.class,
                () -> pipeline.execute((TaskContext) null, ignored -> { }));
    }

    private static final class MinimalPipeline extends BasePipeline {
        @Override
        public String name() {
            return "minimal";
        }
    }
}
