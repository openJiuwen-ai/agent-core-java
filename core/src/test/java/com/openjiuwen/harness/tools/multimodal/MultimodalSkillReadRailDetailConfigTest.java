/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.multimodal;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.harness.tools.mobile_gui.rails.MultimodalSkillReadRail;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Verifies that the wire {@code detail} hint of skill reference images is
 * configurable while the historical default stays explicit.
 */
class MultimodalSkillReadRailDetailConfigTest {
    private static final String MARKDOWN = "![logo](https://example.com/logo.png)";

    @Test
    void parseMarkdownToBlocks_defaultOverloads_keepExplicitLowDetail() {
        List<Map<String, Object>> blocks = MultimodalSkillReadRail.parseMarkdownToBlocks(MARKDOWN);

        assertThat(imageUrlOf(blocks)).containsEntry("detail", "low");
        assertThat(MultimodalSkillReadRail.DEFAULT_IMAGE_DETAIL).isEqualTo("low");
    }

    @Test
    void parseMarkdownToBlocks_configuredDetail_replacesHardcodedLow() {
        List<Map<String, Object>> blocks =
                MultimodalSkillReadRail.parseMarkdownToBlocks(MARKDOWN, null, "high");

        assertThat(imageUrlOf(blocks)).containsEntry("detail", "high");
    }

    @Test
    void parseMarkdownToBlocks_blankDetail_omitsFieldEntirely() {
        List<Map<String, Object>> blocks =
                MultimodalSkillReadRail.parseMarkdownToBlocks(MARKDOWN, null, " ");

        assertThat(imageUrlOf(blocks)).doesNotContainKey("detail");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> imageUrlOf(List<Map<String, Object>> blocks) {
        Map<String, Object> imageBlock = blocks.stream()
                .filter(block -> "image_url".equals(block.get("type")))
                .findFirst()
                .orElseThrow();
        return (Map<String, Object>) imageBlock.get("image_url");
    }
}
