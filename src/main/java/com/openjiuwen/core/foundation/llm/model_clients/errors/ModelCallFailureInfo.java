/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients.errors;

import java.util.LinkedHashMap;
import java.util.Map;

public sealed interface ModelCallFailureInfo permits ModelHttpFailureInfo, ModelTransportFailureInfo,
        ModelResponseParseFailureInfo, ModelStreamFailureInfo, ModelClientInternalFailureInfo {
    ModelCallFailureStage stage();

    String modelProvider();

    String apiBase();

    boolean streaming();

    String errorMessage();

    default Map<String, Object> toParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("error_msg", errorMessage());
        params.put("failure_stage", stage().name());
        params.put("model_provider", modelProvider());
        params.put("api_base", apiBase());
        params.put("streaming", streaming());
        return params;
    }
}
