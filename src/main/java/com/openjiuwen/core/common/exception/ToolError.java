/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import com.openjiuwen.core.common.schema.BaseCard;

import java.util.Map;

/**
 * Mirrors Python's {@code ToolError} in
 * {@code openjiuwen/core/common/exception/errors.py}.
 */
public class ToolError extends ExecutionError {
    private final BaseCard card;

    public ToolError(StatusCode status, String msg, Object details, Throwable cause, BaseCard card, Map<String, Object> params) {
        super(status, msg, card != null ? null : details, cause, params);
        this.card = copyCard(card);
    }

    public ToolError(StatusCode status, Map<String, Object> params) {
        super(status, params);
        this.card = null;
    }

    public ToolError(StatusCode status) {
        super(status);
        this.card = null;
    }

    public BaseCard getCard() {
        return card;
    }

    private static BaseCard copyCard(BaseCard card) {
        if (card == null) {
            return null;
        }
        return new BaseCard(card.getId(), card.getName(), card.getDescription());
    }
}
