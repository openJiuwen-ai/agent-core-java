/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeStreamResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Base code operation contract.
 *
 * <p>Mirrors Python's {@code BaseCodeOperation} in
 * {@code openjiuwen/core/sys_operation/code.py}.</p>
 */
public abstract class BaseCodeOperation extends BaseOperation {

    protected BaseCodeOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    @Override
    public List<ToolCard> listTools() {
        return generateToolCards(List.of("execute_code", "execute_code_stream"));
    }

    public abstract CompletableFuture<ExecuteCodeResult> executeCode(
            String code,
            CodeLanguage language,
            int timeout,
            Map<String, String> environment,
            String cwd,
            Map<String, Object> options);

    public abstract Flow.Publisher<ExecuteCodeStreamResult> executeCodeStream(
            String code,
            CodeLanguage language,
            int timeout,
            Map<String, String> environment,
            String cwd,
            Map<String, Object> options);

    /**
     * Mirrors Python's literal language choices in
     * {@code openjiuwen/core/sys_operation/code.py}.
     */
    public enum CodeLanguage {
        PYTHON("python"),
        JAVASCRIPT("javascript");

        private final String value;

        CodeLanguage(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }

        @JsonCreator
        public static CodeLanguage fromValue(String value) {
            if (value == null) {
                return PYTHON;
            }
            for (CodeLanguage language : values()) {
                if (language.value.equalsIgnoreCase(value.trim())) {
                    return language;
                }
            }
            return PYTHON;
        }
    }
}
