package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.foundation.tool.ToolCard;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * RESTful API工具卡片
 * 
 * <p>包含HTTP请求的配置信息，并进行方法和URL验证。
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public class RestfulApiCard extends ToolCard {
    
    /**
     * 支持的HTTP方法
     */
    public static final Set<String> SUPPORTED_METHODS = Set.of("POST", "GET");
    
    private String url;
    private String method;
    private Map<String, Object> headers;
    private Map<String, Object> queries;
    private Map<String, Object> paths;
    private float timeout;
    private int maxResponseByteSize;
    
    /**
     * 默认构造器
     */
    public RestfulApiCard() {
        super();
        this.method = "POST";
        this.headers = new HashMap<>();
        this.queries = new HashMap<>();
        this.paths = new HashMap<>();
        this.timeout = 60.0f;
        this.maxResponseByteSize = 10 * 1024 * 1024;
    }
    
    /**
     * 完整构造器
     */
    public RestfulApiCard(String name, String description, String url, String method,
                         Map<String, Object> headers, Map<String, Object> queries,
                         Map<String, Object> paths, float timeout, int maxResponseByteSize,
                         Object inputParams) {
        super(name, description, inputParams);
        this.url = validateUrl(url);
        this.method = validateMethod(method);
        this.headers = headers != null ? headers : new HashMap<>();
        this.queries = queries != null ? queries : new HashMap<>();
        this.paths = paths != null ? paths : new HashMap<>();
        this.timeout = validateTimeout(timeout);
        this.maxResponseByteSize = maxResponseByteSize > 0 ? maxResponseByteSize : 10 * 1024 * 1024;
    }
    
    // Validation methods
    
    private String validateMethod(String method) {
        if (method == null) {
            return "POST";
        }
        String upperMethod = method.toUpperCase();
        if (!SUPPORTED_METHODS.contains(upperMethod)) {
            Map<String, Object> params = Map.of(
                "reason", "support invalid method, method=" + method + ", only accepts: " + SUPPORTED_METHODS
            );
            throw ErrorBuilder.build(StatusCode.TOOL_RESTFUL_API_CARD_CONFIG_INVALID, null, null, null, params);
        }
        return upperMethod;
    }
    
    private String validateUrl(String url) {
        try {
            // 临时替换URL中的占位符（{...}或<...>）以便验证基本URL格式
            String urlToCheck = url.replaceAll("\\{[^}]+\\}", "123");
            urlToCheck = urlToCheck.replaceAll("<[^>]+>", "123");
            UrlUtils.checkUrlIsValid(urlToCheck);
            return url;
        } catch (Exception e) {
            Map<String, Object> params = Map.of(
                "reason", "support invalid url, url=" + url
            );
            throw ErrorBuilder.build(StatusCode.TOOL_RESTFUL_API_CARD_CONFIG_INVALID, null, null, e, params);
        }
    }
    
    private float validateTimeout(float timeout) {
        if (timeout < 1.0f || timeout > 300.0f) {
            return 60.0f;
        }
        return timeout;
    }
    
    // Getters and Setters
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = validateUrl(url);
    }
    
    public String getMethod() {
        return method;
    }
    
    public void setMethod(String method) {
        this.method = validateMethod(method);
    }
    
    public Map<String, Object> getHeaders() {
        return headers;
    }
    
    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers != null ? headers : new HashMap<>();
    }
    
    public Map<String, Object> getQueries() {
        return queries;
    }
    
    public void setQueries(Map<String, Object> queries) {
        this.queries = queries != null ? queries : new HashMap<>();
    }
    
    public Map<String, Object> getPaths() {
        return paths;
    }
    
    public void setPaths(Map<String, Object> paths) {
        this.paths = paths != null ? paths : new HashMap<>();
    }
    
    public float getTimeout() {
        return timeout;
    }
    
    public void setTimeout(float timeout) {
        this.timeout = validateTimeout(timeout);
    }
    
    public int getMaxResponseByteSize() {
        return maxResponseByteSize;
    }
    
    public void setMaxResponseByteSize(int maxResponseByteSize) {
        this.maxResponseByteSize = maxResponseByteSize > 0 ? maxResponseByteSize : 10 * 1024 * 1024;
    }
    
    /**
     * Builder类
     */
    public static class Builder {
        private String id;
        private String name;
        private String description;
        private Object inputParams;
        private String url;
        private String method = "POST";
        private Map<String, Object> headers = new HashMap<>();
        private Map<String, Object> queries = new HashMap<>();
        private Map<String, Object> paths = new HashMap<>();
        private float timeout = 60.0f;
        private int maxResponseByteSize = 10 * 1024 * 1024;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder inputParams(Object inputParams) {
            this.inputParams = inputParams;
            return this;
        }
        
        public Builder url(String url) {
            this.url = url;
            return this;
        }
        
        public Builder method(String method) {
            this.method = method;
            return this;
        }
        
        public Builder headers(Map<String, Object> headers) {
            this.headers = headers;
            return this;
        }
        
        public Builder queries(Map<String, Object> queries) {
            this.queries = queries;
            return this;
        }
        
        public Builder paths(Map<String, Object> paths) {
            this.paths = paths;
            return this;
        }
        
        public Builder timeout(float timeout) {
            this.timeout = timeout;
            return this;
        }
        
        public Builder maxResponseByteSize(int maxResponseByteSize) {
            this.maxResponseByteSize = maxResponseByteSize;
            return this;
        }
        
        public RestfulApiCard build() {
            RestfulApiCard card = new RestfulApiCard(
                name, 
                description, 
                url, 
                method,
                headers, 
                queries, 
                paths, 
                timeout, 
                maxResponseByteSize,
                inputParams
            );
            if (id != null && !id.isEmpty()) {
                card.setId(id);
            }
            return card;
        }
    }
    
    /**
     * 创建RestfulApiCard Builder
     * 
     * @return Builder实例
     */
    public static Builder newBuilder() {
        return new Builder();
    }
}

