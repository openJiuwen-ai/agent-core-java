/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decorates skill markdown image reads with multimodal image blocks.
 *
 * <p>Mirrors Python's markdown image helpers and
 * {@code MultimodalSkillReadRail} in
 * {@code openjiuwen/harness/tools/mobile_gui/rails/multimodal_skill_read_rail.py}.</p>
 */
public class MultimodalSkillReadRail extends DeepAgentRail {

    public static final String MULTIMODAL_SKILL_USER_MESSAGE_NAME = "multimodal_skill";
    public static final String SKILL_TOOL_MARKDOWN_IMAGES_HINT =
            "When skill markdown references local images, inspect the loaded reference images.";

    private static final Pattern IMAGE_MARKDOWN = Pattern.compile("!\\[[^]]*]\\(([^)]+)\\)");

    private final int maxImages;

    public MultimodalSkillReadRail(int maxImages) {
        this.maxImages = Math.max(0, maxImages);
    }

    public static String applySkillToolMarkdownImagesHint(String body) {
        String safeBody = body == null ? "" : body;
        return safeBody.contains(SKILL_TOOL_MARKDOWN_IMAGES_HINT)
                ? safeBody
                : safeBody + "\n\n" + SKILL_TOOL_MARKDOWN_IMAGES_HINT;
    }

    public static boolean isPathUnderWorkspaceSkills(String absoluteFilePath, String workspaceRoot) {
        if (absoluteFilePath == null || workspaceRoot == null) {
            return false;
        }
        Path skillsRoot = Path.of(workspaceRoot).resolve(".skills").toAbsolutePath().normalize();
        return Path.of(absoluteFilePath).toAbsolutePath().normalize().startsWith(skillsRoot);
    }

    public static String getMimeType(Path filePath) {
        String name = filePath == null ? "" : filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    public int countMarkdownImages(String body) {
        Matcher matcher = IMAGE_MARKDOWN.matcher(body == null ? "" : body);
        int count = 0;
        while (matcher.find() && count < maxImages) {
            count++;
        }
        return count;
    }

    @Override
    public void afterToolCall(CallbackContext ctx) {
        if (ctx != null && ctx.get("tool_result") instanceof String text && countMarkdownImages(text) > 0) {
            ctx.put("tool_result", applySkillToolMarkdownImagesHint(text));
        }
    }

    public static boolean fileExists(Path path) {
        return path != null && Files.isRegularFile(path);
    }
}
