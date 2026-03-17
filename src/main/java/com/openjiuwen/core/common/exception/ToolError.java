/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.exception;

import com.openjiuwen.core.common.schema.BaseCard;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool execution error — may carry a {@link BaseCard} reference.
 */
public class ToolError extends ExecutionError {

    private final BaseCard card;

    public ToolError(StatusCode status, String msg, Object details, Throwable cause,
                     BaseCard card, Map<String, Object> params) {
        super(status, msg, mergeCardDetails(details, card), cause, params);
        this.card = card != null ? card.copy() : null;
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

    private static Object mergeCardDetails(Object details, BaseCard card) {
        if (card == null) {
            return details;
        }
        if (details instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = new HashMap<>((Map<String, Object>) details);
            map.put("card", card);
            return map;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("card", card);
        if (details != null) {
            map.put("original_details", details);
        }
        return map;
    }
}
