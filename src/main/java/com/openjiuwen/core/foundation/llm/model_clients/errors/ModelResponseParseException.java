/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients.errors;

public class ModelResponseParseException extends ModelClientException {
    public ModelResponseParseException(ModelResponseParseFailureInfo failureInfo, Throwable cause) {
        super(failureInfo, cause);
    }

    @Override
    public ModelResponseParseFailureInfo getFailureInfo() {
        return (ModelResponseParseFailureInfo) super.getFailureInfo();
    }
}
