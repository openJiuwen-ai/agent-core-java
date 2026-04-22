package com.openjiuwen.harness.prompts.sections.tools;

import com.openjiuwen.core.foundation.tool.ToolCard;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolMetadataRegistryTest {

    @Test
    void askUserProviderExposesPythonAlignedMetadata() {
        ToolCard card = ToolMetadataRegistry.buildToolCard("ask_user", "ask_user", "cn");

        assertThat(card.getName()).isEqualTo("ask_user");
        assertThat(card.getId()).isEqualTo("ask_user");
        assertThat(card.getDescription()).isEqualTo("中断执行并向用户请求输入");
        assertThat(card.getInputParams()).containsEntry("type", "object");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) card.getInputParams().get("properties");
        assertThat(properties).containsKey("query");
    }
}
