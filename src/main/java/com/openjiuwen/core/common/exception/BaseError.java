/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Framework unified exception base class.
 * <p>
 * Key design points:
 * </p>
 * <ul>
 * <li>{@link StatusCode} is the primary semantic identifier</li>
 * <li>Exception type represents control / recovery semantics</li>
 * <li>Message rendering is template-based and lazy-safe</li>
 * </ul>
 * <p>
 * Mirrors Python's {@code BaseError} class.
 * </p>
 * 
 * @since 0.1.7
 */
public class BaseError extends RuntimeException {
    private final StatusCode status;
    private final int code;
    private final Map<String, Object> params;
    private final Object details;
    private final String templateMessage;
    private final String message;

    /** Whether this error is recoverable (can be retried). */
    private final boolean recoverable;

    /** Whether this error is fatal (must abort execution). */
    private final boolean fatal;

    /**
     * Construct a new BaseError.
     * 
     * @param status the status code
     * @param msg optional custom message (overrides template)
     * @param details optional additional details
     * @param cause optional root cause
     * @param params template parameters for message rendering
     * @since 0.1.7
     */
    public BaseError(StatusCode status, String msg, Object details, Throwable cause, Map<String, Object> params) {
        super(msg != null ? msg : renderMessage(status, params), cause);
        this.status = status;
        this.code = status.getCode();
        this.params = params != null ? Collections.unmodifiableMap(new HashMap<>(params)) : Collections.emptyMap();
        this.details = details;
        this.templateMessage = renderMessage(status, params);
        this.message = msg != null ? msg : this.templateMessage;
        this.recoverable = defaultRecoverable();
        this.fatal = defaultFatal();
    }

    /**
     * Convenience constructor with builder-style params.
     * 
     * @param status the status code
     * @param msg optional custom message
     * @param details optional additional details
     * @param cause optional root cause
     * @since 0.1.7
     */
    public BaseError(StatusCode status, String msg, Object details, Throwable cause) {
        this(status, msg, details, cause, Collections.emptyMap());
    }

    /**
     * Creates a BaseError with status and template parameters.
     * 
     * @param status the status code
     * @param params template parameters for message rendering
     * @since 0.1.7
     */
    public BaseError(StatusCode status, Map<String, Object> params) {
        this(status, null, null, null, params);
    }

    /**
     * Creates a BaseError with status only.
     * 
     * @param status the status code
     * @since 0.1.7
     */
    public BaseError(StatusCode status) {
        this(status, null, null, null, Collections.emptyMap());
    }

    // ======================== Template rendering ========================

    /**
     * Render error message from StatusCode template in a safe manner.
     * Missing placeholders are replaced with {@code <missing:key>}.
     *
     * @param status the status code containing the message template
     * @param params the parameters for template substitution
     * @return the rendered message string
     */
    static String renderMessage(StatusCode status, Map<String, Object> params) {
        if (status == null || status.getErrmsg() == null || status.getErrmsg().isEmpty()) {
            return "";
        }
        String template = status.getErrmsg();
        if (params == null || params.isEmpty()) {
            return template;
        }
        try {
            return formatTemplate(template, params);
        } catch (Exception e) {
            return template;
        }
    }

    /**
     * Safe template formatting. Placeholders use {@code {key}} syntax.
     * Missing keys are replaced with {@code <missing:key>}.
     *
     * @param template the message template with {key} placeholders
     * @param params the parameters for substitution
     * @return the formatted string
     */
    static String formatTemplate(String template, Map<String, Object> params) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(template.length() * 2);
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{') {
                int end = template.indexOf('}', i + 1);
                if (end > i) {
                    String key = template.substring(i + 1, end);
                    if (params.containsKey(key)) {
                        Object val = params.get(key);
                        sb.append(val != null ? val.toString() : "null");
                    } else {
                        sb.append("<missing:").append(key).append('>');
                    }
                    i = end + 1;
                } else {
                    sb.append(c);
                    i++;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    // ======================== Structured output ========================

    /**
     * Standard structured output for API / RPC / logging.
     * 
     * @return a map representation of this error
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("status", status.name());
        map.put("message", templateMessage);
        map.put("params", params);
        map.put("raw_message", message);
        map.put("details", details);
        return map;
    }

    /**
     * Serialize this error to a JSON string.
     * 
     * @return JSON representation of the error
     * @since 0.1.7
     */
    public String toJson() {
        try {
            return new ObjectMapper().writeValueAsString(toMap());
        } catch (JsonProcessingException e) {
            return toString();
        }
    }

    /**
     * getStatus.
     * 
     * @return the result
     * @since 0.1.7
     */
    public StatusCode getStatus() {
        return status;
    }

    /**
     * getCode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getCode() {
        return code;
    }

    /**
     * getParams.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * getDetails.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getDetails() {
        return details;
    }

    /**
     * getTemplateMessage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTemplateMessage() {
        return templateMessage;
    }

    /**
     * getMessage.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getMessage() {
        return message;
    }

    /**
     * isRecoverable.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isRecoverable() {
        return recoverable;
    }

    /**
     * isFatal.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isFatal() {
        return fatal;
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        return "[" + code + "] " + message;
    }

    // ======================== Subclass overrides ========================

    /**
     * Subclasses override to define default recoverability.
     * 
     * @return the result
     * @since 0.1.7
     */
    protected boolean defaultRecoverable() {
        return false;
    }

    /**
     * Subclasses override to define default fatality.
     * 
     * @return the result
     * @since 0.1.7
     */
    protected boolean defaultFatal() {
        return false;
    }
}
