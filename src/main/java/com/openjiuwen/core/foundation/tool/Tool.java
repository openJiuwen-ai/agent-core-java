/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for all tools.
 * <p>
 * Defines the contract for tool invocation and streaming.
 * Mirrors Python's {@code Tool} ABC from <code>foundation/tool/base.py</code>.
 *
 * <p>Usage:
 * <pre>
 *   Tool myTool = new LocalFunction(card, myFunction);
 *   Map&lt;String, Object&gt; result = myTool.invoke(inputs);
 * </pre>
 *
 * @see ToolCard
 */
public abstract class Tool {

    /** The tool configuration card. */
    protected final ToolCard card;

    /**
     * Construct a new tool with the given configuration card.
     *
     * @param card the tool card; must not be null and must have a valid id
     * @throws com.openjiuwen.core.common.exception.ToolError if card is invalid
     */
    protected Tool(ToolCard card) {
        if (card == null) {
            throw ErrorHelper.buildError(StatusCode.TOOL_CARD_INVALID, "card", "null", "reason", "card is None");
        }
        if (card.getId() == null || card.getId().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.TOOL_CARD_INVALID,
                    "card", card.toString(), "reason", "card id is None or empty");
        }
        this.card = card;
    }

    /**
     * Get the tool card.
     */
    public ToolCard getCard() {
        return card;
    }

    /**
     * Execute the tool with provided inputs and return the final result.
     * <p>
     * This method performs complete tool execution in a single call.
     * In Java, async behavior is achieved via Virtual Threads or CompletableFuture
     * at the caller's discretion.
     *
     * @param inputs structured input data conforming to the tool's input schema
     * @param kwargs additional execution parameters
     * @return the complete result of tool execution
     * @throws Exception if execution fails
     */
    public abstract Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception;

    /**
     * Execute the tool with provided inputs (no extra kwargs).
     */
    public Object invoke(Map<String, Object> inputs) throws Exception {
        return invoke(inputs, Map.of());
    }

    /**
     * Execute the tool and stream incremental results.
     * <p>
     * Returns an {@link Iterator} to yield partial results as they become available.
     * For reactive streaming, callers can wrap this in a Reactor Flux.
     *
     * @param inputs structured input data conforming to the tool's input schema
     * @param kwargs additional execution parameters
     * @return an iterator of incremental results
     * @throws Exception if execution fails
     */
    public abstract Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception;

    /**
     * Execute the tool and stream incremental results (no extra kwargs).
     */
    public Iterator<Object> stream(Map<String, Object> inputs) throws Exception {
        return stream(inputs, Map.of());
    }
}
