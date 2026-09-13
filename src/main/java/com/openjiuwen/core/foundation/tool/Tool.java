/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.Iterator;
import java.util.Map;

/**
 * Abstract base class for all tools.
 * <p>
 * Defines the contract for tool invocation and streaming.
 * Mirrors Python's {@code Tool} ABC from <code>foundation/tool/base.py</code>.
 * <p>
 * Usage:
 * 
 * <pre>
 * Tool myTool = new LocalFunction(card, myFunction);
 * Map&lt;String, Object&gt; result = myTool.invoke(inputs);
 * </pre>
 * 
 * @see ToolCard
 * @since 0.1.7
 */
public abstract class Tool {
    /**
     * card.
     * 
     * @since 0.1.7
     */
    protected final ToolCard card;

    /**
     * Construct a new tool with the given configuration card.
     * 
     * @param card the tool card; must not be null and must have a valid id
     * @since 0.1.7
     */
    protected Tool(ToolCard card) {
        if (card == null) {
            throw ErrorHelper.buildError(StatusCode.TOOL_CARD_INVALID, "card", "None", "reason", "card is None");
        }
        if (card.getId() == null || card.getId().isBlank()) {
            throw ErrorHelper.buildError(StatusCode.TOOL_CARD_INVALID, "card", "id=''", "reason",
                    "card id is None or empty");
        }
        this.card = card;
    }

    /**
     * Get the tool card.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ToolCard getCard() {
        return card;
    }

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception;

    /**
     * Execute the tool with provided inputs (no extra kwargs).
     * 
     * @param inputs inputs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Object invoke(Map<String, Object> inputs) throws Exception {
        return invoke(inputs, Map.of());
    }

    /**
     * stream.
     * 
     * @param inputs inputs
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception;

    /**
     * Execute the tool and stream incremental results (no extra kwargs).
     * 
     * @param inputs inputs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public Iterator<Object> stream(Map<String, Object> inputs) throws Exception {
        return stream(inputs, Map.of());
    }
}
