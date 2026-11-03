/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.schema;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.Map;

/**
 * Focused smoke for {@link AgentCard}.
 */
public final class AgentCardTest {

    private AgentCardTest() {
    }

    public static void main(String[] args) {
        AgentCard card = new AgentCard("agent-1", "helper", "does work");
        require("agent-1".equals(card.getId()), "id");
        require("helper".equals(card.getName()), "name");
        require("does work".equals(card.getDescription()), "description");
        require(card.toolInfo().getParameters().isEmpty(), "empty input params");

        card.setInputParams(Map.of("type", "object"));
        card.setOutputParams(Map.of("ok", true));
        card.setInterfaceUrl("http://127.0.0.1:8000");
        ToolInfo info = card.toolInfo();
        require("helper".equals(info.getName()), "tool name");
        require("does work".equals(info.getDescription()), "tool description");
        require("object".equals(info.getParameters().get("type")), "tool parameters");
        require("http://127.0.0.1:8000".equals(card.getInterfaceUrl()), "interface url");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
