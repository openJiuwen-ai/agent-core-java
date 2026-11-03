/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base result envelope for sys-operation responses.
 * <p>
 * Mirrors Python's {@code BaseResult} in
 * {@code openjiuwen/core/sys_operation/result/base_result.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseResult<T> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{([^{}]+)\\}");

    private int code;
    private String message;
    private T data;

    public BaseResult() {
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{code=" + code + ", message=" + message + ", data=" + data + "}";
    }

    public static <T, R> R buildOperationErrorResult(
            StatusCode errorType,
            Map<String, Object> msgFormatKwargs,
            Class<R> resultClass
    ) {
        return buildOperationErrorResult(errorType, msgFormatKwargs, resultClass, null, Map.of());
    }

    public static <T, R> R buildOperationErrorResult(
            StatusCode errorType,
            Map<String, Object> msgFormatKwargs,
            Class<R> resultClass,
            T data
    ) {
        return buildOperationErrorResult(errorType, msgFormatKwargs, resultClass, data, Map.of());
    }

    public static <T, R> R buildOperationErrorResult(
            StatusCode errorType,
            Map<String, Object> msgFormatKwargs,
            Class<R> resultClass,
            T data,
            Map<String, Object> extraFields
    ) {
        String errorMessage = formatTemplate(errorType.errmsg(), msgFormatKwargs);
        Map<String, Object> finalFields = new LinkedHashMap<>();
        finalFields.put("code", errorType.code());
        finalFields.put("message", errorMessage);
        finalFields.put("data", data);
        if (extraFields != null) {
            finalFields.putAll(extraFields);
        }
        return OBJECT_MAPPER.convertValue(finalFields, resultClass);
    }

    private static String formatTemplate(String template, Map<String, Object> values) {
        Matcher matcher = TEMPLATE_PATTERN.matcher(template == null ? "" : template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (values == null || !values.containsKey(key)) {
                throw new IllegalArgumentException("Missing message format key: " + key);
            }
            Object value = values.get(key);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
