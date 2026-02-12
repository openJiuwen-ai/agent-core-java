// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Framework unified exception base class
 * 
 * <p>Key design points:
 * <ul>
 *   <li>StatusCode is the primary semantic identifier</li>
 *   <li>Exception type represents control / recovery semantics</li>
 *   <li>Message rendering is template-based and lazy-safe</li>
 * </ul>
 * 
 * @since 0.1.4
 */
public class BaseError extends RuntimeException {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    protected StatusCode status;
    protected int code;
    protected Map<String, Object> params;
    protected Object details;
    protected Throwable causeException;
    protected String templateMessage;
    protected String customMessage;
    protected boolean recoverable;
    protected boolean fatal;
    
    /**
     * Constructor
     * 
     * @param status the status code
     */
    public BaseError(StatusCode status) {
        this(status, null, null, null, null);
    }
    
    /**
     * Full constructor
     * 
     * @param status the status code
     * @param msg custom message (optional)
     * @param details additional details (optional)
     * @param cause the cause exception (optional)
     * @param params template parameters (optional)
     */
    public BaseError(
            StatusCode status,
            String msg,
            Object details,
            Throwable cause,
            Map<String, Object> params) {
        super(cause);
        
        this.status = status != null ? status : StatusCode.ERROR;
        this.code = this.status.getCode();
        this.params = params != null ? new HashMap<>(params) : new HashMap<>();
        this.details = details;
        this.causeException = cause;
        this.customMessage = msg != null ? msg : "";
        this.templateMessage = renderMessage();
        this.recoverable = false;
        this.fatal = false;
    }
    
    /**
     * Render message from template with parameters
     * 
     * @return the rendered message
     */
    protected String renderMessage() {
        try {
            return formatTemplate(status.getMessage(), params);
        } catch (Exception e) {
            return status.getMessage();
        }
    }
    
    /**
     * Format a template string with parameters
     * 
     * @param template the message template
     * @param params the parameters to substitute
     * @return the formatted message
     */
    protected static String formatTemplate(String template, Map<String, Object> params) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        
        if (params == null || params.isEmpty()) {
            return template;
        }
        
        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value;
            
            if (entry.getValue() == null) {
                value = "null";
            } else {
                value = entry.getValue().toString();
            }
            
            result = result.replace(placeholder, value);
        }
        
        // Replace any remaining placeholders with <missing:key>
        result = result.replaceAll("\\{([^}]+)\\}", "<missing:$1>");
        
        return result;
    }
    
    /**
     * Convert to Map for structured output
     * 
     * @return a Map representation
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("status", status.name());
        map.put("message", templateMessage);
        map.put("params", params);
        map.put("raw_message", customMessage);
        map.put("details", details);
        return map;
    }
    
    /**
     * Convert to JSON string
     * 
     * @return a JSON representation
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(toMap());
        } catch (JsonProcessingException e) {
            return String.format("{\"code\": %d, \"message\": \"%s\"}", code, templateMessage);
        }
    }
    
    @Override
    public String toString() {
        if (customMessage.isEmpty()) {
            return String.format("[%d] %s", code, templateMessage);
        }
        return String.format("[%d] %s %s", code, templateMessage, customMessage);
    }
    
    @Override
    public String getMessage() {
        return toString();
    }
    
    // Getters
    
    public StatusCode getStatus() {
        return status;
    }
    
    public int getCode() {
        return code;
    }
    
    public Map<String, Object> getParams() {
        return Collections.unmodifiableMap(params);
    }
    
    public Object getDetails() {
        return details;
    }
    
    public String getCustomMessage() {
        return customMessage;
    }
    
    public String getTemplateMessage() {
        return templateMessage;
    }
    
    public boolean isRecoverable() {
        return recoverable;
    }
    
    public boolean isFatal() {
        return fatal;
    }
    
    // Builder for convenient construction
    
    public static Builder builder(StatusCode status) {
        return new Builder(status);
    }
    
    public static class Builder {
        private final StatusCode status;
        private String msg;
        private Object details;
        private Throwable cause;
        private final Map<String, Object> params = new HashMap<>();
        
        public Builder(StatusCode status) {
            this.status = status;
        }
        
        public Builder msg(String msg) {
            this.msg = msg;
            return this;
        }
        
        public Builder details(Object details) {
            this.details = details;
            return this;
        }
        
        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }
        
        public Builder param(String key, Object value) {
            this.params.put(key, value);
            return this;
        }
        
        public Builder params(Map<String, Object> params) {
            this.params.putAll(params);
            return this;
        }
        
        public BaseError build() {
            return new BaseError(status, msg, details, cause, params);
        }
        
        public void raise() {
            throw build();
        }
    }
}

