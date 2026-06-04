/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.core.component;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.workflow.ComponentExecutable;
import com.openjiuwen.core.workflow.component.llm.react.ReActAgentComp;
import com.openjiuwen.core.workflow.component.llm.react.ReActAgentCompConfig;
import com.openjiuwen.core.workflow.component.llm.react.ReActAgentCompExecutable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_react_agent_workflow.py} in
 * {@code tests.unit_tests.core.component}.
 */
@Tag("unit-test")
class TestReactAgentWorkflow {

    @Test
    @DisplayName("component creation creates executable lazily")
    void testComponentCreation() {
        ReActAgentComp component = new ReActAgentComp(config(5));

        assertNotNull(component);
        assertNotNull(component.getExecutable());
        assertInstanceOf(ReActAgentCompExecutable.class, component.getExecutable());
    }

    @Test
    @DisplayName("executable exposes invoke stream collect and transform methods")
    void testExecutableMethods() throws NoSuchMethodException {
        ReActAgentCompExecutable executable = new ReActAgentComp(config(5)).getExecutable();

        assertInstanceOf(ComponentExecutable.class, executable);
        assertMethodExists(executable, "invoke");
        assertMethodExists(executable, "stream");
        assertMethodExists(executable, "collect");
        assertMethodExists(executable, "transform");
    }

    @Test
    @DisplayName("config defaults mirror Python ReActAgentCompConfig")
    void testConfigDefaults() {
        ReActAgentCompConfig config = new ReActAgentCompConfig();

        assertEquals(5, config.getMaxIterations());
        assertEquals("openai", config.getModelProvider());
        assertTrue(config.getPromptTemplate().isEmpty());
    }

    @Test
    @DisplayName("executable exposes ability manager for adding tools")
    void testAbilityManagerAvailable() {
        ReActAgentCompExecutable executable = new ReActAgentComp(config(3)).getExecutable();

        assertNotNull(executable.getAbilityManager());
        assertNotNull(executable.getReactAgent());
    }

    @Test
    @Disabled("Mirrors Python @unittest.skip(\"skip system test\") for real LLM workflow")
    void testReactAgentInWorkflow() {
    }

    @Test
    @Disabled("Mirrors Python @unittest.skip(\"skip system test\") for real add-tool workflow")
    void testReactAgentWithAddToolInWorkflow() {
    }

    @Test
    @Disabled("Mirrors Python @unittest.skip(\"skip system test\") for real streaming add-tool workflow")
    void testReactAgentStreamWithAddToolInWorkflow() {
    }

    @Test
    @Disabled("Mirrors Python @unittest.skip(\"skip system test\") for real ReAct streaming")
    void testReactAgentCompStream() {
    }

    private static ReActAgentCompConfig config(int maxIterations) {
        return ReActAgentCompConfig.builder()
                .modelClientConfig(ModelClientConfig.builder()
                        .clientProvider("OpenAI")
                        .apiKey("sk-fake")
                        .apiBase("mock://api.openai.com/v1")
                        .verifySsl(false)
                        .build())
                .modelConfigObj(ModelRequestConfig.builder().modelName("fake-model").build())
                .maxIterations(maxIterations)
                .modelName("fake-model")
                .modelProvider("OpenAI")
                .apiKey("sk-fake")
                .apiBase("mock://api.openai.com/v1")
                .build();
    }

    private static void assertMethodExists(Object executable, String methodName) throws NoSuchMethodException {
        Method method = executable.getClass().getMethod(
                methodName,
                Object.class,
                com.openjiuwen.core.session.NodeSessionApi.class,
                com.openjiuwen.core.context.ModelContext.class);
        assertEquals(methodName, method.getName());
    }
}
