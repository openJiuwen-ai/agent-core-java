/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.core.component;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.component.End;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Mirrors Python's {@code test_end.py} in
 * {@code tests.unit_tests.core.component}.
 */
@Tag("unit-test")
class TestEnd {

    @Test
    @DisplayName("simple template workflow output")
    void testSimpleTemplateWorkflow() {
        Object result = new End(Map.of("responseTemplate", "hello:{{end_input}}"))
                .invoke(Map.of("end_input", "haha"), null, null);

        assertEquals(Map.of("response", "hello:haha"), result);
    }

    @Test
    @DisplayName("response_template alias renders inputs")
    void testEndInvokeTemplate() {
        Object result = new End(Map.of("response_template", "渲染结果:{{param1}},{{param2}}"))
                .invoke(Map.of("param1", "你好", "param2", "杭州"), null, null);

        assertEquals(Map.of("response", "渲染结果:你好,杭州"), result);
    }

    @Test
    @DisplayName("no template returns output envelope")
    void testEndInvokeNoTemplate() {
        Object result = new End(Map.of())
                .invoke(Map.of("param1", "你好", "param2", "杭州"), null, null);

        assertEquals(Map.of("output", Map.of("param1", "你好", "param2", "杭州")), result);
    }

    @Test
    @DisplayName("streaming template yields static and variable chunks")
    void testEndStreamTemplate() {
        List<Object> streams = collect(new End(Map.of("responseTemplate", "渲染结果:{{param1}},{{param2}}"))
                .stream(Map.of("param1", "你好", "param2", "杭州"), null, null));

        assertEquals(List.of(
                new OutputSchema(Constant.END_NODE_STREAM, 0, Map.of("response", "渲染结果:")),
                new OutputSchema(Constant.END_NODE_STREAM, 1, Map.of("response", "你好")),
                new OutputSchema(Constant.END_NODE_STREAM, 2, Map.of("response", ",")),
                new OutputSchema(Constant.END_NODE_STREAM, 3, Map.of("response", "杭州"))
        ), streams);
    }

    @Test
    @DisplayName("streaming without template yields one output frame per field")
    void testEndStreamNoTemplate() {
        Map<String, Object> inputs = linkedMap("param1", "你好", "param2", "杭州");
        List<Object> streams = collect(new End(Map.of()).stream(inputs, null, null));

        assertEquals(List.of(
                Map.of("output", Map.of("param1", "你好")),
                Map.of("output", Map.of("param2", "杭州"))
        ), streams);
    }

    @Test
    @DisplayName("transform template yields the same output chunks as stream")
    void testEndTransform() {
        List<Object> streams = collect(new End(Map.of("responseTemplate", "渲染结果:{{param1}},{{param2}}"))
                .transform(Map.of("param1", "你好", "param2", "杭州"), null, null));

        assertEquals(List.of(
                new OutputSchema(Constant.END_NODE_STREAM, 0, Map.of("response", "渲染结果:")),
                new OutputSchema(Constant.END_NODE_STREAM, 1, Map.of("response", "你好")),
                new OutputSchema(Constant.END_NODE_STREAM, 2, Map.of("response", ",")),
                new OutputSchema(Constant.END_NODE_STREAM, 3, Map.of("response", "杭州"))
        ), streams);
    }

    @Test
    @DisplayName("simple output schema without template")
    void testSimpleOutputSchemaWorkflow() {
        Object result = new End().invoke(Map.of("end_input", "haha"), null, null);

        assertEquals(Map.of("output", Map.of("end_input", "haha")), result);
    }

    @Test
    @DisplayName("stream workflow template chunks")
    void testEndStreamWorkflow() {
        List<Object> chunks = collect(new End(Map.of("responseTemplate", "hello:{{end_input}}"))
                .stream(Map.of("end_input", 1), null, null));

        assertEquals(List.of(
                new OutputSchema(Constant.END_NODE_STREAM, 0, Map.of("response", "hello:")),
                new OutputSchema(Constant.END_NODE_STREAM, 1, Map.of("response", 1))
        ), chunks);
    }

    @Test
    @DisplayName("batch stream workflow consumes iterator values")
    void testEndBatchStreamWorkflow() {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("value", List.of(1, 2).iterator());

        List<Object> chunks = collect(new End(Map.of("responseTemplate", "hello:{{value}}"))
                .transform(inputs, null, null));

        assertEquals(List.of(
                new OutputSchema(Constant.END_NODE_STREAM, 0, Map.of("response", "hello:")),
                new OutputSchema(Constant.END_NODE_STREAM, 1, Map.of("response", 1)),
                new OutputSchema(Constant.END_NODE_STREAM, 2, Map.of("response", 2))
        ), chunks);
    }

    @Test
    @DisplayName("collect without template preserves streamed field frames")
    void testEndNoStreamingNoTemplate() {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("a", List.of(1).iterator());
        inputs.put("b", List.of(2).iterator());

        Object result = new End().collect(inputs, null, null);

        assertEquals(Map.of("output", List.of(Map.of("a", 1), Map.of("b", 2))), result);
    }

    @Test
    @DisplayName("streaming invoke with missing template variable emits static prefix")
    void testEndTemplate001() {
        WorkflowOutput result = new WorkflowOutput(
                collect(new End(Map.of("responseTemplate", "输出:{{custom.result}}"))
                        .stream(Map.of(), null, null)),
                WorkflowExecutionState.COMPLETED);

        List<?> frames = (List<?>) result.getResult();
        assertFalse(frames.isEmpty());
        assertEquals(WorkflowExecutionState.COMPLETED, result.getState());
        assertEquals(new OutputSchema(Constant.END_NODE_STREAM, 0, Map.of("response", "输出:")), frames.get(0));
    }

    @Test
    @DisplayName("stream() with missing template variable emits static prefix")
    void testEndTemplate002() {
        List<Object> chunks = collect(new End(Map.of("responseTemplate", "输出是:{{custom.result}}"))
                .stream(Map.of(), null, null));

        assertFalse(chunks.isEmpty());
        assertEquals(new OutputSchema(Constant.END_NODE_STREAM, 0, Map.of("response", "输出是:")), chunks.get(0));
    }

    @Test
    @DisplayName("invoke with missing template variable renders static prefix")
    void testEndTemplate013() {
        WorkflowOutput result = new WorkflowOutput(
                new End(Map.of("responseTemplate", "输出:{{custom.result}}"))
                        .invoke(Map.of(), null, null),
                WorkflowExecutionState.COMPLETED);

        assertEquals(WorkflowExecutionState.COMPLETED, result.getState());
        assertNotNull(result.getResult());
        assertEquals(Map.of("response", "输出:"), result.getResult());
    }

    @Test
    @DisplayName("stream final chunk mirrors non-streaming static-prefix result")
    void testEndTemplate014() {
        WorkflowOutput chunk = new WorkflowOutput(
                new End(Map.of("responseTemplate", "输出:{{custom.result}}"))
                        .invoke(Map.of(), null, null),
                WorkflowExecutionState.COMPLETED);

        assertEquals(Map.of("response", "输出:"), chunk.getResult());
    }

    @Test
    @DisplayName("stream inputs render a complete template")
    void testEndTemplate017() {
        Object result = new End(Map.of("responseTemplate", "输出:{{a}}{{op}}{{b}}={{end_result}}"))
                .invoke(Map.of("op", "+", "a", 1, "b", 2, "end_result", 3), null, null);

        assertEquals(Map.of("response", "输出:1+2=3"), result);
    }

    @Test
    @DisplayName("invoke with stream-input-style values renders a complete template")
    void testEndTemplate019() {
        Object result = new End(Map.of("responseTemplate", "输出:{{a}}{{op}}{{b}}={{end_result}}"))
                .invoke(Map.of("op", "+", "a", 1, "b", 2, "end_result", 3), null, null);

        assertEquals(Map.of("response", "输出:1+2=3"), result);
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static Map<String, Object> linkedMap(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }
}
