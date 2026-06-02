/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.prompts;

import com.openjiuwen.harness.prompts.tools.ToolDescriptionRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/prompts/test_tool_descriptions.py}.
 */
class TestToolDescriptions {

    @Test
    void testCoreDescriptionsAreBilingual() {
        for (String name : coreToolNames()) {
            assertThat(ToolDescriptionRegistry.getToolDescription(name, "cn")).isNotBlank();
            assertThat(ToolDescriptionRegistry.getToolDescription(name, "en")).isNotBlank();
        }
    }

    @Test
    void testKnownToolCn() {
        assertThat(ToolDescriptionRegistry.getToolDescription("bash", "cn")).isNotBlank();
        assertThat(ToolDescriptionRegistry.getToolDescription("powershell", "cn")).isNotBlank();
    }

    @Test
    void testKnownToolEn() {
        assertThat(ToolDescriptionRegistry.getToolDescription("bash", "en")).isNotBlank();
        assertThat(ToolDescriptionRegistry.getToolDescription("powershell", "en")).isNotBlank();
    }

    @Test
    void testUnknownToolRaises() {
        assertThrows(
                ToolDescriptionRegistry.KeyError.class,
                () -> ToolDescriptionRegistry.getToolDescription("nonexistent", "cn")
        );
    }

    @Test
    void testAllRegisteredTools() {
        for (String name : coreToolNames()) {
            assertThat(ToolDescriptionRegistry.getToolDescription(name, "cn")).isNotBlank();
            assertThat(ToolDescriptionRegistry.getToolDescription(name, "en")).isNotBlank();
        }
    }

    @Test
    void testBuildToolCardUsesRegistryDescriptions() {
        Object bashDescription = ToolDescriptionRegistry.buildToolCard("bash", "BashTool", "en", null).get("description");
        Object powershellDescription =
                ToolDescriptionRegistry.buildToolCard("powershell", "PowerShellTool", "en", null).get("description");

        assertThat(bashDescription).isEqualTo(ToolDescriptionRegistry.getToolDescription("bash", "en"));
        assertThat(powershellDescription).isEqualTo(ToolDescriptionRegistry.getToolDescription("powershell", "en"));
    }

    @Test
    void testBuildToolCardUsesRegistryForVisionAndAudioTools() {
        assertThat(ToolDescriptionRegistry.buildToolCard("image_ocr", "ImageOCRTool", "en", null).get("description"))
                .isEqualTo(ToolDescriptionRegistry.getToolDescription("image_ocr", "en"));
        assertThat(ToolDescriptionRegistry.buildToolCard("audio_metadata", "AudioMetadataTool", "en", null).get("description"))
                .isEqualTo(ToolDescriptionRegistry.getToolDescription("audio_metadata", "en"));
    }

    private static List<String> coreToolNames() {
        return List.of(
                "bash",
                "powershell",
                "code",
                "read_file",
                "write_file",
                "edit_file",
                "glob",
                "list_files",
                "grep",
                "list_skill",
                "todo_create",
                "todo_list",
                "todo_modify",
                "image_ocr",
                "visual_question_answering",
                "audio_transcription",
                "audio_question_answering",
                "audio_metadata"
        );
    }
}
