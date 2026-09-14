/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.operator;

import com.openjiuwen.agentevolving.ApplyResult;
import com.openjiuwen.agentevolving.UpdateValue;

/**
 * Optional operator extension for local preview updates.
 *
 * <p>Mirrors Python's {@code PreviewableOperator} in
 * {@code openjiuwen/core/operator/base.py}.
 */
public abstract class PreviewableOperator extends Operator {

    /**
     * Applies a preview update without persisting pending lifecycle state.
     *
     * @param target parameter name
     * @param update structured update request
     * @return preview apply result
     */
    public abstract ApplyResult previewUpdate(String target, UpdateValue update);

    @Override
    public ApplyResult applyUpdate(String target, UpdateValue update) {
        return previewUpdate(target, update);
    }
}
