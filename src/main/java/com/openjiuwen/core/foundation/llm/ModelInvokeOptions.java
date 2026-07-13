/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import lombok.Builder;
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

    @Builder.Default
    Map<String, Object> extraFields = new LinkedHashMap<>();
}
