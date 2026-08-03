/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Framework unified exception base class.
 * <p>
 * Mirrors Python's {@code BaseError} in
 * {@code openjiuwen/core/common/exception/errors.py}.
 * </p>
 */
public class BaseError extends RuntimeException {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StatusCode status;
    private final int code;
    private final Map<String, Object> params;
    private final Object details;
    private final String templateMessage;
    private final String message;
    private final boolean recoverable;
    private final boolean fatal;

    public BaseError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(msg != null ? msg : renderMessage(status, params), cause);
        this.status = status;
        this.code = status.getCode();
        this.params = params != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(params))
                : Collections.emptyMap();
        this.details = details;
        this.templateMessage = renderMessage(status, this.params);
        this.message = msg != null ? msg : this.templateMessage;
        this.recoverable = defaultRecoverable();
        this.fatal = defaultFatal();
    }

    public BaseError(StatusCode status, String msg, Object details, Throwable cause) {
        this(status, msg, details, cause, Collections.emptyMap());
    }

    public BaseError(StatusCode status, Map<String, Object> params) {
        this(status, null, null, null, params);
    }

    public BaseError(StatusCode status) {
        this(status, null, null, null, Collections.emptyMap());
    }

    static String renderMessage(StatusCode status, Map<String, Object> params) {
        if (status == null || status.getErrmsg() == null) {
            return "";
        }
        try {
            return formatTemplate(status.getErrmsg(), params);
        } catch (Exception ignored) {
            return status.getErrmsg();
        }
    }

    static String formatTemplate(String template, Map<String, Object> params) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(template.length() * 2);
        int index = 0;
        while (index < template.length()) {
            char current = template.charAt(index);
            if (current == '{') {
                int end = template.indexOf('}', index + 1);
                if (end > index) {
                    String key = template.substring(index + 1, end);
                    if (params != null && params.containsKey(key)) {
                        Object value = params.get(key);
                        builder.append(value != null ? value : "null");
                    } else {
                        builder.append("<missing:").append(key).append('>');
                    }
                    index = end + 1;
                    continue;
                }
            }
            builder.append(current);
            index++;
        }
        return builder.toString();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("status", status.name());
        result.put("message", templateMessage);
        result.put("params", params);
        result.put("raw_message", message);
        result.put("details", details);
        return result;
    }

    public Map<String, Object> toDict() {
        return toMap();
    }

    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(toMap());
        } catch (JsonProcessingException ignored) {
            return toString();
        }
    }

    public StatusCode getStatus() {
        return status;
    }

    public int getCode() {
        return code;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public Object getDetails() {
        return details;
    }

    public String getTemplateMessage() {
        return templateMessage;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public boolean isRecoverable() {
        return recoverable;
    }

    public boolean isFatal() {
        return fatal;
    }

    @Override
    public String toString() {
        return "[" + code + "] " + message;
    }

    protected boolean defaultRecoverable() {
        return false;
    }

    protected boolean defaultFatal() {
        return false;
    }
}
