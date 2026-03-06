/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.sysop.registry.OperationRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter for converting SysOperation to LocalFunction tools.
 * <p>
 * Mirrors Python's {@code SysOperationToolAdapter} in {@code sys_operation/tool_adapter.py}.
 */
public final class SysOperationToolAdapter {

    private SysOperationToolAdapter() {
    }

    /**
     * A tuple of (toolId, LocalFunction).
     */
    public record ToolEntry(String toolId, LocalFunction localFunction) {
    }

    /**
     * Extract all tools from SysOperation and wrap them as LocalFunction instances.
     *
     * @param card     SysOperationCard containing operation metadata
     * @param instance SysOperation instance to extract tools from
     * @return list of (toolId, LocalFunction) entries ready for registration
     */
    public static List<ToolEntry> extractTools(SysOperationCard card, SysOperation instance) {
        List<ToolEntry> tools = new ArrayList<>();

        for (String opType : OperationRegistry.getSupportedOperations(card.getMode())) {
            BaseOperation subOp = instance.getOperation(opType);
            if (subOp == null) {
                continue;
            }

            List<ToolCard> toolCards = subOp.listTools();
            if (toolCards == null || toolCards.isEmpty()) {
                continue;
            }

            for (ToolCard toolCard : toolCards) {
                String toolId = SysOperationCard.generateToolId(card.getId(), opType, toolCard.getName());

                // Create a new card with the tool-specific ID
                ToolCard newCard = ToolCard.builder()
                        .id(toolId)
                        .name(toolCard.getName())
                        .description(toolCard.getDescription())
                        .inputParams(toolCard.getInputParams())
                        .build();

                // Wrap as LocalFunction — in the real scenario, reflect to the method
                LocalFunction localFunc = new LocalFunction(newCard, (Map<String, Object> inputs) -> {
                    // Delegate to the actual operation method
                    // This is a simplified version; in production, use reflection
                    return "Operation " + toolId + " invoked with inputs: " + inputs;
                });

                tools.add(new ToolEntry(toolId, localFunc));
            }
        }

        return tools;
    }

    /**
     * Get tool ID prefix for a sys operation.
     *
     * @param sysOperationId the sys operation card ID
     * @return prefix string ending with "."
     */
    public static String getToolIdPrefix(String sysOperationId) {
        return sysOperationId + ".";
    }
}
