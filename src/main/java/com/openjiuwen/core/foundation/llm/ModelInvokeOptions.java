/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional invocation parameters for {@link Model}.
 *
 * <p>Mirrors Python's keyword-only arguments accepted by {@code Model.invoke} in
 * {@code openjiuwen/core/foundation/llm/model.py}.</p>
 */
@Value
@Builder(toBuilder = true)
public class ModelInvokeOptions {

    List<?> tools;

    Float temperature;

    Float topP;

    Integer maxTokens;

    String stop;

    String model;

    BaseOutputParser outputParser;

    Float timeout;

    ModelRetryListener retryListener;

    @ToString.Exclude
    Map<String, String> requestHeaders;

    @Builder.Default
    Map<String, Object> extraFields = new LinkedHashMap<>();

    ModelInvokeOptions(List<?> tools, Float temperature, Float topP, Integer maxTokens,
                       String stop, String model, BaseOutputParser outputParser, Float timeout,
                       ModelRetryListener retryListener, Map<String, String> requestHeaders,
                       Map<String, Object> extraFields) {
        this.tools = tools;
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.stop = stop;
        this.model = model;
        this.outputParser = outputParser;
        this.timeout = timeout;
        this.retryListener = retryListener;
        this.requestHeaders = requestHeaders == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(requestHeaders);
        this.extraFields = extraFields;
    }

    ModelInvokeOptions(List<?> tools, Float temperature, Float topP, Integer maxTokens,
                       String stop, String model, BaseOutputParser outputParser, Float timeout,
                       ModelRetryListener retryListener, Map<String, Object> extraFields) {
        this(tools, temperature, topP, maxTokens, stop, model, outputParser, timeout,
                retryListener, new LinkedHashMap<>(), extraFields);
    }

    public Map<String, String> getRequestHeaders() {
        return new LinkedHashMap<>(requestHeaders);
    }

    public static class ModelInvokeOptionsBuilder {
        private Map<String, String> requestHeaders = new LinkedHashMap<>();

        public ModelInvokeOptionsBuilder requestHeaders(Map<String, String> requestHeaders) {
            this.requestHeaders = requestHeaders == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(requestHeaders);
            return this;
        }

        @Override
        public String toString() {
            return "ModelInvokeOptions.ModelInvokeOptionsBuilder("
                    + "tools=" + tools
                    + ", temperature=" + temperature
                    + ", topP=" + topP
                    + ", maxTokens=" + maxTokens
                    + ", stop=" + stop
                    + ", model=" + model
                    + ", outputParser=" + outputParser
                    + ", timeout=" + timeout
                    + ", retryListener=" + retryListener
                    + ")";
        }
    }
}
