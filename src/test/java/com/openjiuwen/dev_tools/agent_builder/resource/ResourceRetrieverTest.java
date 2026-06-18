/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import com.openjiuwen.core.common.exception.ExecutionError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Focused parity tests for resource retrieval.
 *
 * <p>Mirrors Python's {@code ResourceRetriever} in
 * {@code openjiuwen/dev_tools/agent_builder/resource/retriever.py}.</p>
 */
class ResourceRetrieverTest {

    @Test
    void loadResourcesReturnsDefaultPluginJson() {
        List<Map<String, Object>> plugins = ResourceRetriever.loadResources();

        assertEquals(2, plugins.size());
        assertEquals("f0ab5ca0-0d9c-4a05-a7cb-1f4dceff2ec1", plugins.get(0).get("plugin_id"));
        assertEquals("77490e6d-c61b-4bb3-8a44-6b298a7e0d32", plugins.get(1).get("plugin_id"));
    }

    @Test
    void loadResourcesReturnsEmptyListForMissingFile() {
        List<Map<String, Object>> plugins = ResourceRetriever.loadResources("not-found/plugins.json");

        assertTrue(plugins.isEmpty());
    }

    @Test
    void constructorPreprocessesPluginsAndToolMap() {
        ResourceRetriever retriever = new ResourceRetriever(modelReturning("{\"tool_id_list\": []}", null));

        assertEquals(2, retriever.getPluginDict().size());
        assertEquals(2, retriever.getToolPluginIdMap().size());
        assertEquals("f0ab5ca0-0d9c-4a05-a7cb-1f4dceff2ec1",
                retriever.getToolPluginIdMap().get("4aebb55e-1571-4a98-b353-41793b4434e3"));
    }

    @Test
    void retrieveFormatsPromptAndReturnsWorkflowResourceShape() {
        AtomicReference<List<BaseMessage>> capturedMessages = new AtomicReference<>();
        ResourceRetriever retriever = new ResourceRetriever(modelReturning(
                "```json\n{\"tool_id_list\": [\"4aebb55e-1571-4a98-b353-41793b4434e3\"]}\n```",
                capturedMessages
        ));

        Map<String, Object> result = retriever.retrieve(List.<Map<String, ?>>of(Map.of(
                "role", "user",
                "content", "build addition workflow"
        )));

        String prompt = String.valueOf(capturedMessages.get().get(0).getContent());
        assertTrue(prompt.contains("user: build addition workflow"));
        assertTrue(prompt.contains("4aebb55e-1571-4a98-b353-41793b4434e3"));

        List<?> plugins = assertList(result.get("plugins"));
        assertEquals(1, plugins.size());
        Map<?, ?> tool = assertMap(plugins.get(0));
        assertEquals("4aebb55e-1571-4a98-b353-41793b4434e3", tool.get("tool_id"));
        assertTrue(tool.containsKey("inputs"));
        assertTrue(tool.containsKey("outputs"));
        assertMap(result.get("plugin_dict"));
        assertMap(result.get("tool_id_map"));
    }

    @Test
    void retrieveOmitsInputOutputDetailsWhenNotForWorkflow() {
        ResourceRetriever retriever = new ResourceRetriever(modelReturning(
                "{\"tool_id_list\": [\"f6448b6e-860b-4a67-98bc-ec10de05832a\"]}",
                null
        ));

        Map<String, Object> result = retriever.retrieve(List.of(), false);

        List<?> plugins = assertList(result.get("plugins"));
        Map<?, ?> tool = assertMap(plugins.get(0));
        assertEquals("f6448b6e-860b-4a67-98bc-ec10de05832a", tool.get("tool_id"));
        assertFalse(tool.containsKey("inputs"));
        assertFalse(tool.containsKey("outputs"));
    }

    @Test
    void retrieveWrapsInvalidLlmPayloadAsExecutionError() {
        ResourceRetriever retriever = new ResourceRetriever(modelReturning("[1, 2]", null));

        try {
            retriever.retrieve(List.of());
            fail("expected ExecutionError");
        } catch (ExecutionError error) {
            assertSame(StatusCode.AGENT_BUILDER_RESOURCE_RETRIEVE_ERROR, error.getStatus());
            assertInstanceOf(ValidationError.class, error.getCause());
            assertEquals(true, assertMap(error.getDetails()).get("for_workflow"));
        }
    }

    private static Model modelReturning(String content, AtomicReference<List<BaseMessage>> capturedMessages) {
        return new Model((messages, modelConfig, modelClientConfig, options) -> {
            if (capturedMessages != null) {
                capturedMessages.set(messages);
            }
            return CompletableFuture.completedFuture(new AssistantMessage(content));
        });
    }

    private static List<?> assertList(Object value) {
        return assertInstanceOf(List.class, value);
    }

    private static Map<?, ?> assertMap(Object value) {
        return assertInstanceOf(Map.class, value);
    }
}
