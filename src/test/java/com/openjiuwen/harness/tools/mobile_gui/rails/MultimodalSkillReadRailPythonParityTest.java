/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.sys_operation.Cwd;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/tools/mobile_gui/test_multimodal_skill_read_rail.py}.
 */
class MultimodalSkillReadRailPythonParityTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearCwd() {
        Cwd.clear();
    }

    @Test
    void parseMarkdownToBlocksLabelsSkillReferenceImages() throws Exception {
        Path imageDir = tempDir.resolve("images");
        Files.createDirectories(imageDir);
        Files.write(imageDir.resolve("calendar_search.png"), "fake-image-bytes".getBytes());

        List<Map<String, Object>> blocks = MultimodalSkillReadRail.parseMarkdownToBlocks(
                "Open Calendar.\n\n![Calendar Search](images/calendar_search.png)\n\nThen tap the event.",
                tempDir
        );

        assertEquals(4, blocks.size());
        assertEquals(Map.of("type", "text", "text", "Open Calendar."), blocks.get(0));
        assertEquals("text", blocks.get(1).get("type"));
        assertTrue(String.valueOf(blocks.get(1).get("text")).contains("[Skill reference image: Calendar Search]"));
        assertEquals("image_url", blocks.get(2).get("type"));
        assertTrue(imageUrl(blocks.get(2)).startsWith("data:image/png;base64,"));
        assertEquals("low", imageDetail(blocks.get(2)));
        assertEquals(Map.of("type", "text", "text", "Then tap the event."), blocks.get(3));
    }

    @Test
    void parseMarkdownToBlocksUsesFilenameAsMissingAltCaption() {
        List<Map<String, Object>> blocks = MultimodalSkillReadRail.parseMarkdownToBlocks("![](images/alarm_time.png)");

        assertEquals(2, blocks.size());
        assertEquals("text", blocks.get(0).get("type"));
        assertTrue(String.valueOf(blocks.get(0).get("text")).contains("[Skill reference image: alarm_time.png]"));
        assertEquals("image_url", blocks.get(1).get("type"));
        assertEquals("images/alarm_time.png", imageUrl(blocks.get(1)));
        assertEquals("low", imageDetail(blocks.get(1)));
    }

    @Test
    void expandMessagesPrependsShortHintForSkillToolWithImages() {
        MultimodalSkillReadRail rail = new MultimodalSkillReadRail(tempDir.toString());
        AssistantMessage assistant = assistant(toolCall(
                "call-1",
                "skill_tool",
                "{\"skill_name\":\"scheduling\",\"relative_file_path\":\"SKILL.md\"}"
        ));
        ToolMessage toolMessage = new ToolMessage(
                "data={'skill_content': 'Step one.\\n\\n![Alarm Time](images/alarm_time.png)'}",
                "call-1",
                "skill_tool"
        );

        List<Object> expanded = rail.expandMessages(List.of(assistant, toolMessage));

        assertEquals(2, expanded.size());
        assertSame(assistant, expanded.get(0));
        ToolMessage toolOut = assertInstanceOf(ToolMessage.class, expanded.get(1));
        assertEquals("tool", toolOut.getRole());
        assertEquals("call-1", toolOut.getToolCallId());
        String content = toolOut.getContentAsString();
        assertTrue(content.startsWith(MultimodalSkillReadRail.SKILL_TOOL_MARKDOWN_IMAGES_HINT));
        assertTrue(content.contains("Step one."));
        assertTrue(content.contains("![Alarm Time]"));
        assertFalse(content.contains("[Skill reference image:"));
        assertFalse(content.contains("data:image"));
    }

    @Test
    void expandMessagesSkillToolHintIsIdempotent() {
        MultimodalSkillReadRail rail = new MultimodalSkillReadRail(tempDir.toString());
        List<Object> messages = List.of(
                assistant(toolCall("call-1", "skill_tool", "{\"skill_name\":\"scheduling\"}")),
                new ToolMessage("data={'skill_content': '![x](y.png)'}", "call-1", "skill_tool")
        );

        List<Object> once = rail.expandMessages(messages);
        List<Object> twice = rail.expandMessages(once);

        String onceContent = ((ToolMessage) once.get(1)).getContentAsString();
        String twiceContent = ((ToolMessage) twice.get(1)).getContentAsString();
        assertEquals(onceContent, twiceContent);
        assertEquals(1, count(twiceContent, MultimodalSkillReadRail.SKILL_TOOL_MARKDOWN_IMAGES_HINT));
    }

    @Test
    void multimodalReadRailInlineModeActiveByDefault() {
        MultimodalSkillReadRail rail = new MultimodalSkillReadRail(tempDir.toString());

        assertTrue(rail.inlineModeActive());
    }

    @Test
    void multimodalReadRailNoopsInBranchMode() {
        MultimodalSkillReadRail rail = new MultimodalSkillReadRail(tempDir.toString(), "branch");

        assertFalse(rail.inlineModeActive());
    }

    @Test
    void buildSkillBundleImageLeadTextUsesExplicitCaption() {
        String body = MultimodalSkillReadRail.buildSkillBundleImageLeadText(
                "/workspace/skills/foo/images/bar.png",
                "Calendar search"
        );

        assertEquals(MultimodalSkillReadRail.REFERENCE_IMAGE_NOTE.formatted("Calendar search"), body);
        assertTrue(body.contains("not the current device screen"));
    }

    @Test
    void buildSkillBundleImageLeadTextFallsBackToPathStem() {
        String body = MultimodalSkillReadRail.buildSkillBundleImageLeadText("/workspace/skills/foo/refs/q.png");

        assertEquals(MultimodalSkillReadRail.REFERENCE_IMAGE_NOTE.formatted("q"), body);
        assertTrue(body.contains("[Skill reference image: q]"));
    }

    @Test
    void isPathUnderWorkspaceSkillsAcceptsBundleFiles() throws Exception {
        Path workspace = tempDir.resolve("wk");
        Path png = workspace.resolve("skills/myskill/pic.png");
        Files.createDirectories(png.getParent());
        Files.writeString(png, "x");
        Cwd.initCwd(workspace.toString());

        assertTrue(MultimodalSkillReadRail.isPathUnderWorkspaceSkills(png.toAbsolutePath().normalize().toString()));
    }

    @Test
    void isPathUnderWorkspaceSkillsRejectsOutsideBundle() throws Exception {
        Path workspace = tempDir.resolve("wk");
        Path png = workspace.resolve("docs/a.png");
        Files.createDirectories(png.getParent());
        Files.writeString(png, "x");
        Cwd.initCwd(workspace.toString());

        assertFalse(MultimodalSkillReadRail.isPathUnderWorkspaceSkills(png.toAbsolutePath().normalize().toString()));
    }

    @Test
    void decorateReadFileRewritesSkillsUserMessageAndName() throws Exception {
        Path workspace = tempDir.resolve("wk");
        Path png = workspace.resolve("skills/s1/pic.png");
        Files.createDirectories(png.getParent());
        Files.writeString(png, "x");
        Cwd.initCwd(workspace.toString());
        UserMessage userImage = imageUserMessage(png, "AA");
        List<Object> messages = new ArrayList<>(List.of(
                assistant(toolCall("t1", "read_file", "{\"file_path\":\"skills/s1/pic.png\",\"caption\":\"From SKILL alt\"}")),
                new ToolMessage("ok", "t1", "read_file"),
                userImage
        ));

        MultimodalSkillReadRail.decorateReadFileSkillBundleUserMessages(messages);

        assertEquals(MultimodalSkillReadRail.MULTIMODAL_SKILL_USER_MESSAGE_NAME, userImage.getName());
        String lead = text(userImage, 0);
        assertTrue(lead.contains("[Skill reference image: From SKILL alt]"));
        assertTrue(lead.toLowerCase().contains("example screenshot from the skill documentation"));
        assertEquals("image_url", block(userImage, 1).get("type"));
    }

    @Test
    void decorateReadFileNoopForNonSkillsPath() throws Exception {
        Path workspace = tempDir.resolve("wk");
        Path png = workspace.resolve("docs/a.png");
        Files.createDirectories(png.getParent());
        Files.writeString(png, "x");
        Cwd.initCwd(workspace.toString());
        UserMessage userImage = imageUserMessage(png, "AA");

        MultimodalSkillReadRail.decorateReadFileSkillBundleUserMessages(List.of(userImage));

        assertEquals(null, userImage.getName());
        assertTrue(text(userImage, 0).contains("Image loaded from read_file"));
    }

    @Test
    void mergeConsecutiveReadFileSkillUserMessages() throws Exception {
        Path workspace = tempDir.resolve("wk");
        Path skillDir = workspace.resolve("skills/s1");
        Files.createDirectories(skillDir);
        Cwd.initCwd(workspace.toString());
        UserMessage first = imageUserMessage(skillDir.resolve("a.png"), "0");
        UserMessage second = imageUserMessage(skillDir.resolve("b.png"), "1");
        UserMessage third = imageUserMessage(skillDir.resolve("c.png"), "2");
        Files.writeString(skillDir.resolve("a.png"), "x");
        Files.writeString(skillDir.resolve("b.png"), "x");
        Files.writeString(skillDir.resolve("c.png"), "x");
        List<Object> messages = new ArrayList<>(List.of(
                assistant(
                        toolCall("t0", "read_file", "{\"file_path\":\"skills/s1/a.png\",\"caption\":\"Cap a.png\"}"),
                        toolCall("t1", "read_file", "{\"file_path\":\"skills/s1/b.png\",\"caption\":\"Cap b.png\"}"),
                        toolCall("t2", "read_file", "{\"file_path\":\"skills/s1/c.png\",\"caption\":\"Cap c.png\"}")
                ),
                new ToolMessage("ok", "t0", "read_file"),
                new ToolMessage("ok", "t1", "read_file"),
                new ToolMessage("ok", "t2", "read_file"),
                first,
                second,
                third
        ));
        MultimodalSkillReadRail.decorateReadFileSkillBundleUserMessages(messages);

        List<Object> merged = MultimodalSkillReadRail.mergeConsecutiveReadFileSkillUserMessages(messages);

        assertEquals(5, merged.size());
        assertInstanceOf(AssistantMessage.class, merged.get(0));
        assertInstanceOf(ToolMessage.class, merged.get(1));
        UserMessage combined = assertInstanceOf(UserMessage.class, merged.get(4));
        assertEquals(MultimodalSkillReadRail.MULTIMODAL_SKILL_USER_MESSAGE_NAME, combined.getName());
        List<String> textBlocks = content(combined).stream()
                .filter(block -> "text".equals(block.get("type")))
                .map(block -> String.valueOf(block.get("text")))
                .toList();
        List<String> imageUrls = content(combined).stream()
                .filter(block -> "image_url".equals(block.get("type")))
                .map(MultimodalSkillReadRailPythonParityTest::imageUrl)
                .toList();
        assertEquals(3, textBlocks.size());
        assertEquals(3, imageUrls.size());
        assertTrue(textBlocks.stream().anyMatch(text -> text.contains("Cap a.png")));
        assertTrue(textBlocks.stream().anyMatch(text -> text.contains("reference image") && text.contains("c")));
        assertEquals(List.of("data:image/png;base64,0", "data:image/png;base64,1", "data:image/png;base64,2"), imageUrls);
    }

    @Test
    void mergeConsecutiveReadFileLeavesNonSkillUserMessagesSeparate() throws Exception {
        Path workspace = tempDir.resolve("wk");
        Path skillPng = workspace.resolve("skills/s1/a.png");
        Path otherPng = workspace.resolve("docs/b.png");
        Files.createDirectories(skillPng.getParent());
        Files.createDirectories(otherPng.getParent());
        Files.writeString(skillPng, "x");
        Files.writeString(otherPng, "x");
        Cwd.initCwd(workspace.toString());
        UserMessage skillUser = imageUserMessage(skillPng, "AA");
        UserMessage otherUser = imageUserMessage(otherPng, "BB");
        List<Object> messages = new ArrayList<>(List.of(skillUser, otherUser));

        MultimodalSkillReadRail.decorateReadFileSkillBundleUserMessages(messages);
        List<Object> merged = MultimodalSkillReadRail.mergeConsecutiveReadFileSkillUserMessages(messages);

        assertEquals(2, merged.size());
        assertEquals(MultimodalSkillReadRail.MULTIMODAL_SKILL_USER_MESSAGE_NAME, ((UserMessage) merged.get(0)).getName());
        assertEquals(null, ((UserMessage) merged.get(1)).getName());
        assertEquals("data:image/png;base64,BB", imageUrl(block((UserMessage) merged.get(1), 1)));
    }

    @Test
    void isPathUnderWorkspaceSkillsWithPatchedGetCwdEquivalent() throws Exception {
        Path skillsDir = tempDir.resolve("skills/k");
        Files.createDirectories(skillsDir);
        Path png = skillsDir.resolve("x.png");
        Files.writeString(png, "x");
        Cwd.initCwd(tempDir.toString());

        assertTrue(MultimodalSkillReadRail.isPathUnderWorkspaceSkills(png.toAbsolutePath().normalize().toString()));
    }

    @Test
    void parseMarkdownToBlocksPlainTextOnly() {
        List<Map<String, Object>> blocks = MultimodalSkillReadRail.parseMarkdownToBlocks("Just text, no images.");

        assertEquals(List.of(Map.of("type", "text", "text", "Just text, no images.")), blocks);
    }

    @Test
    void parseMarkdownToBlocksRemoteUrlWithoutBase64() {
        List<Map<String, Object>> blocks = MultimodalSkillReadRail.parseMarkdownToBlocks("See ![logo](https://example.com/logo.png)");

        assertEquals(3, blocks.size());
        assertEquals(Map.of("type", "text", "text", "See"), blocks.get(0));
        assertTrue(String.valueOf(blocks.get(1).get("text")).contains("[Skill reference image: logo]"));
        assertEquals("image_url", blocks.get(2).get("type"));
        assertEquals("https://example.com/logo.png", imageUrl(blocks.get(2)));
        assertEquals("low", imageDetail(blocks.get(2)));
    }

    @Test
    void expandMessagesLeavesSkillToolWithoutMarkdownUnchanged() {
        MultimodalSkillReadRail rail = new MultimodalSkillReadRail(tempDir.toString());
        ToolMessage original = new ToolMessage("data={'skill_content': 'No images here.'}", "c1", "skill_tool");

        List<Object> expanded = rail.expandMessages(List.of(
                assistant(toolCall("c1", "skill_tool", "{}")),
                original
        ));

        assertEquals(original.getContent(), ((ToolMessage) expanded.get(1)).getContent());
        assertFalse(((ToolMessage) expanded.get(1)).getContentAsString()
                .contains(MultimodalSkillReadRail.SKILL_TOOL_MARKDOWN_IMAGES_HINT));
    }

    @Test
    void expandMessagesLeavesNonSkillToolUnchanged() {
        MultimodalSkillReadRail rail = new MultimodalSkillReadRail(tempDir.toString());
        ToolMessage toolMessage = new ToolMessage("done", "c2", "wait");

        List<Object> expanded = rail.expandMessages(List.of(
                assistant(toolCall("c2", "wait", "{}")),
                toolMessage
        ));

        assertEquals("done", ((ToolMessage) expanded.get(1)).getContent());
    }

    @Test
    void decorateReadFileUsesReferenceCaptionFieldAlias() throws Exception {
        Path workspace = tempDir.resolve("wk");
        Path png = workspace.resolve("skills/s1/pic.png");
        Files.createDirectories(png.getParent());
        Files.writeString(png, "x");
        Cwd.initCwd(workspace.toString());
        UserMessage userImage = imageUserMessage(png, "AA");

        MultimodalSkillReadRail.decorateReadFileSkillBundleUserMessages(new ArrayList<>(List.of(
                assistant(toolCall("t1", "read_file", "{\"file_path\":\"skills/s1/pic.png\",\"reference_caption\":\"Alias caption\"}")),
                new ToolMessage("ok", "t1", "read_file"),
                userImage
        )));

        assertTrue(text(userImage, 0).contains("[Skill reference image: Alias caption]"));
    }

    @Test
    void mergeSingleSkillReadFileUserIsUnchanged() throws Exception {
        Path workspace = tempDir.resolve("wk");
        Path png = workspace.resolve("skills/s1/only.png");
        Files.createDirectories(png.getParent());
        Files.writeString(png, "x");
        Cwd.initCwd(workspace.toString());
        UserMessage userImage = imageUserMessage(png, "AA");
        MultimodalSkillReadRail.decorateReadFileSkillBundleUserMessages(List.of(userImage));

        List<Object> merged = MultimodalSkillReadRail.mergeConsecutiveReadFileSkillUserMessages(List.of(userImage));

        assertEquals(1, merged.size());
        assertSame(userImage, merged.get(0));
    }

    @Test
    void applySkillToolMarkdownImagesHintIdempotent() {
        String once = MultimodalSkillReadRail.applySkillToolMarkdownImagesHint("skill text");
        String twice = MultimodalSkillReadRail.applySkillToolMarkdownImagesHint(once);

        assertEquals(once, twice);
        assertEquals(1, count(twice, MultimodalSkillReadRail.SKILL_TOOL_MARKDOWN_IMAGES_HINT));
    }

    private static AssistantMessage assistant(ToolCall... toolCalls) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCalls))
                .build();
    }

    private static ToolCall toolCall(String id, String name, String arguments) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .name(name)
                .arguments(arguments)
                .build();
    }

    private static UserMessage imageUserMessage(Path resolvedPath, String imageB64) {
        return UserMessage.builder()
                .content(new ArrayList<>(List.of(
                        Map.of("type", "text", "text", "Image loaded from read_file: " + resolvedPath.toAbsolutePath().normalize()),
                        Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64," + imageB64, "detail", "low"))
                )))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> content(UserMessage message) {
        return (List<Map<String, Object>>) (List<?>) message.getContentAsList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(UserMessage message, int index) {
        return (Map<String, Object>) message.getContentAsList().get(index);
    }

    private static String text(UserMessage message, int index) {
        return String.valueOf(block(message, index).get("text"));
    }

    @SuppressWarnings("unchecked")
    private static String imageUrl(Map<String, Object> block) {
        return String.valueOf(((Map<String, Object>) block.get("image_url")).get("url"));
    }

    @SuppressWarnings("unchecked")
    private static String imageDetail(Map<String, Object> block) {
        return String.valueOf(((Map<String, Object>) block.get("image_url")).get("detail"));
    }

    private static int count(String value, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = value.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
