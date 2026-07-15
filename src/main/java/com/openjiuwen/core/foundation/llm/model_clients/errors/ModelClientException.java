/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients.errors;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;

public class ModelClientException extends BaseError {
    public ModelClientException(ModelCallFailureInfo failureInfo, Throwable cause) {
        super(StatusCode.MODEL_CALL_FAILED, failureInfo.errorMessage(), failureInfo, cause, failureInfo.toParams());
    }

    public ModelCallFailureInfo getFailureInfo() {
        return (ModelCallFailureInfo) getDetails();
    }

    public ModelCallFailureStage getStage() {
        return getFailureInfo().stage();
    }
}
