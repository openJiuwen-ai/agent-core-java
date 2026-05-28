/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.base;

import com.openjiuwen.core.foundation.tool.ToolCard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * BaseOperation for file, code, shell and so on.
 *
 * <p>Mirrors Python's {@code BaseOperation} in
 * {@code openjiuwen.core.sys_operation.base}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseOperation {

    /** Operation name. */
    protected String name;

    /** Operation mode. */
    protected OperationMode mode;

    /** Operation description. */
    protected String description;

    /** Run configuration. */
    protected Object runConfig;

    /**
     * Retrieves a list of tool cards.
     *
     * @return List of ToolCard objects containing tool information
     */
    public abstract List<ToolCard> listTools();

    /**
     * Generate tool cards from method names.
     *
     * @param methodNames list of method names
     * @return list of ToolCard objects
     */
    protected List<ToolCard> generateToolCards(List<String> methodNames) {
        // Placeholder implementation
        return new java.util.ArrayList<>();
    }
}