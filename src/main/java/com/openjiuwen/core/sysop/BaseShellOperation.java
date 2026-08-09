/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Base shell operation contract.
 *
 * <p>Mirrors Python's {@code BaseShellOperation} and {@code ShellType} in
 * {@code openjiuwen/core/sys_operation/shell.py}.</p>
 */
public abstract class BaseShellOperation extends BaseOperation {

    protected BaseShellOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    @Override
    public List<ToolCard> listTools() {
        return generateToolCards(List.of("execute_cmd", "execute_cmd_stream", "execute_cmd_background"));
    }

    public abstract CompletableFuture<ExecuteCmdResult> executeCmd(
            String command,
            String cwd,
            Integer timeout,
            Map<String, String> environment,
            Map<String, Object> options,
            ShellType shellType);

    /**
     * Legacy sync overload used by compatibility tests and reflective providers.
     */
    public ExecuteCmdResult executeCmd(
            String command,
            String cwd,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options) {
        return executeCmd(command, cwd, timeout, environment, options, null).join();
    }

    public Flow.Publisher<ExecuteCmdStreamResult> executeCmdStream(
            String command,
            String cwd,
            Integer timeout,
            Map<String, String> environment,
            Map<String, Object> options,
            ShellType shellType) {
        return null;
    }

    public abstract CompletableFuture<ExecuteCmdBackgroundResult> executeCmdBackground(
            String command,
            String cwd,
            Map<String, String> environment,
            double grace,
            ShellType shellType);

    /**
     * Mirrors Python's {@code ShellType} in
     * {@code openjiuwen/core/sys_operation/shell.py}.
     */
    public enum ShellType {
        AUTO("auto"),
        CMD("cmd"),
        POWERSHELL("powershell"),
        BASH("bash"),
        SH("sh");

        private final String value;

        ShellType(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }

        @JsonCreator
        public static ShellType fromValue(String value) {
            if (value == null) {
                return AUTO;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (ShellType shellType : values()) {
                if (shellType.value.equals(normalized)) {
                    return shellType;
                }
            }
            return AUTO;
        }
    }
}
