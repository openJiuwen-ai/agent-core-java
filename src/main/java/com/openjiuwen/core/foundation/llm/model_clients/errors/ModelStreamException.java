/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients.errors;

public class ModelStreamException extends ModelClientException {
    public ModelStreamException(ModelStreamFailureInfo failureInfo, Throwable cause) {
        super(failureInfo, cause);
    }

    @Override
    public ModelStreamFailureInfo getFailureInfo() {
        return (ModelStreamFailureInfo) super.getFailureInfo();
    }
}
