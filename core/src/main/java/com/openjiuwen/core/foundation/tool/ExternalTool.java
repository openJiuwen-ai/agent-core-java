/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.Objects;

public final class ExternalTool {
    private final ToolCard card;

    public ExternalTool(ToolCard card) {
        if (card == null) {
            throw new IllegalArgumentException("ExternalTool card must not be null.");
        }
        if (card.getName() == null || card.getName().isBlank()) {
            throw new IllegalArgumentException("ExternalTool card name must not be blank.");
        }
        this.card = card;
    }

    public ToolCard getCard() {
        return card;
    }

    public ToolInfo toolInfo() {
        return card.toolInfo();
    }

    public ToolInfo tool_info() {
        return toolInfo();
    }

    @Override
    public String toString() {
        return "ExternalTool{name='%s', id='%s'}".formatted(
                Objects.toString(card.getName(), ""),
                Objects.toString(card.getId(), "")
        );
    }
}
