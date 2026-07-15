/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients.errors;

public class ModelTransportException extends ModelClientException {
    public ModelTransportException(ModelTransportFailureInfo failureInfo, Throwable cause) {
        super(failureInfo, cause);
    }

    @Override
    public ModelTransportFailureInfo getFailureInfo() {
        return (ModelTransportFailureInfo) super.getFailureInfo();
    }
}
