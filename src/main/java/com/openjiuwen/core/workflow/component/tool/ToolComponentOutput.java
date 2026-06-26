/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output model for the Tool workflow component.
 * <p>
 * Mirrors Python's {@code ToolComponentOutput} in
 * {@code openjiuwen/core/workflow/components/tool/tool_comp.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolComponentOutput {

    public static final String ERR_CODE = "errCode";
    public static final String ERR_MESSAGE = "errMessage";
    public static final String RESTFUL_DATA = "data";
    public static final String PY_ERR_CODE = "error_code";
    public static final String PY_ERR_MESSAGE = "error_message";

    private int errorCode = 0;
    private String errorMessage = "";
    private Object data = "";

    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put(ERR_CODE, errorCode);
        map.put(ERR_MESSAGE, errorMessage);
        map.put(PY_ERR_CODE, errorCode);
        map.put(PY_ERR_MESSAGE, errorMessage);
        map.put(RESTFUL_DATA, data);
        return map;
    }

    public static ToolComponentOutput fromMap(java.util.Map<String, Object> map) {
        ToolComponentOutput out = new ToolComponentOutput();
        if (map == null) {
            return out;
        }
        if (map.containsKey(ERR_CODE)) {
            Object code = map.get(ERR_CODE);
            out.errorCode = parseErrorCode(code, out.errorCode);
        } else if (map.containsKey(PY_ERR_CODE)) {
            Object code = map.get(PY_ERR_CODE);
            out.errorCode = parseErrorCode(code, out.errorCode);
        }
        if (map.containsKey(ERR_MESSAGE)) {
            Object msg = map.get(ERR_MESSAGE);
            out.errorMessage = msg != null ? msg.toString() : "";
        } else if (map.containsKey(PY_ERR_MESSAGE)) {
            Object msg = map.get(PY_ERR_MESSAGE);
            out.errorMessage = msg != null ? msg.toString() : "";
        }
        if (map.containsKey(RESTFUL_DATA)) {
            out.data = map.get(RESTFUL_DATA);
        }
        return out;
    }

    private static int parseErrorCode(Object code, int defaultValue) {
        if (code instanceof Number number) {
            return number.intValue();
        }
        if (code instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
