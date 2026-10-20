/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.tool_discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.tool.ToolCard;

import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * The model must see {@code search_tools} / {@code load_tools} with their parameter schemas.
 *
 * @since 2026-10-20
 */
class ToolDiscoveryToolCardTest {

    @Test
    void testSearchToolsCardUsesFunctionNameAndSchema() {
        ToolCard card = new SearchToolsTool(null, null).getCard();

        assertThat(card.getName()).isEqualTo("search_tools");
        assertThat(card.getId()).isEqualTo("SearchToolsTool");
        assertThat(properties(card)).containsKeys("query", "limit", "detail_level");
    }

    @Test
    void testLoadToolsCardUsesFunctionNameAndSchema() {
        ToolCard card = new LoadToolsTool(null).getCard();

        assertThat(card.getName()).isEqualTo("load_tools");
        assertThat(card.getId()).isEqualTo("LoadToolsTool");
        assertThat(properties(card)).containsKeys("tool_names", "replace");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(ToolCard card) {
        Object properties = card.getInputParams().get("properties");
        if (!(properties instanceof Map<?, ?> propertiesMap)) {
            throw new IllegalStateException("properties map expected");
        }
        return (Map<String, Object>) propertiesMap;
    }
}
