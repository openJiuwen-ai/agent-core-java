// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.schema;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 模型请求配置类。
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/schema/config.py - ModelRequestConfig
 */
public class ModelRequestConfig {
    private final String modelName;
    private final double temperature;
    private final double topP;
    private final Integer maxTokens;
    private final String stop;
    private final Map<String, Object> extraParams;

    private ModelRequestConfig(Builder builder) {
        this.modelName = builder.modelName;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.maxTokens = builder.maxTokens;
        this.stop = builder.stop;
        this.extraParams = new HashMap<>(builder.extraParams);
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

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public String getStop() {
        return stop;
    }

    /**
     * 获取额外参数
     */
    public Object getExtraParam(String key) {
        return extraParams.get(key);
    }

    /**
     * 获取所有额外参数
     */
    public Map<String, Object> getExtraParams() {
        return new HashMap<>(extraParams);
    }

    /**
     * 导出为Map（排除null值）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        if (modelName != null) {
            result.put("model", modelName);
        }
        result.put("temperature", temperature);
        result.put("top_p", topP);
        if (maxTokens != null) {
            result.put("max_tokens", maxTokens);
        }
        if (stop != null) {
            result.put("stop", stop);
        }
        result.putAll(extraParams);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModelRequestConfig that = (ModelRequestConfig) o;
        return Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelName);
    }

    /**
     * Builder类
     */
    public static class Builder {
        private String modelName = "";
        private double temperature = 0.95;
        private double topP = 0.1;
        private Integer maxTokens;
        private String stop;
        private final Map<String, Object> extraParams = new HashMap<>();

        /**
         * 设置model（别名，实际设置modelName）
         */
        public Builder model(String model) {
            this.modelName = model;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
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

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder stop(String stop) {
            this.stop = stop;
            return this;
        }

        /**
         * 添加额外参数（支持pydantic的extra="allow"功能）
         */
        public Builder extraParam(String key, Object value) {
            this.extraParams.put(key, value);
            return this;
        }

        public ModelRequestConfig build() {
            return new ModelRequestConfig(this);
        }
    }
}

