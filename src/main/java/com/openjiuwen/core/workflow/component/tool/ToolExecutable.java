/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow.component.tool;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.service_api.RestfulApi;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Executable for the Tool workflow component.
 * <p>
 * Invokes the bound {@link Tool}, processes its output, and wraps results
 * in a standard {@link ToolComponentOutput} envelope.
 * <p>
 * Mirrors Python's {@code ToolExecutable}.
 */
public class ToolExecutable extends ComponentExecutable {

    private static final int DEFAULT_EXCEPTION_ERROR_CODE = -1;

    private final ToolComponentConfig config;
    private Tool tool;

    public ToolExecutable(ToolComponentConfig config) {
        this.config = config;
    }

    public ToolExecutable setTool(Tool tool) {
        this.tool = tool;
        return this;
    }

    @Override
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        if (tool == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_TOOL_EXECUTION_ERROR,
                    "error_msg", "tool is not initialized");
        }
        Map<String, Object> toolInputs = validateInputs(inputs);

        Map<String, Object> response;
        try {
            Object rawResponse = tool.invoke(toolInputs);
            response = postProcessToolResult(rawResponse);
        } catch (Exception e) {
            if (e instanceof BaseError be) {
                response = Map.of(
                        ToolComponentOutput.ERR_MESSAGE, be.getMessage(),
                        ToolComponentOutput.ERR_CODE, be.getCode()
                );
            } else {
                response = Map.of(
                        ToolComponentOutput.ERR_MESSAGE, "tool execution error: "
                                + (e.getMessage() != null ? e.getMessage() : "unknown exception"),
                        ToolComponentOutput.ERR_CODE, StatusCode.TOOL_EXECUTION_ERROR.getCode()
                );
            }
        }
        return ToolComponentOutput.fromMap(response).toMap();
    }

    // ==================== Internal ====================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> validateInputs(Object inputs) {
        if (inputs instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) inputs);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postProcessToolResult(Object toolResult) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (tool instanceof RestfulApi) {
            Map<String, Object> trMap = (toolResult instanceof Map) ? (Map<String, Object>) toolResult : Map.of();
            result.put(ToolComponentOutput.RESTFUL_DATA, trMap.getOrDefault("data", ""));
            int code;
            try {
                code = ((Number) trMap.getOrDefault("code", DEFAULT_EXCEPTION_ERROR_CODE)).intValue();
            } catch (Exception e) {
                code = DEFAULT_EXCEPTION_ERROR_CODE;
            }
            result.put(ToolComponentOutput.ERR_CODE,
                    (200 <= code && code < 300) ? StatusCode.SUCCESS.getCode()
                            : StatusCode.TOOL_EXECUTION_ERROR.getCode());
            result.put(ToolComponentOutput.ERR_MESSAGE, trMap.getOrDefault("message", ""));
        } else {
            // Check if result follows {code, data, message} convention
            if (toolResult instanceof Map<?, ?> trMap) {
                if (trMap.containsKey("code") && trMap.containsKey("data") && trMap.containsKey("message")) {
                    result.put(ToolComponentOutput.RESTFUL_DATA, trMap.get("data") != null ? trMap.get("data") : "");
                    result.put(ToolComponentOutput.ERR_CODE,
                            trMap.get("code") != null ? trMap.get("code") : StatusCode.TOOL_EXECUTION_ERROR.getCode());
                    result.put(ToolComponentOutput.ERR_MESSAGE, trMap.get("message") != null ? trMap.get("message") : "");
                    return result;
                }
            }
            result.put(ToolComponentOutput.RESTFUL_DATA, toolResult);
        }
        return result;
    }
}
