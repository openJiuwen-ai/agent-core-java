/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.foundation.tool.function.LocalFunction;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tool(...)} decorator behavior in
 * {@code openjiuwen/core/foundation/tool/tool.py}.
 */
class ToolDecoratorTest {

    @Test
    void directToolUsesExplicitNameAndFallbackSchema() throws Exception {
        LocalFunction add = ToolDecorator.tool("add", inputs ->
                ((Number) inputs.get("a")).intValue() + ((Number) inputs.get("b")).intValue());

        assertEquals(5, add.invoke(Map.of("a", 2, "b", 3)));
        assertEquals("add", add.getCard().getId());
        assertEquals("add", add.getCard().getName());
        assertEquals("Function add", add.getCard().getDescription());
        assertEquals(Map.of("type", "object", "properties", Map.of()), add.getCard().getInputParams());
    }

    @Test
    void configuredDecoratorOverridesNameDescriptionAndSchema() throws Exception {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query"));
        Function<Function<Map<String, Object>, Object>, LocalFunction> decorator = ToolDecorator.tool(
                ToolDecorator.Options.builder()
                        .name("search")
                        .description("Search tool")
                        .inputParams(schema)
                        .autoExtract(false)
                        .build());

        LocalFunction search = decorator.apply(inputs -> "found:" + inputs.get("query"));

        assertEquals("found:java", search.invoke(Map.of("query", "java")));
        assertEquals("search", search.getCard().getName());
        assertEquals("Search tool", search.getCard().getDescription());
        assertEquals(schema, search.getCard().getInputParams());
    }

    @Test
    void prebuiltCardIsUsedAsIsWithoutOverrides() throws Exception {
        ToolCard card = ToolCard.builder()
                .id("existing")
                .name("existing")
                .description("Existing card")
                .inputParams(Map.of("type", "object"))
                .build();

        LocalFunction tool = ToolDecorator.tool(inputs -> "ok", ToolDecorator.Options.builder().card(card).build());

        assertSame(card, tool.getCard());
        assertEquals("ok", tool.invoke(Map.of()));
    }

    @Test
    void prebuiltCardOverridesCreateNewCardAndKeepOriginalUntouched() {
        ToolCard card = ToolCard.builder()
                .id("existing")
                .name("old")
                .description("Old description")
                .inputParams(Map.of("type", "object"))
                .build();
        Map<String, Object> schema = Map.of("type", "object", "properties", Map.of("value", Map.of("type", "integer")));

        LocalFunction tool = ToolDecorator.tool(inputs -> inputs.get("value"),
                ToolDecorator.Options.builder()
                        .card(card)
                        .name("new")
                        .description("New description")
                        .inputParams(schema)
                        .build());

        assertNotSame(card, tool.getCard());
        assertEquals("existing", tool.getCard().getId());
        assertEquals("new", tool.getCard().getName());
        assertEquals("New description", tool.getCard().getDescription());
        assertEquals(schema, tool.getCard().getInputParams());
        assertEquals("old", card.getName());
        assertEquals("Old description", card.getDescription());
    }

    @Test
    void methodBasedToolAutoExtractsNameDescriptionAndSchema() throws Exception {
        SampleFunctions target = new SampleFunctions();
        Method method = SampleFunctions.class.getDeclaredMethod("submitPlan",
                String.class, Optional.class, List.class);

        LocalFunction tool = ToolDecorator.toolFromMethod(target, method);

        assertEquals("submitPlan", tool.getCard().getName());
        assertEquals("submit plan", tool.getCard().getDescription());
        assertEquals("task-1:2", tool.invoke(Map.of(
                "taskId", "task-1",
                "planId", 7,
                "tags", List.of("a", "b"))));
        Map<String, Object> schema = tool.getCard().getInputParams();
        assertEquals("submit plan", schema.get("title"));
        Map<String, Object> properties = castMap(schema.get("properties"));
        assertTrue(properties.containsKey("taskId"));
        assertTrue(properties.containsKey("planId"));
        assertTrue(properties.containsKey("tags"));
        assertTrue(Boolean.TRUE.equals(castMap(properties.get("planId")).get("nullable")));
        assertFalse(castList(schema.get("required")).contains("planId"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    /**
     * Mirrors Python callables decorated by {@code tool(...)} in
     * {@code openjiuwen/core/foundation/tool/tool.py}.
     */
    private static final class SampleFunctions {
        String submitPlan(String taskId, Optional<Integer> planId, List<String> tags) {
            return taskId + ":" + tags.size();
        }
    }
}
