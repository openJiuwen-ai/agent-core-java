/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.toolgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

@Tag("issue196-w1")
class ToolGroupTest {

    @Test
    void toolGroup_whenInputCollectionChanges_keepsInternalStateUnchanged() {
        List<String> toolNames = new ArrayList<>(List.of("search", "calculator"));
        ToolGroup toolGroup = new ToolGroup("research", "Research tools", toolNames);

        toolNames.add("browser");

        assertThat(toolGroup.toolNames()).containsExactly("search", "calculator");
        assertThatThrownBy(() -> toolGroup.toolNames().add("browser"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toolGroup_whenNameIsBlank_rejectsIt() {
        assertThatThrownBy(() -> new ToolGroup(" ", "Invalid", List.of("search")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toolGroup_whenToolNameIsBlank_rejectsIt() {
        assertThatThrownBy(
                        () -> new ToolGroup("research", "Research tools", List.of("search", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tool name must not be blank");
    }

    @Test
    void toolGroup_whenToolNameHasLeadingOrTrailingWhitespace_rejectsIt() {
        assertThatThrownBy(() -> new ToolGroup("research", "Research tools", List.of(" search ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leading or trailing whitespace");
    }

    @Test
    void toolGroup_whenDescriptionIsBlank_acceptsIt() {
        ToolGroup toolGroup = new ToolGroup("research", "", List.of("search"));

        assertThat(toolGroup.name()).isEqualTo("research");
        assertThat(toolGroup.description()).isEmpty();
        assertThat(toolGroup.toolNames()).containsExactly("search");
    }
}
