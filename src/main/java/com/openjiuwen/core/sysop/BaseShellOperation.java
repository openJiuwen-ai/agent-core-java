/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Base shell operation — abstract class for shell command execution.
 * <p>
 * Mirrors Python's {@code BaseShellOperation} in {@code sys_operation/shell.py}.
 */
public abstract class BaseShellOperation extends BaseOperation {

    protected BaseShellOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    @Override
    public List<ToolCard> listTools() {
        List<ToolCard> toolCards = generateToolCards(List.of("executeCmd", "executeCmdStream", "executeCmdBackground"));
        for (ToolCard toolCard : toolCards) {
            toolCard.getInputParams().put("required", List.of("command"));
        }
        return toolCards;
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
     * Launch a command in the background and return immediately with its PID.
     * <p>
     * Mirrors Python's {@code execute_cmd_background} from {@code sys_operation/shell.py}.
     *
     * @param command     command to execute
     * @param cwd         working directory (default: current directory)
     * @param shellType   shell type (powershell, bash, etc.)
     * @return background execution result containing the process PID
     */
    public abstract ExecuteCmdBackgroundResult executeCmdBackground(
            String command,
            String cwd,
            String shellType);
}
