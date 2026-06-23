/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import com.openjiuwen.core.foundation.tool.ToolCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's tool description tests in
 * {@code tests/unit_tests/harness/prompts/test_tool_descriptions.py}.</p>
 */
class ToolDescriptionsPythonParityTest {

    @Test
    void coreDescriptions() {
        for (ToolMetadataProvider provider : coreProviders()) {
            assertTrue(provider.getDescription("cn").strip().length() > 0);
            assertTrue(provider.getDescription("en").strip().length() > 0);
        }
    }

    @Test
    void knownToolCn() {
        assertEquals(new BashMetadataProvider().getDescription("cn"),
                HarnessPromptToolsPackage.getToolDescription("bash", "cn"));
        assertEquals(new PowerShellMetadataProvider().getDescription("cn"),
                HarnessPromptToolsPackage.getToolDescription("powershell", "cn"));
    }

    @Test
    void knownToolEn() {
        assertEquals(new BashMetadataProvider().getDescription("en"),
                HarnessPromptToolsPackage.getToolDescription("bash", "en"));
        assertEquals(new PowerShellMetadataProvider().getDescription("en"),
                HarnessPromptToolsPackage.getToolDescription("powershell", "en"));
    }

    @Test
    void unknownToolRaises() {
        assertThrows(
                NoSuchElementException.class,
                () -> HarnessPromptToolsPackage.getToolDescription("nonexistent", "cn")
        );
    }

    @Test
    void allRegisteredTools() {
        for (String name : registeredToolNames()) {
            assertTrue(HarnessPromptToolsPackage.getToolDescription(name, "cn").strip().length() > 0);
            assertTrue(HarnessPromptToolsPackage.getToolDescription(name, "en").strip().length() > 0);
        }
    }

    @Test
    void existingToolsEn() {
        assertCardDescription("bash", new BashMetadataProvider().getDescription("en"));
        assertCardDescription("powershell", new PowerShellMetadataProvider().getDescription("en"));
        assertCardDescription("code", new CodePromptToolProviders.CodeMetadataProvider().getDescription("en"));
        assertCardDescription("read_file", new FilesystemPromptToolProviders.ReadFileMetadataProvider().getDescription("en"));
        assertCardDescription("edit_file", new FilesystemPromptToolProviders.EditFileMetadataProvider().getDescription("en"));
    }

    @Test
    void visionToolsEn() {
        assertCardDescription("image_ocr", new ImageOCRMetadataProvider().getDescription("en"));
        assertCardDescription("visual_question_answering",
                new VisualQuestionAnsweringMetadataProvider().getDescription("en"));
    }

    @Test
    void audioToolsEn() {
        assertCardDescription("audio_transcription",
                new AudioPromptToolProviders.AudioTranscriptionMetadataProvider().getDescription("en"));
        assertCardDescription("audio_question_answering",
                new AudioPromptToolProviders.AudioQuestionAnsweringMetadataProvider().getDescription("en"));
        assertCardDescription("audio_metadata",
                new AudioPromptToolProviders.AudioMetadataMetadataProvider().getDescription("en"));
    }

    private static void assertCardDescription(String toolName, String expectedDescription) {
        ToolCard card = HarnessPromptToolsPackage.buildToolCard(toolName, toolName, "en", "test-agent");

        assertEquals(expectedDescription, card.getDescription());
    }

    private static List<ToolMetadataProvider> coreProviders() {
        return List.of(
                new BashMetadataProvider(),
                new PowerShellMetadataProvider(),
                new CodePromptToolProviders.CodeMetadataProvider(),
                new ListSkillMetadataProvider(),
                new FilesystemPromptToolProviders.ReadFileMetadataProvider(),
                new FilesystemPromptToolProviders.WriteFileMetadataProvider(),
                new FilesystemPromptToolProviders.EditFileMetadataProvider(),
                new FilesystemPromptToolProviders.GlobMetadataProvider(),
                new FilesystemPromptToolProviders.ListDirMetadataProvider(),
                new FilesystemPromptToolProviders.GrepMetadataProvider(),
                new TodoPromptToolProviders.TodoCreateMetadataProvider(),
                new TodoPromptToolProviders.TodoListMetadataProvider(),
                new TodoPromptToolProviders.TodoModifyMetadataProvider(),
                new ImageOCRMetadataProvider(),
                new VisualQuestionAnsweringMetadataProvider(),
                new AudioPromptToolProviders.AudioTranscriptionMetadataProvider(),
                new AudioPromptToolProviders.AudioQuestionAnsweringMetadataProvider(),
                new AudioPromptToolProviders.AudioMetadataMetadataProvider()
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
