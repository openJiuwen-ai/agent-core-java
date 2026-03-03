// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * 框架统一异常基类
 *
 * <p>核心设计点：
 * - StatusCode 是主要语义标识
 * - 异常类型表示控制/恢复语义
 * - 消息渲染基于模板且延迟安全</p>
 */
public class BaseError extends Exception {

    private final StatusCode status;
    private final int code;
    private final Map<String, Object> params;
    private final Object details;
    private final String templateMessage;
    private final String message;

    public BaseError(StatusCode status) {
        this(status, null, null, null, null);
    }

    public BaseError(StatusCode status, String message) {
        this(status, message, null, null, null);
    }

    public BaseError(StatusCode status, Object details, Throwable cause) {
        this(status, null, details, cause, null);
    }

    public BaseError(StatusCode status, String message, Object details, Throwable cause) {
        this(status, message, details, cause, null);
    }

    public BaseError(StatusCode status, String message, Object details, Throwable cause, Map<String, Object> params) {
        // 使用模板消息作为异常消息，自定义消息存储在 message 字段中
        super(ErrorCodeFormatter.formatTemplate(status.errmsg(), params), cause);
        this.status = status;
        this.code = status.code();
        this.params = params != null ? params : new HashMap<>();
        this.details = details;
        this.templateMessage = ErrorCodeFormatter.formatTemplate(status.errmsg(), this.params);
        this.message = message != null ? message : this.templateMessage;
    }

    /**
     * 从 StatusCode 模板渲染错误消息
     *
     * <p>永远不会向外抛出格式化异常。</p>
     *
     * @return 渲染后的消息
     */
    protected String renderMessage() {
        return templateMessage;
    }

    /**
     * 获取 StatusCode
     *
     * @return StatusCode
     */
    public StatusCode getStatus() {
        return status;
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取参数
     *
     * @return 参数映射
     */
    public Map<String, Object> getParams() {
        return new HashMap<>(params);
    }

    /**
     * 获取详细信息
     *
     * @return 详细信息
     */
    public Object getDetails() {
        return details;
    }

    /**
     * 获取模板消息
     *
     * @return 模板消息
     */
    public String getTemplateMessage() {
        return templateMessage;
    }

    /**
     * 获取自定义消息（如果有的话），否则返回模板消息
     *
     * @return 自定义消息或模板消息
     */
    @Override
    public String getMessage() {
        return message;
    }

    /**
     * 检查是否可恢复
     *
     * @return 是否可恢复
     */
    public boolean isRecoverable() {
        return false;
    }

    /**
     * 检查是否致命
     *
     * @return 是否致命
     */
    public boolean isFatal() {
        return false;
    }

    /**
     * 转换为字典（API/RPC/日志的标准结构化输出）
     *
     * @return 字典表示
     */
    public Map<String, Object> toDict() {
        Map<String, Object> dict = new HashMap<>();
        dict.put("code", code);
        dict.put("status", status.name());
        dict.put("message", templateMessage);
        dict.put("params", params);
        dict.put("raw_message", message);
        dict.put("details", details);
        return dict;
    }

    /**
     * 转换为 JSON 字符串
     *
     * @return JSON 字符串
     */
    public String toJson() {
        // 使用简单的 JSON 格式化
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"code\":").append(code).append(",");
        sb.append("\"status\":\"").append(status.name()).append("\",");
        sb.append("\"message\":\"").append(escapeJson(templateMessage)).append("\",");
        sb.append("\"params\":").append(mapToJson(params)).append(",");
        sb.append("\"raw_message\":\"").append(escapeJson(message)).append("\",");
        sb.append("\"details\":").append(valueToJson(details));
        sb.append("}");
        return sb.toString();
    }

    /**
     * 转义 JSON 字符串
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "null";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * 将 Map 转换为 JSON 字符串
     */
    private String mapToJson(Map<String, Object> map) {
        if (map == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            sb.append(valueToJson(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 将 Object 转换为 JSON 字符串
     */
    private String valueToJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + escapeJson((String) value) + "\"";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map) {
            return mapToJson((Map<String, Object>) value);
        }
        return "\"" + escapeJson(String.valueOf(value)) + "\"";
    }

    @Override
    public String toString() {
        return String.format("[%d] %s", code, message != null ? message : templateMessage);
    }

    /**
     * 创建 BaseError 的 Builder
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * BaseError 的 Builder 类
     */
    public static class Builder {
        private StatusCode status;
        private String message;
        private Object details;
        private Throwable cause;
        private Map<String, Object> params;

        public Builder() {
        }

        public Builder status(StatusCode status) {
            this.status = status;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
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

        public Builder params(Map<String, Object> params) {
            this.params = params;
            return this;
        }

        public BaseError build() {
            return new BaseError(status, message, details, cause, params);
        }
    }
}