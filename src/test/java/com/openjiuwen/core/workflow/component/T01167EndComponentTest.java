/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.stream.OutputSchema;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity checks for {@link End}.
 *
 * <p>Mirrors Python's {@code End}, {@code TemplateProcessor}, and {@code TemplateUtils} in
 * {@code openjiuwen/core/workflow/components/flow/end_comp.py}.</p>
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.component.test_end} in
 * {@code tests/unit_tests/core/component/test_end.py}.</p>
 */
class T01167EndComponentTest {

    @Test
    void invokeWithoutTemplateFiltersNullValuesButKeepsEmptyOutputMap() {
        End end = new End();
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("kept", "value");
        inputs.put("none", null);

        Map<?, ?> result = assertMap(end.invoke(inputs, new TestSession(), null));

        assertEquals(Map.of("kept", "value"), result.get("output"));

        Map<String, Object> onlyNull = new LinkedHashMap<>();
        onlyNull.put("none", null);

        Map<?, ?> emptyResult = assertMap(end.invoke(onlyNull, new TestSession(), null));

        assertTrue(assertMap(emptyResult.get("output")).isEmpty());
    }

    @Test
    void streamWithoutTemplatePreservesNullFieldsAndScalarInputs() {
        End end = new End();
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("none", null);
        inputs.put("value", "ok");

        List<Object> frames = collect(end.stream(inputs, new TestSession(), null));

        Map<?, ?> firstOutput = assertMap(assertMap(frames.get(0)).get("output"));
        assertTrue(firstOutput.containsKey("none"));
        assertNull(firstOutput.get("none"));
        assertEquals(Map.of("value", "ok"), assertMap(frames.get(1)).get("output"));

        List<Object> scalarFrames = collect(end.stream("plain", new TestSession(), null));

        assertEquals(Map.of("output", "plain"), scalarFrames.get(0));
        assertFalse(end.stream(null, new TestSession(), null).hasNext());
    }

    @Test
    void transformAndCollectUsePythonLeafPathFormatting() {
        End end = new End();

        List<Object> scalarFrames = collect(end.transform("tail", new TestSession(), null));

        assertEquals(Map.of("output", Map.of("", "tail")), scalarFrames.get(0));

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("branch", List.of("a", "b").iterator());

        Map<?, ?> collected = assertMap(end.collect(inputs, new TestSession(), null));

        assertEquals(List.of(Map.of("branch", "a"), Map.of("branch", "b")), collected.get("output"));
    }

    @Test
    void templateStreamBuildsOutputSchemaFrames() {
        End end = new End(Map.of("responseTemplate", "Hello {{name}}!"));

        List<Object> frames = collect(end.stream(Map.of("name", "Ada"), new TestSession(), null));

        OutputSchema first = assertInstanceOf(OutputSchema.class, frames.get(0));
        OutputSchema second = assertInstanceOf(OutputSchema.class, frames.get(1));
        OutputSchema third = assertInstanceOf(OutputSchema.class, frames.get(2));
        assertEquals(Constant.END_NODE_STREAM, first.getType());
        assertEquals(0, first.getIndex());
        assertEquals(Map.of("response", "Hello "), first.getPayload());
        assertEquals(Map.of("response", "Ada"), second.getPayload());
        assertEquals(Map.of("response", "!"), third.getPayload());
    }

    @Test
    void templateUtilsKeepsPythonSafeSubstituteSemantics() {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("name", "Ada");
        inputs.put("user", "root");
        inputs.put("none", null);

        assertEquals("Hello Ada, missing $missing",
                TemplateUtils.renderTemplate("Hello {{name}}, missing {{missing}}", inputs));
        assertEquals("root.name", TemplateUtils.renderTemplate("{{user.name}}", inputs));
        assertEquals("None", TemplateUtils.renderTemplate("{{none}}", inputs));
    }

    @Test
    void simpleTemplateWorkflowRendersResponseEnvelope() {
        End end = new End(Map.of("responseTemplate", "hello:{{end_input}}"));

        Map<?, ?> result = assertMap(end.invoke(Map.of("end_input", "haha"), new TestSession(), null));

        assertEquals("hello:haha", result.get("response"));
    }

    @Test
    void endInvokeTemplateRendersTwoParameters() {
        End end = new End(Map.of("response_template", "渲染结果:{{param1}},{{param2}}"));

        Map<?, ?> result = assertMap(end.invoke(
                Map.of("param1", "你好", "param2", "杭州"),
                new TestSession(),
                null));

        assertEquals("渲染结果:你好,杭州", result.get("response"));
    }

    @Test
    void endInvokeNoTemplateReturnsOutputEnvelope() {
        End end = new End();

        Map<?, ?> result = assertMap(end.invoke(
                Map.of("param1", "你好", "param2", "杭州"),
                new TestSession(),
                null));

        assertEquals(Map.of("param1", "你好", "param2", "杭州"), result.get("output"));
    }

    @Test
    void endStreamTemplateRendersChineseChunks() {
        End end = new End(Map.of("responseTemplate", "渲染结果:{{param1}},{{param2}}"));

        List<Object> frames = collect(end.stream(
                Map.of("param1", "你好", "param2", "杭州"),
                new TestSession(),
                null));

        assertOutputSchema(frames.get(0), 0, "渲染结果:");
        assertOutputSchema(frames.get(1), 1, "你好");
        assertOutputSchema(frames.get(2), 2, ",");
        assertOutputSchema(frames.get(3), 3, "杭州");
    }

    @Test
    void endStreamNoTemplateReturnsOneFramePerField() {
        End end = new End();
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("param1", "你好");
        inputs.put("param2", "杭州");

        List<Object> frames = collect(end.stream(inputs, new TestSession(), null));

        assertEquals(Map.of("output", Map.of("param1", "你好")), frames.get(0));
        assertEquals(Map.of("output", Map.of("param2", "杭州")), frames.get(1));
    }

    @Test
    void endTransformTemplateYieldsResponseFrames() {
        End end = new End(Map.of("responseTemplate", "渲染结果:{{param1}},{{param2}}"));

        List<Object> frames = collect(end.transform(
                Map.of("param1", "你好", "param2", "杭州"),
                new TestSession(),
                null));

        assertOutputSchema(frames.get(0), 0, "渲染结果:");
        assertOutputSchema(frames.get(1), 1, "你好");
        assertOutputSchema(frames.get(2), 2, ",");
        assertOutputSchema(frames.get(3), 3, "杭州");
    }

    @Test
    void simpleOutputSchemaWorkflowUsesOutputEnvelope() {
        End end = new End();

        Map<?, ?> result = assertMap(end.invoke(Map.of("end_input", "haha"), new TestSession(), null));

        assertEquals(Map.of("end_input", "haha"), result.get("output"));
    }

    @Test
    void endStreamWorkflowEmitsStaticAndNumericTemplateParts() {
        End end = new End(Map.of("responseTemplate", "hello:{{end_input}}"));

        List<Object> frames = collect(end.stream(Map.of("end_input", 1), new TestSession(), null));

        assertOutputSchema(frames.get(0), 0, "hello:");
        assertOutputSchema(frames.get(1), 1, 1);
    }

    @Test
    void endBatchStreamWorkflowExpandsIteratorVariable() {
        End end = new End(Map.of("responseTemplate", "hello:{{value}}"));

        List<Object> frames = collect(end.stream(
                Map.of("value", List.of(1, 2).iterator()),
                new TestSession(),
                null));

        assertOutputSchema(frames.get(0), 0, "hello:");
        assertOutputSchema(frames.get(1), 1, 1);
        assertOutputSchema(frames.get(2), 2, 2);
    }

    @Test
    void endNoStreamingNoTemplateCollectsStreamInputs() {
        End end = new End();
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("a", List.of(1).iterator());
        inputs.put("b", List.of(2).iterator());

        Map<?, ?> result = assertMap(end.collect(inputs, new TestSession(), null));

        assertEquals(List.of(Map.of("a", 1), Map.of("b", 2)), result.get("output"));
    }

    @Test
    void endTemplateWithUnmappedVariableRendersStaticTextOnly() {
        End end = new End(Map.of("responseTemplate", "输出:{{custom.result}}"));

        Map<?, ?> result = assertMap(end.invoke(Map.of("a", 1, "b", 2), new TestSession(), null));

        assertEquals("输出:", result.get("response"));
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    private static Map<?, ?> assertMap(Object value) {
        return assertInstanceOf(Map.class, value);
    }

    private static void assertOutputSchema(Object value, int index, Object response) {
        OutputSchema schema = assertInstanceOf(OutputSchema.class, value);
        assertEquals(Constant.END_NODE_STREAM, schema.getType());
        assertEquals(index, schema.getIndex());
        assertEquals(Map.of("response", response), schema.getPayload());
    }

    private static final class TestSession extends BaseSession {
        public Object getEnv(String key) {
            if (SessionConstants.END_COMP_TEMPLATE_BATCH_READER_TIMEOUT_KEY.equals(key)
                    || SessionConstants.END_COMP_TEMPLATE_RENDER_POSITION_TIMEOUT_KEY.equals(key)) {
                return 0.01d;
            }
            return null;
        }

        public String getComponentId() {
            return "end";
        }

        public String getExecutableId() {
            return "end";
        }

        @Override
        public String sessionId() {
            return "session";
        }
    }
}
