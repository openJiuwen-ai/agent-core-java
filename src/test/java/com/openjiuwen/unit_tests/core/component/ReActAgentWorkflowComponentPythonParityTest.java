/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.component;

import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.components.llm.react.ReActAgentComp;
import com.openjiuwen.core.workflow.components.llm.react.ReActAgentCompConfig;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests/unit_tests/core/component/test_react_agent_workflow.py}.</p>
 */
class ReActAgentWorkflowComponentPythonParityTest {

    private static final String API_BASE = "https://api.openai.com/v1";
    private static final String API_KEY = "sk-fake";
    private static final String MODEL_NAME = "";
    private static final String MODEL_PROVIDER = "OpenAI";

    @Test
    void testComponentCreation() {
        ReActAgentComp component = new ReActAgentComp(config());

        assertThat(component).isNotNull();
        assertThat(component.getExecutable()).isNotNull();
    }

    @Test
    void testExecutableMethods() throws NoSuchMethodException {
        ReActAgentComp component = new ReActAgentComp(config());
        Object executable = component.getExecutable();
        Class<?> executableType = executable.getClass();

        Method invoke = executableType.getMethod("invoke", Object.class, BaseSession.class, ModelContext.class);
        Method stream = executableType.getMethod("stream", Object.class, BaseSession.class, ModelContext.class);
        Method collect = executableType.getMethod("collect", Object.class, BaseSession.class, ModelContext.class);
        Method transform = executableType.getMethod("transform", Object.class, BaseSession.class, ModelContext.class);

        assertThat(invoke).isNotNull();
        assertThat(stream.getReturnType()).isAssignableFrom(Iterator.class);
        assertThat(collect).isNotNull();
        assertThat(transform.getReturnType()).isAssignableFrom(Iterator.class);
    }

    @Disabled("Skipped in Python source: skip system test")
    @Test
    void testReactAgentInWorkflow() {
        assertThat(true).isTrue();
    }

    @Disabled("Skipped in Python source: skip system test")
    @Test
    void testReactAgentWithAddToolInWorkflow() {
        assertThat(true).isTrue();
    }

    @Disabled("Skipped in Python source: skip system test")
    @Test
    void testReactAgentStreamWithAddToolInWorkflow() {
        assertThat(true).isTrue();
    }

    @Disabled("Skipped in Python source: skip system test")
    @Test
    void testReactAgentCompStream() {
        assertThat(true).isTrue();
    }

    private static ReActAgentCompConfig config() {
        ReActAgentCompConfig config = new ReActAgentCompConfig();
        config.setModelClientConfig(ModelClientConfig.builder()
                .clientProvider(MODEL_PROVIDER)
                .apiKey(API_KEY)
                .apiBase(API_BASE)
                .build());
        config.setModelConfigObj(ModelRequestConfig.builder()
                .modelName(MODEL_NAME)
                .build());
        config.setMaxIterations(5);
        return config;
    }
}
