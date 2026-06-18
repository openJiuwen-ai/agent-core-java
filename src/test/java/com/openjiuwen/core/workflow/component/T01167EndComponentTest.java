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
