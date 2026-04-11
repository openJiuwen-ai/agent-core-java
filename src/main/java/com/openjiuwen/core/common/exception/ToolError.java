/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Creates a ToolError with full details including card reference.
     *
     * @param status  the status code
     * @param msg     optional custom message
     * @param details optional additional details
     * @param cause   optional root cause
     * @param card    the tool card that caused the error
     * @param params  template parameters for message rendering
     */
    public ToolError(StatusCode status, String msg, Object details, Throwable cause,
                     BaseCard card, Map<String, Object> params) {
        super(status, msg, mergeCardDetails(details, card), cause, params);
        this.card = card != null ? card.copy() : null;
    }

    /**
     * Creates a ToolError with status and parameters.
     *
     * @param status the status code
     * @param params template parameters for message rendering
     */
    public ToolError(StatusCode status, Map<String, Object> params) {
        super(status, params);
        this.card = null;
    }

    /**
     * Creates a ToolError with status only.
     *
     * @param status the status code
     */
    public ToolError(StatusCode status) {
        super(status);
        this.card = null;
    }

    /**
     * Gets the tool card that caused this error.
     *
     * @return the tool card, or null if not available
     */
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
