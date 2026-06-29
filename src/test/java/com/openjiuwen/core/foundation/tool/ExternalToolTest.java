/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalToolTest {

    @Test
    void wrapsToolCardAndExposesSameToolInfoShapeAsNormalTool() {
        ToolCard card = ToolCard.builder()
                .id("external.frontend_read_text_input")
                .name("frontend_read_text_input")
                .description("Read text from browser input")
                .inputParams(Map.of("type", "object", "properties", Map.of("field_id", Map.of("type", "string"))))
                .build();

        ExternalTool tool = new ExternalTool(card);
        ToolInfo info = tool.toolInfo();

        assertThat(tool.getCard()).isSameAs(card);
        assertThat(info.getType()).isEqualTo("function");
        assertThat(info.getName()).isEqualTo("frontend_read_text_input");
        assertThat(info.getDescription()).isEqualTo("Read text from browser input");
        assertThat(info.getParameters()).containsEntry("type", "object");
    }

    @Test
    void rejectsMissingCardAndBlankName() {
        assertThatThrownBy(() -> new ExternalTool(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("card");

        ToolCard blankName = ToolCard.builder().id("external.blank").name(" ").description("bad").build();
        assertThatThrownBy(() -> new ExternalTool(blankName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }
}
