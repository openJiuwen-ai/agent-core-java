// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 基础模型信息配置类。
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/schema/mode_info.py - BaseModelInfo
 */
public class BaseModelInfo {
    private final String apiKey;
    private final String apiBase;
    private final String modelName;
    private final double temperature;
    private final double topP;
    private final boolean streaming;
    private final int timeout;
    private final Map<String, Object> extraParams;

    private BaseModelInfo(Builder builder) {
        this.apiKey = builder.apiKey;
        this.apiBase = builder.apiBase;
        this.modelName = builder.modelName;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.streaming = builder.streaming;
        this.timeout = builder.timeout;
        this.extraParams = new HashMap<>(builder.extraParams);
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiBase() {
        return apiBase;
    }

    public String getModelName() {
        return modelName;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getTopP() {
        return topP;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public int getTimeout() {
        return timeout;
    }

    public Map<String, Object> getExtraParams() {
        return new HashMap<>(extraParams);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseModelInfo that = (BaseModelInfo) o;
        return Objects.equals(apiBase, that.apiBase) &&
                Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiBase, modelName);
    }

    /**
     * Builder类
     */
    public static class Builder {
        private String apiKey = "";
        private String apiBase;
        private String modelName = "";
        private boolean modelNameSetDirectly = false;  // 跟踪是否直接设置了modelName
        private double temperature = 0.95;
        private double topP = 0.1;
        private boolean streaming = false;
        private int timeout = 60;
        private final Map<String, Object> extraParams = new HashMap<>();

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder apiBase(String apiBase) {
            this.apiBase = apiBase;
            return this;
        }

        /**
         * 设置model（别名，等同于modelName）
         * 注意：这是推荐的设置方式，直接使用modelName()可能导致值被重置为空
         */
        public Builder model(String model) {
            this.modelName = model;
            this.modelNameSetDirectly = false;  // 通过alias设置
            return this;
        }

        /**
         * 直接设置modelName（不推荐）
         * 注意：为了与Python Pydantic行为一致，直接设置modelName会在build时被重置为空字符串
         * 请使用model()方法代替
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            this.modelNameSetDirectly = true;  // 标记为直接设置
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(double topP) {
            this.topP = topP;
            return this;
        }

        /**
         * 设置stream（别名，等同于streaming）
         */
        public Builder stream(boolean stream) {
            this.streaming = stream;
            return this;
        }

        public Builder streaming(boolean streaming) {
            this.streaming = streaming;
            return this;
        }

        public Builder timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder extraParam(String key, Object value) {
            this.extraParams.put(key, value);
            return this;
        }

        public BaseModelInfo build() {
            // 验证必填字段
            if (apiBase == null || apiBase.isEmpty()) {
                throw new IllegalArgumentException("apiBase is required and must have at least 1 character");
            }
            // 验证timeout > 0
            if (timeout <= 0) {
                throw new IllegalArgumentException("timeout must be greater than 0");
            }
            
            // 模拟Python Pydantic的行为：如果直接设置了modelName（而不是通过alias），则重置为空
            // 这是Pydantic的一个特殊行为，当Field有alias时，直接使用字段名可能导致值被重置
            if (modelNameSetDirectly) {
                this.modelName = "";
            }
            
            return new BaseModelInfo(this);
        }
    }
}

