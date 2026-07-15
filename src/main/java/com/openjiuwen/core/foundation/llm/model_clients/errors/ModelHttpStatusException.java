/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients.errors;

public class ModelHttpStatusException extends ModelClientException {
    public ModelHttpStatusException(ModelHttpFailureInfo failureInfo, Throwable cause) {
        super(failureInfo, cause);
    }

    @Override
    public ModelHttpFailureInfo getFailureInfo() {
        return (ModelHttpFailureInfo) super.getFailureInfo();
    }
}
