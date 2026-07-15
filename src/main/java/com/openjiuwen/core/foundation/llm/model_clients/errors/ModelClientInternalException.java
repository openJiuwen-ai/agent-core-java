/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients.errors;

public class ModelClientInternalException extends ModelClientException {
    public ModelClientInternalException(ModelClientInternalFailureInfo failureInfo, Throwable cause) {
        super(failureInfo, cause);
    }

    @Override
    public ModelClientInternalFailureInfo getFailureInfo() {
        return (ModelClientInternalFailureInfo) super.getFailureInfo();
    }
}
