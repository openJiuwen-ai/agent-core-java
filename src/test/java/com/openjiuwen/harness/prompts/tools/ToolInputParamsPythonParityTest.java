/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.tools.CodeTool;
import com.openjiuwen.harness.tools.FilesystemTools;
import com.openjiuwen.harness.tools.multimodal.AudioTools;
import com.openjiuwen.harness.tools.multimodal.VisionTools;
import com.openjiuwen.harness.tools.shell.bash.BashTool;
import com.openjiuwen.harness.tools.shell.powershell.PowerShellTool;
import com.openjiuwen.harness.tools.skills.ListSkillTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's tool input-parameter tests in
 * {@code tests/unit_tests/harness/prompts/test_tool_input_params.py}.</p>
 */
class ToolInputParamsPythonParityTest {

    @Test
    void coreBuilders() {
        for (Function<String, Map<String, Object>> builder : builders()) {
            assertBilingualDescriptionsDiffer(builder);
        }
    }

    @Test
    void expectedRequiredFields() {
        assertEquals(List.of("command"), new BashMetadataProvider().getInputParams("cn").get("required"));
        assertEquals(List.of("command"), new PowerShellMetadataProvider().getInputParams("cn").get("required"));
        assertEquals(List.of("code"), new CodePromptToolProviders.CodeMetadataProvider().getInputParams("cn")
                .get("required"));
        assertEquals(List.of("file_path"), new FilesystemPromptToolProviders.ReadFileMetadataProvider()
                .getInputParams("cn").get("required"));
        assertEquals(Set.of("file_path", "content"), Set.copyOf(required(
                new FilesystemPromptToolProviders.WriteFileMetadataProvider().getInputParams("cn"))));
        assertEquals(List.of("tasks"), new TodoPromptToolProviders.TodoCreateMetadataProvider()
                .getInputParams("cn").get("required"));
        assertEquals(List.of("image_path_or_url"), new ImageOCRMetadataProvider().getInputParams("cn")
                .get("required"));
        assertEquals(List.of("image_path_or_url", "question"),
                new VisualQuestionAnsweringMetadataProvider().getInputParams("cn").get("required"));
        assertEquals(List.of("audio_path_or_url"),
                new AudioPromptToolProviders.AudioTranscriptionMetadataProvider().getInputParams("cn")
                        .get("required"));
        assertEquals(List.of("audio_path_or_url", "question"),
                new AudioPromptToolProviders.AudioQuestionAnsweringMetadataProvider().getInputParams("cn")
                        .get("required"));
        assertEquals(List.of("audio_path_or_url"),
                new AudioPromptToolProviders.AudioMetadataMetadataProvider().getInputParams("cn")
                        .get("required"));
    }

    @Test
    void allRegisteredTools() {
        for (String name : registeredToolNames()) {
            Map<String, Object> schema = HarnessPromptToolsPackage.getToolInputParams(name, "cn");

            assertEquals("object", schema.get("type"));
        }
    }

    @Test
    void unknownToolRaises() {
        assertThrows(
                NoSuchElementException.class,
                () -> HarnessPromptToolsPackage.getToolInputParams("nonexistent", "cn")
        );
    }

    @Test
    void registryMatchesDirectBuilder() {
        assertEquals(new BashMetadataProvider().getInputParams("cn"),
                HarnessPromptToolsPackage.getToolInputParams("bash", "cn"));
        assertEquals(new PowerShellMetadataProvider().getInputParams("cn"),
                HarnessPromptToolsPackage.getToolInputParams("powershell", "cn"));
        assertEquals(new ImageOCRMetadataProvider().getInputParams("en"),
                HarnessPromptToolsPackage.getToolInputParams("image_ocr", "en"));
        assertEquals(new AudioPromptToolProviders.AudioMetadataMetadataProvider().getInputParams("en"),
                HarnessPromptToolsPackage.getToolInputParams("audio_metadata", "en"));
    }

    @Test
    void existingToolsUseBuilders() {
        assertToolInputParams(new BashTool(), new BashMetadataProvider().getInputParams("en"));
        assertToolInputParams(new PowerShellTool(), new PowerShellMetadataProvider().getInputParams("en"));
        assertToolInputParams(new CodeTool((code, language, timeoutSeconds, kwargs) ->
                new CodeTool.CodeExecutionResult("", "", 0)),
                new CodePromptToolProviders.CodeMetadataProvider().getInputParams("en"));

        assertToolInputParams(new FilesystemTools.ReadFileTool("."), new FilesystemPromptToolProviders
                .ReadFileMetadataProvider().getInputParams("en"));
        assertToolInputParams(new FilesystemTools.WriteFileTool("."), new FilesystemPromptToolProviders
                .WriteFileMetadataProvider().getInputParams("en"));
        assertToolInputParams(new FilesystemTools.EditFileTool("."), new FilesystemPromptToolProviders
                .EditFileMetadataProvider().getInputParams("en"));
        assertToolInputParams(new FilesystemTools.GlobTool("."), new FilesystemPromptToolProviders
                .GlobMetadataProvider().getInputParams("en"));
        assertToolInputParams(new FilesystemTools.ListDirTool("."), new FilesystemPromptToolProviders
                .ListDirMetadataProvider().getInputParams("en"));
        assertToolInputParams(new FilesystemTools.GrepTool("."), new FilesystemPromptToolProviders
                .GrepMetadataProvider().getInputParams("en"));
        assertToolInputParams(new ListSkillTool(() -> List.of()), new ListSkillMetadataProvider().getInputParams("en"));
    }

    @Test
    void visionToolsUseBuilders() {
        assertToolInputParams(new VisionTools.ImageOcrTool(null), new ImageOCRMetadataProvider().getInputParams("en"));
        assertToolInputParams(new VisionTools.VisualQuestionAnsweringTool(null),
                new VisualQuestionAnsweringMetadataProvider().getInputParams("en"));
    }

    @Test
    void audioToolsUseBuilders() {
        assertToolInputParams(new AudioTools.AudioTranscriptionTool(null),
                new AudioPromptToolProviders.AudioTranscriptionMetadataProvider().getInputParams("en"));
        assertToolInputParams(new AudioTools.AudioQuestionAnsweringTool(null),
                new AudioPromptToolProviders.AudioQuestionAnsweringMetadataProvider().getInputParams("en"));
        assertToolInputParams(new AudioTools.AudioMetadataTool(null),
                new AudioPromptToolProviders.AudioMetadataMetadataProvider().getInputParams("en"));
    }

    private static void assertToolInputParams(Tool tool, Map<String, Object> expectedSchema) {
        assertEquals(expectedSchema, tool.getCard().getInputParams());
    }

    private static void assertBilingualDescriptionsDiffer(Function<String, Map<String, Object>> builder) {
        Map<String, Object> cn = builder.apply("cn");
        Map<String, Object> en = builder.apply("en");
        assertValidSchema(cn);
        assertValidSchema(en);
        Map<String, Object> cnProperties = stringObjectMap(cn.get("properties"));
        Map<String, Object> enProperties = stringObjectMap(en.get("properties"));
        if (cnProperties.isEmpty()) {
            return;
        }
        boolean anyDiffer = false;
        for (String key : cnProperties.keySet()) {
            Map<String, Object> cnProperty = stringObjectMap(cnProperties.get(key));
            Map<String, Object> enProperty = stringObjectMap(enProperties.get(key));
            String cnDescription = String.valueOf(cnProperty.getOrDefault("description", ""));
            String enDescription = String.valueOf(enProperty.getOrDefault("description", ""));
            assertFalse(cnDescription.isBlank());
            assertFalse(enDescription.isBlank());
            if (!cnDescription.equals(enDescription)) {
                anyDiffer = true;
            }
        }
        assertTrue(anyDiffer);
    }

    private static void assertValidSchema(Map<String, Object> schema) {
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));
        assertTrue(schema.containsKey("required"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(Map<String, Object> schema) {
        return (List<String>) schema.get("required");
    }

    private static Map<String, Object> stringObjectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    private static List<Function<String, Map<String, Object>>> builders() {
        return List.of(
                language -> new BashMetadataProvider().getInputParams(language),
                language -> new PowerShellMetadataProvider().getInputParams(language),
                language -> new CodePromptToolProviders.CodeMetadataProvider().getInputParams(language),
                language -> new FilesystemPromptToolProviders.ReadFileMetadataProvider().getInputParams(language),
                language -> new FilesystemPromptToolProviders.WriteFileMetadataProvider().getInputParams(language),
                language -> new FilesystemPromptToolProviders.EditFileMetadataProvider().getInputParams(language),
                language -> new FilesystemPromptToolProviders.GlobMetadataProvider().getInputParams(language),
                language -> new FilesystemPromptToolProviders.ListDirMetadataProvider().getInputParams(language),
                language -> new FilesystemPromptToolProviders.GrepMetadataProvider().getInputParams(language),
                language -> new ListSkillMetadataProvider().getInputParams(language),
                language -> new TodoPromptToolProviders.TodoCreateMetadataProvider().getInputParams(language),
                language -> new TodoPromptToolProviders.TodoListMetadataProvider().getInputParams(language),
                language -> new TodoPromptToolProviders.TodoModifyMetadataProvider().getInputParams(language),
                language -> new ImageOCRMetadataProvider().getInputParams(language),
                language -> new VisualQuestionAnsweringMetadataProvider().getInputParams(language),
                language -> new AudioPromptToolProviders.AudioTranscriptionMetadataProvider().getInputParams(language),
                language -> new AudioPromptToolProviders.AudioQuestionAnsweringMetadataProvider()
                        .getInputParams(language),
                language -> new AudioPromptToolProviders.AudioMetadataMetadataProvider().getInputParams(language)
        );
    }

    private static List<String> registeredToolNames() {
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
