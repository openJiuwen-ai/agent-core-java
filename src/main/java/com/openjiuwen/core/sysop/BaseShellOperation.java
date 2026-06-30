/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Base shell operation — abstract class for shell command execution.
 * <p>
 * Mirrors Python's {@code BaseShellOperation} in {@code sys_operation/shell.py}.
 */
public abstract class BaseShellOperation extends BaseOperation {

    /**
     * Auto-generated for codecheck compliance.
     */
    protected BaseShellOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<ToolCard> listTools() {
        return generateToolCards(List.of("executeCmd", "executeCmdStream", "executeCmdBackground"));
    }

    /**
     * Execute a shell command.
     *
     * @param command     command to execute
     * @param cwd         working directory (default: current directory)
     * @param timeout     command execution timeout in seconds (default 300)
     * @param environment custom environment variables
     * @param options     additional execution configuration
     * @return execution result
     */
    public abstract ExecuteCmdResult executeCmd(
            String command,
            String cwd,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options);

    /**
     * Execute a shell command with streaming output.
     *
     * @param command     command to execute
     * @param cwd         working directory (default: current directory)
     * @param timeout     command execution timeout in seconds (default 300)
     * @param environment custom environment variables
     * @param options     additional execution configuration
     * @return iterator of streaming results
     */
    public abstract Iterator<ExecuteCmdStreamResult> executeCmdStream(
            String command,
            String cwd,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options);

    /**
     * Execute a shell command in the background and return its PID.
     */
    public abstract ExecuteCmdBackgroundResult executeCmdBackground(
            String command,
            String cwd,
            Map<String, String> environment,
            double grace,
            Map<String, Object> options);
}
