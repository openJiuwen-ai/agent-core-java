/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.prompts;

import com.openjiuwen.harness.prompts.tools.ToolDescriptionRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/prompts/test_tool_input_params.py}.
 */
class TestToolInputParams {

    @Test
    void testCoreBuilders() {
        for (String name : coreToolNames()) {
            assertBilingualSchema(name);
        }
    }

    @Test
    void testExpectedRequiredFields() {
        assertThat(required("bash", "cn")).containsExactly("command");
        assertThat(required("powershell", "cn")).containsExactly("command");
        assertThat(required("code", "cn")).containsExactly("code");
        assertThat(required("read_file", "cn")).containsExactly("file_path");
        assertThat(required("write_file", "cn")).containsExactlyInAnyOrder("file_path", "content");
        assertThat(required("todo_create", "cn")).containsExactly("tasks");
        assertThat(required("image_ocr", "cn")).containsExactly("image_path_or_url");
        assertThat(required("visual_question_answering", "cn"))
                .containsExactly("image_path_or_url", "question");
        assertThat(required("audio_transcription", "cn")).containsExactly("audio_path_or_url");
        assertThat(required("audio_question_answering", "cn"))
                .containsExactly("audio_path_or_url", "question");
        assertThat(required("audio_metadata", "cn")).containsExactly("audio_path_or_url");
    }

    @Test
    void testAllRegisteredTools() {
        for (String name : coreToolNames()) {
            assertThat(ToolDescriptionRegistry.getToolInputParams(name, "cn").get("type")).isEqualTo("object");
        }
    }

    @Test
    void testUnknownToolRaises() {
        assertThrows(
                ToolDescriptionRegistry.KeyError.class,
                () -> ToolDescriptionRegistry.getToolInputParams("nonexistent", "cn")
        );
    }

    @Test
    void testRegistryMatchesDirectBuilder() {
        assertThat(ToolDescriptionRegistry.buildToolCard("bash", "BashTool", "cn", null).get("input_params"))
                .isEqualTo(ToolDescriptionRegistry.getToolInputParams("bash", "cn"));
        assertThat(ToolDescriptionRegistry.buildToolCard("powershell", "PowerShellTool", "cn", null).get("input_params"))
                .isEqualTo(ToolDescriptionRegistry.getToolInputParams("powershell", "cn"));
        assertThat(ToolDescriptionRegistry.buildToolCard("image_ocr", "ImageOCRTool", "en", null).get("input_params"))
                .isEqualTo(ToolDescriptionRegistry.getToolInputParams("image_ocr", "en"));
        assertThat(ToolDescriptionRegistry.buildToolCard("audio_metadata", "AudioMetadataTool", "en", null).get("input_params"))
                .isEqualTo(ToolDescriptionRegistry.getToolInputParams("audio_metadata", "en"));
    }

    @Test
    void testExistingToolsUseBuilderSchemasViaCardBuilder() {
        for (String name : List.of("bash", "powershell", "code", "read_file", "write_file", "edit_file", "glob", "list_files", "grep", "list_skill")) {
            assertThat(ToolDescriptionRegistry.buildToolCard(name, name, "en", null).get("input_params"))
                    .isEqualTo(ToolDescriptionRegistry.getToolInputParams(name, "en"));
        }
    }

    @Test
    void testVisionToolsUseBuildersViaCardBuilder() {
        for (String name : List.of("image_ocr", "visual_question_answering")) {
            assertThat(ToolDescriptionRegistry.buildToolCard(name, name, "en", null).get("input_params"))
                    .isEqualTo(ToolDescriptionRegistry.getToolInputParams(name, "en"));
        }
    }

    @Test
    void testAudioToolsUseBuildersViaCardBuilder() {
        for (String name : List.of("audio_transcription", "audio_question_answering", "audio_metadata")) {
            assertThat(ToolDescriptionRegistry.buildToolCard(name, name, "en", null).get("input_params"))
                    .isEqualTo(ToolDescriptionRegistry.getToolInputParams(name, "en"));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(String name, String language) {
        return (List<String>) ToolDescriptionRegistry.getToolInputParams(name, language).get("required");
    }

    @SuppressWarnings("unchecked")
    private static void assertBilingualSchema(String name) {
        Map<String, Object> cn = ToolDescriptionRegistry.getToolInputParams(name, "cn");
        Map<String, Object> en = ToolDescriptionRegistry.getToolInputParams(name, "en");
        assertThat(cn.get("type")).isEqualTo("object");
        assertThat(en.get("type")).isEqualTo("object");
        Map<String, Object> cnProps = (Map<String, Object>) cn.get("properties");
        Map<String, Object> enProps = (Map<String, Object>) en.get("properties");
        assertThat(cnProps.keySet()).isEqualTo(enProps.keySet());
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
