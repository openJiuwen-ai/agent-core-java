  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Base code operation — abstract class for code execution.
 * <p>
 * Mirrors Python's {@code BaseCodeOperation} in {@code sys_operation/code.py}.
 */
public abstract class BaseCodeOperation extends BaseOperation {

    protected BaseCodeOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    @Override
    public List<ToolCard> listTools() {
        return generateToolCards(List.of("executeCode", "executeCodeStream"));
    }

    /**
     * Execute arbitrary code.
     *
     * @param code        source code to execute
     * @param language    programming language ("python" or "javascript")
     * @param timeout     maximum execution time in seconds (default 300)
     * @param environment custom environment variables
     * @param options     additional execution configuration
     * @return execution result
     */
    public abstract ExecuteCodeResult executeCode(
            String code,
            String language,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options);

    /**
     * Execute arbitrary code with streaming output.
     *
     * @param code        source code to execute
     * @param language    programming language ("python" or "javascript")
     * @param timeout     maximum execution time in seconds (default 300)
     * @param environment custom environment variables
     * @param options     additional execution configuration
     * @return iterator of streaming results
     */
    public abstract Iterator<ExecuteCodeStreamResult> executeCodeStream(
            String code,
            String language,
            int timeout,
            Map<String, String> environment,
            Map<String, Object> options);
}
