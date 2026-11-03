/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallArgumentUtilsTest {
    @Test
    void preservesValidObjectCharactersExactly() {
        String arguments = "{\n  \"query\" : [1, 2],\n  \"escaped\" : \"a\\\\b\\\"c\"\n}";

        assertThat(ToolCallArgumentUtils.repairJsonObject(arguments)).isEqualTo(arguments);
        assertThat(ToolCallArgumentUtils.fallbackJsonObject(arguments)).isEqualTo(arguments);
    }

    @Test
    void repairsMissingOrBlankHistoryArgumentsToEmptyObject() {
        assertThat(ToolCallArgumentUtils.repairJsonObject(null)).isEqualTo("{}");
        assertThat(ToolCallArgumentUtils.repairJsonObject("")).isEqualTo("{}");
        assertThat(ToolCallArgumentUtils.repairJsonObject(" \n\t ")).isEqualTo("{}");
    }

    @Test
    void appendsOnlyMissingContainerClosures() {
        assertRepairAppendsOnlyMissingClosures("{\"query\":[1,2", "{\"query\":[1,2]}");
        assertRepairAppendsOnlyMissingClosures("{\"nested\":{\"value\":1", "{\"nested\":{\"value\":1}}");
    }

    @Test
    void leavesUnrepairableOrNonObjectArgumentsUntouched() {
        assertThat(ToolCallArgumentUtils.repairJsonObject("{\"query\": bare}"))
                .isEqualTo("{\"query\": bare}");
        assertThat(ToolCallArgumentUtils.repairJsonObject("{\"query\":\"unterminated}"))
                .isEqualTo("{\"query\":\"unterminated}");
        assertThat(ToolCallArgumentUtils.repairJsonObject("{\"query\":[1,2}"))
                .isEqualTo("{\"query\":[1,2}");
        assertThat(ToolCallArgumentUtils.repairJsonObject("{\"query\":1,}"))
                .isEqualTo("{\"query\":1,}");
        assertThat(ToolCallArgumentUtils.repairJsonObject("[1,2")).isEqualTo("[1,2");
    }

    @Test
    void fallsBackModelRequestArgumentsThatAreNotOneCompleteObject() {
        assertThat(ToolCallArgumentUtils.fallbackJsonObject("[]")).isEqualTo("{}");
        assertThat(ToolCallArgumentUtils.fallbackJsonObject("\"text\"")).isEqualTo("{}");
        assertThat(ToolCallArgumentUtils.fallbackJsonObject("1")).isEqualTo("{}");
        assertThat(ToolCallArgumentUtils.fallbackJsonObject("true")).isEqualTo("{}");
        assertThat(ToolCallArgumentUtils.fallbackJsonObject("null")).isEqualTo("{}");
        assertThat(ToolCallArgumentUtils.fallbackJsonObject("{\"a\":1} trailing")).isEqualTo("{}");
    }

    @Test
    void directlyIdentifiesOnlyCompleteJsonObjects() {
        assertThat(ToolCallArgumentUtils.isJsonObject("{\"a\":1}")).isTrue();
        assertThat(ToolCallArgumentUtils.isJsonObject("[]")).isFalse();
        assertThat(ToolCallArgumentUtils.isJsonObject("{\"a\":1} trailing")).isFalse();
    }

    private static void assertRepairAppendsOnlyMissingClosures(String incomplete, String repaired) {
        String normalized = ToolCallArgumentUtils.repairJsonObject(incomplete);

        assertThat(normalized).isEqualTo(repaired);
        assertThat(normalized).startsWith(incomplete);
        assertThat(ToolCallArgumentUtils.isJsonObject(normalized)).isTrue();
    }
}
