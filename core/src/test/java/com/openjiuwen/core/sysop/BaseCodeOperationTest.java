/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code BaseCodeOperation} in
 * {@code openjiuwen/core/sys_operation/code.py}.
 */
class BaseCodeOperationTest {

    @Test
    void listToolsExposePythonCodeOperationMethods() {
        ExampleCodeOperation operation = new ExampleCodeOperation();

        List<ToolCard> cards = operation.listTools();

        assertThat(cards).extracting(ToolCard::getName)
                .containsExactly("execute_code", "execute_code_stream");
    }

    @Test
    void executeCodeSignatureIncludesCurrentPythonCwdParameter() throws NoSuchMethodException {
        Method method = BaseCodeOperation.class.getMethod(
                "executeCode",
                String.class,
                BaseCodeOperation.CodeLanguage.class,
                int.class,
                Map.class,
                String.class,
                Map.class
        );

        assertThat(method.getParameterTypes()[4]).isEqualTo(String.class);
    }

    @Test
    void executeCodeStreamSignatureIncludesCurrentPythonCwdParameter() throws NoSuchMethodException {
        Method method = BaseCodeOperation.class.getMethod(
                "executeCodeStream",
                String.class,
                BaseCodeOperation.CodeLanguage.class,
                int.class,
                Map.class,
                String.class,
                Map.class
        );

        assertThat(method.getParameterTypes()[4]).isEqualTo(String.class);
    }

    @Test
    void codeLanguageValuesMatchPythonLiteralChoices() {
        assertThat(BaseCodeOperation.CodeLanguage.PYTHON.value()).isEqualTo("python");
        assertThat(BaseCodeOperation.CodeLanguage.JAVASCRIPT.value()).isEqualTo("javascript");
    }

    private static final class ExampleCodeOperation extends BaseCodeOperation {

        private ExampleCodeOperation() {
            super("code", OperationMode.LOCAL, "example code operation", null);
        }

        @Override
        public CompletableFuture<ExecuteCodeResult> executeCode(String code,
                                                                CodeLanguage language,
                                                                int timeout,
                                                                Map<String, String> environment,
                                                                String cwd,
                                                                Map<String, Object> options) {
            return CompletableFuture.completedFuture(new ExecuteCodeResult());
        }

        @Override
        public Flow.Publisher<ExecuteCodeStreamResult> executeCodeStream(String code,
                                                                         CodeLanguage language,
                                                                         int timeout,
                                                                         Map<String, String> environment,
                                                                         String cwd,
                                                                         Map<String, Object> options) {
            return subscriber -> subscriber.onComplete();
        }
    }
}
