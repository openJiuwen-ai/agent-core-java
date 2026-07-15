/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients.errors;

public enum ModelCallFailureStage {
    HTTP_STATUS,
    TRANSPORT,
    RESPONSE_PARSE,
    STREAM,
    CLIENT_INTERNAL
}
