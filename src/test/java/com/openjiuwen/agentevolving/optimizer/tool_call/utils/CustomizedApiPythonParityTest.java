/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_customized_api} module in
 * {@code tests/unit_tests/agent_evolving/optimizer/tool_call/test_customized_api.py}.
 */
class CustomizedApiPythonParityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    private Path tempDir;

    @Test
    void simpleApiWrapperCallSuccessNotFoundAndException() throws Exception {
        Function<Map<String, Object>, Object> okFunction = params -> Map.of("echo", params);
        SimpleApiWrapper wrapper = new SimpleApiWrapper("ok_fn", Map.of("ok_fn", okFunction));

        Object[] result = wrapper.call(Map.of("name", "ok_fn"), Map.of("a", 1));

        assertEquals(0, result[1]);
        assertEquals(Map.of("echo", Map.of("a", 1)), payload(result[0]).get("response"));

        SimpleApiWrapper missing = new SimpleApiWrapper("missing", Map.of("ok_fn", okFunction));
        Object[] missingResult = missing.call(Map.of("name", "missing"), Map.of("a", 1));

        assertEquals(12, missingResult[1]);
        assertTrue(String.valueOf(payload(missingResult[0]).get("error")).contains("no function"));

        Function<Map<String, Object>, Object> badFunction = params -> {
            throw new IllegalArgumentException("boom");
        };
        SimpleApiWrapper bad = new SimpleApiWrapper("bad_fn", Map.of("bad_fn", badFunction));
        Object[] badResult = bad.call(Map.of("name", "bad_fn"), Map.of("a", 1));

        assertEquals(12, badResult[1]);
        assertTrue(String.valueOf(payload(badResult[0]).get("error")).contains("boom"));
    }

    @Test
    void simpleApiWrapperLoadModuleAndAddFunction() throws Exception {
        Path modulePath = tempDir.resolve("toy_module.py");
        Files.writeString(modulePath, "def ping(params):\n    return {'pong': params.get('x')}\n");
        SimpleApiWrapper wrapper = new SimpleApiWrapper(
                modulePath.toString(),
                "ping",
                Map.of(),
                loadedPath -> new SimpleApiWrapper.LoadedToolModule(
                        loadedPath,
                        Map.of("ping", (Function<Map<String, Object>, Object>) params -> Map.of("pong", params.get("x")))
                )
        );

        Object[] result = wrapper.call(Map.of("name", "ping"), Map.of("x", 9));

        assertEquals(0, result[1]);
        assertEquals(Map.of("pong", 9), payload(result[0]).get("response"));

        wrapper.addFunction("sum2", (Function<Map<String, Object>, Object>) params ->
                ((Number) params.get("a")).intValue() + ((Number) params.get("b")).intValue());
        wrapper.setFnCallName("sum2");
        Object[] sumResult = wrapper.call(Map.of("name", "sum2"), Map.of("a", 1, "b", 2));

        assertEquals(0, sumResult[1]);
        assertEquals(3, payload(sumResult[0]).get("response"));
    }

    @Test
    void simpleApiWrapperFromCallable() throws Exception {
        SimpleApiWrapperFromCallable wrapper = new SimpleApiWrapperFromCallable(
                (Function<Map<String, Object>, Object>) params -> ((Number) params.get("v")).intValue() * 2,
                "f",
                Map.of()
        );

        Object[] result = wrapper.call(Map.of("name", "f"), Map.of("v", 3));

        assertEquals(0, result[1]);
        assertEquals(6, payload(result[0]).get("response"));
    }

    @Test
    void loadCustomDataJsonlAndJson() throws Exception {
        Path jsonl = tempDir.resolve("x.jsonl");
        Files.writeString(jsonl,
                "{\"function\":{\"name\":\"f1\"}}\n"
                        + "{\"function\":[{\"name\":\"f2\"},{\"name\":\"f3\"}]}");
        List<Map<String, Object>> tools = SimpleApiWrapper.loadCustomData(jsonl.toString(), null);

        assertEquals(List.of("f1", "f2", "f3"), functionNames(tools));

        Path asList = tempDir.resolve("list.json");
        Files.writeString(asList, "[{\"function\":{\"name\":\"a\"}},{\"name\":\"b\"}]");
        List<Map<String, Object>> listTools = SimpleApiWrapper.loadCustomData(asList.toString(), null);

        assertEquals(List.of("a", "b"), functionNames(listTools));

        Path asObject = tempDir.resolve("obj.json");
        Files.writeString(asObject, "{\"functions\":[{\"name\":\"c\"}]}");
        List<Map<String, Object>> objectTools = SimpleApiWrapper.loadCustomData(asObject.toString(), null);

        assertEquals(List.of("c"), functionNames(objectTools));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Object raw) throws Exception {
        return OBJECT_MAPPER.readValue(String.valueOf(raw), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static List<String> functionNames(List<Map<String, Object>> tools) {
        return tools.stream()
                .map(tool -> (Map<String, Object>) tool.get("function"))
                .map(function -> String.valueOf(function.get("name")))
                .toList();
    }
}
