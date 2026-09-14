/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.sysop.Cwd;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final String MULTIMODAL_SKILL_USER_MESSAGE_NAME = "multimodal_skill";
    public static final String SKILL_TOOL_MARKDOWN_IMAGES_HINT =
            "Embedded figures in this skill are markdown links (paths/URLs) only; pixel data is not "
                    + "attached. Call read_file on the image path under skills/<skill-name>/... when you need "
                    + "to inspect a reference screenshot.";
    public static final String REFERENCE_IMAGE_NOTE =
            "[Skill reference image: %s]\n"
                    + "This is an example screenshot from the skill documentation, not the "
                    + "current device screen. Do not infer current coordinates, current app "
                    + "state, or visible text from it.";

    private static final Pattern IMAGE_MARKDOWN = Pattern.compile("!\\[([^]]*)]\\((.*?)\\)");
    private static final Pattern IMAGE_LOADED_FROM_READ_FILE = Pattern.compile(
            "^Image loaded from read_file:\\s*(.+?)\\s*\\z",
            Pattern.DOTALL
    );
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final int maxImages;
    private final Path skillRoot;
    private final String skillConsultMode;

    public MultimodalSkillReadRail(int maxImages) {
        this.maxImages = Math.max(0, maxImages);
        this.skillRoot = null;
        this.skillConsultMode = "inline";
    }

    public MultimodalSkillReadRail(String skillRoot) {
        this(skillRoot, "inline");
    }

    public MultimodalSkillReadRail(String skillRoot, String skillConsultMode) {
        this.maxImages = Integer.MAX_VALUE;
        this.skillRoot = skillRoot == null ? null : Path.of(skillRoot);
        this.skillConsultMode = skillConsultMode == null ? "inline" : skillConsultMode;
    }

    public static String applySkillToolMarkdownImagesHint(String body) {
        String safeBody = body == null ? "" : body;
        while (safeBody.startsWith(SKILL_TOOL_MARKDOWN_IMAGES_HINT + "\n\n")) {
            safeBody = safeBody.substring((SKILL_TOOL_MARKDOWN_IMAGES_HINT + "\n\n").length());
        }
        return SKILL_TOOL_MARKDOWN_IMAGES_HINT + "\n\n" + safeBody;
    }

    public static boolean isPathUnderWorkspaceSkills(String absoluteFilePath) {
        return isPathUnderWorkspaceSkills(absoluteFilePath, Cwd.getCwd());
    }

    public static boolean isPathUnderWorkspaceSkills(String absoluteFilePath, String workspaceRoot) {
        if (absoluteFilePath == null || workspaceRoot == null) {
            return false;
        }
        Path skillsRoot = Path.of(workspaceRoot).resolve("skills").toAbsolutePath().normalize();
        return Path.of(absoluteFilePath).toAbsolutePath().normalize().startsWith(skillsRoot);
    }

    public static String buildSkillBundleImageLeadText(String sourcePath) {
        return buildSkillBundleImageLeadText(sourcePath, null);
    }

    public static String buildSkillBundleImageLeadText(String sourcePath, String referenceCaption) {
        String stripped = referenceCaption == null ? "" : referenceCaption.strip();
        String caption = stripped.isEmpty() ? stemOrDefault(sourcePath) : stripped;
        return REFERENCE_IMAGE_NOTE.formatted(caption);
    }

    public static String getMimeType(Path filePath) {
        String name = filePath == null ? "" : filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    public static List<Map<String, Object>> parseMarkdownToBlocks(String markdownText) {
        return parseMarkdownToBlocks(markdownText, null);
    }

    public static List<Map<String, Object>> parseMarkdownToBlocks(String markdownText, Path baseDir) {
        String text = markdownText == null ? "" : markdownText;
        List<Map<String, Object>> blocks = new ArrayList<>();
        Matcher matcher = IMAGE_MARKDOWN.matcher(text);
        int lastIdx = 0;
        while (matcher.find()) {
            String textPart = text.substring(lastIdx, matcher.start()).strip();
            if (!textPart.isEmpty()) {
                blocks.add(textBlock(textPart));
            }

            String altText = matcher.group(1).strip();
            String imageUrl = matcher.group(2).strip();
            String caption = altText.isEmpty() ? fileNameOrDefault(imageUrl) : altText;
            String resolvedUrl = resolveMarkdownImageUrl(imageUrl, baseDir);
            blocks.add(textBlock(REFERENCE_IMAGE_NOTE.formatted(caption)));
            blocks.add(imageBlock(resolvedUrl));
            lastIdx = matcher.end();
        }

        String textPart = text.substring(lastIdx).strip();
        if (!textPart.isEmpty()) {
            blocks.add(textBlock(textPart));
        }
        return blocks;
    }

    public boolean inlineModeActive() {
        return !"branch".equals(skillConsultMode);
    }

    public List<Object> expandMessages(List<?> messages) {
        Map<String, String> toolSources = collectToolSources(messages);
        List<Object> expanded = new ArrayList<>();
        for (Object message : messages) {
            if (message instanceof ToolMessage toolMessage && toolMessage.getContent() instanceof String body) {
                String toolCallId = toolMessage.getToolCallId() == null ? "" : toolMessage.getToolCallId();
                String source = toolSources.get(toolCallId);
                String markdown = null;
                if ("read_file".equals(source) && containsMarkdownImage(body)) {
                    markdown = body;
                } else if ("skill_tool".equals(source)) {
                    String rawSkill = extractSkillContentFromToolMessageString(body);
                    if (rawSkill != null && containsMarkdownImage(rawSkill)) {
                        markdown = rawSkill;
                    }
                } else if (source == null && containsMarkdownImage(body)) {
                    markdown = body;
                }

                if (markdown != null && "skill_tool".equals(source)) {
                    expanded.add(new ToolMessage(
                            applySkillToolMarkdownImagesHint(body),
                            toolMessage.getToolCallId(),
                            toolMessage.getName()
                    ));
                    continue;
                }
            }
            expanded.add(message);
        }
        return expanded;
    }

    public List<Object> transformMessages(List<?> messages) {
        List<Object> staged = expandMessages(messages);
        decorateReadFileSkillBundleUserMessages(staged);
        return mergeConsecutiveReadFileSkillUserMessages(staged);
    }

    public static void decorateReadFileSkillBundleUserMessages(List<?> messages) {
        for (int idx = 0; idx < messages.size(); idx++) {
            Object raw = messages.get(idx);
            if (!(raw instanceof UserMessage userMessage)) {
                continue;
            }
            if (MULTIMODAL_SKILL_USER_MESSAGE_NAME.equals(userMessage.getName())) {
                continue;
            }
            List<Object> blocks = userMessage.getContentAsList();
            if (blocks == null || blocks.size() < 2 || !(blocks.get(0) instanceof Map<?, ?> firstBlock)) {
                continue;
            }
            if (!"text".equals(String.valueOf(firstBlock.get("type")))) {
                continue;
            }
            Object textValue = firstBlock.get("text");
            if (!(textValue instanceof String textBody)) {
                continue;
            }
            Matcher matcher = IMAGE_LOADED_FROM_READ_FILE.matcher(textBody.strip());
            if (!matcher.matches()) {
                continue;
            }
            Path resolved = Path.of(matcher.group(1).strip()).toAbsolutePath().normalize();
            if (!isPathUnderWorkspaceSkills(resolved.toString())) {
                continue;
            }

            AssistantMessage assistant = nearestAssistantBeforeIdx(messages, idx);
            String caption = assistant == null ? null : matchingReadFileCaption(assistant, resolved);
            userMessage.setName(MULTIMODAL_SKILL_USER_MESSAGE_NAME);
            blocks.set(0, textBlock(buildSkillBundleImageLeadText(resolved.toString(), caption)));
        }
    }

    public static List<Object> mergeConsecutiveReadFileSkillUserMessages(List<?> messages) {
        List<Object> merged = new ArrayList<>();
        int idx = 0;
        while (idx < messages.size()) {
            Object message = messages.get(idx);
            if (!isSkillReadFileImageUserMessage(message)) {
                merged.add(message);
                idx++;
                continue;
            }

            List<UserMessage> run = new ArrayList<>();
            run.add((UserMessage) message);
            int nextIdx = idx + 1;
            while (nextIdx < messages.size() && isSkillReadFileImageUserMessage(messages.get(nextIdx))) {
                run.add((UserMessage) messages.get(nextIdx));
                nextIdx++;
            }
            merged.add(run.size() == 1 ? run.get(0) : mergeSkillReadFileUserMessages(run));
            idx = nextIdx;
        }
        return merged;
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

    public Path getSkillRoot() {
        return skillRoot;
    }

    private static boolean containsMarkdownImage(String body) {
        return body != null && body.contains("![") && body.contains("](");
    }

    private static Map<String, String> collectToolSources(List<?> messages) {
        Map<String, String> toolSources = new LinkedHashMap<>();
        for (Object message : messages) {
            if (!(message instanceof AssistantMessage assistant) || assistant.getToolCalls() == null) {
                continue;
            }
            for (ToolCall toolCall : assistant.getToolCalls()) {
                if (toolCall.getId() == null) {
                    continue;
                }
                if ("read_file".equals(toolCall.getName())) {
                    toolSources.put(toolCall.getId(), "read_file");
                } else if ("skill_tool".equals(toolCall.getName())) {
                    toolSources.put(toolCall.getId(), "skill_tool");
                }
            }
        }
        return toolSources;
    }

    private static String resolveMarkdownImageUrl(String imageUrl, Path baseDir) {
        if (baseDir == null || startsWithAny(imageUrl, "http://", "https://", "data:")) {
            return imageUrl;
        }
        try {
            String decodedPath = URLDecoder.decode(imageUrl, StandardCharsets.UTF_8);
            Path imagePath = baseDir.resolve(decodedPath);
            if (Files.isRegularFile(imagePath)) {
                String encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
                return "data:" + getMimeType(imagePath) + ";base64," + encoded;
            }
        } catch (IOException | IllegalArgumentException ignored) {
            return imageUrl;
        }
        return imageUrl;
    }

    private static boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> textBlock(String text) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "text");
        block.put("text", text);
        return block;
    }

    private static Map<String, Object> imageBlock(String imageUrl) {
        Map<String, Object> imageUrlMap = new LinkedHashMap<>();
        imageUrlMap.put("url", imageUrl);
        imageUrlMap.put("detail", "low");
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "image_url");
        block.put("image_url", imageUrlMap);
        return block;
    }

    private static String fileNameOrDefault(String imageUrl) {
        String decoded = URLDecoder.decode(imageUrl == null ? "" : imageUrl, StandardCharsets.UTF_8);
        String normalized = decoded.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return fileName.isBlank() ? "reference image" : fileName;
    }

    private static String stemOrDefault(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return "reference image";
        }
        String fileName = Path.of(sourcePath).getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        return stem.isBlank() ? "reference image" : stem;
    }

    private static String extractSkillContentFromToolMessageString(String toolBody) {
        Matcher marker = Pattern.compile("[\"']skill_content[\"']\\s*:\\s*").matcher(toolBody);
        if (!marker.find() || marker.end() >= toolBody.length()) {
            return null;
        }
        char quote = toolBody.charAt(marker.end());
        if (quote != '\'' && quote != '"') {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        int idx = marker.end() + 1;
        while (idx < toolBody.length()) {
            char ch = toolBody.charAt(idx);
            if (ch == '\\') {
                idx++;
                if (idx >= toolBody.length()) {
                    return null;
                }
                char next = toolBody.charAt(idx);
                builder.append(switch (next) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> next;
                });
                idx++;
                continue;
            }
            if (ch == quote) {
                return builder.toString();
            }
            builder.append(ch);
            idx++;
        }
        return null;
    }

    private static AssistantMessage nearestAssistantBeforeIdx(List<?> messages, int idx) {
        int cursor = idx - 1;
        while (cursor >= 0 && messages.get(cursor) instanceof ToolMessage) {
            cursor--;
        }
        return cursor >= 0 && messages.get(cursor) instanceof AssistantMessage assistant ? assistant : null;
    }

    private static String matchingReadFileCaption(AssistantMessage assistant, Path resolvedTarget) {
        if (assistant.getToolCalls() == null) {
            return null;
        }
        for (ToolCall toolCall : assistant.getToolCalls()) {
            if (!"read_file".equals(toolCall.getName())) {
                continue;
            }
            Map<String, Object> args = parseToolCallArguments(toolCall.getArguments());
            Object rawPath = args.getOrDefault("path", args.get("file_path"));
            if (rawPath == null) {
                continue;
            }
            Path candidate = resolveReadFileToolPathLikeFilesystem(String.valueOf(rawPath));
            if (!Objects.equals(candidate, resolvedTarget)) {
                continue;
            }
            Object caption = args.get("caption");
            if (caption == null) {
                caption = args.get("reference_caption");
            }
            if (caption instanceof String value && !value.strip().isEmpty()) {
                return value.strip();
            }
        }
        return null;
    }

    private static Map<String, Object> parseToolCallArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(arguments, MAP_TYPE);
        } catch (IOException ignored) {
            return Map.of();
        }
    }

    private static Path resolveReadFileToolPathLikeFilesystem(String raw) {
        String expanded = raw.replaceFirst("^~", System.getProperty("user.home"));
        Path path = Path.of(expanded);
        if (!path.isAbsolute()) {
            path = Path.of(Cwd.getCwd()).resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private static boolean isSkillReadFileImageUserMessage(Object message) {
        if (!(message instanceof UserMessage userMessage)) {
            return false;
        }
        if (!MULTIMODAL_SKILL_USER_MESSAGE_NAME.equals(userMessage.getName())) {
            return false;
        }
        List<Object> blocks = userMessage.getContentAsList();
        if (blocks == null) {
            return false;
        }
        return blocks.stream().anyMatch(block -> block instanceof Map<?, ?> map && "image_url".equals(map.get("type")));
    }

    @SuppressWarnings("unchecked")
    private static UserMessage mergeSkillReadFileUserMessages(List<UserMessage> run) {
        List<Map<String, Object>> mergedBlocks = new ArrayList<>();
        for (UserMessage userMessage : run) {
            List<Object> blocks = userMessage.getContentAsList();
            if (blocks == null) {
                continue;
            }
            List<Map<String, Object>> imageBlocks = new ArrayList<>();
            for (Object block : blocks) {
                if (!(block instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Map<String, Object> map = (Map<String, Object>) rawMap;
                if ("image_url".equals(map.get("type"))) {
                    imageBlocks.add(map);
                }
            }
            for (Object block : blocks) {
                if (!(block instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Map<String, Object> map = (Map<String, Object>) rawMap;
                if (!"text".equals(map.get("type"))) {
                    continue;
                }
                Object text = map.get("text");
                if (text instanceof String value && !value.strip().isEmpty()) {
                    mergedBlocks.add(textBlock(value.strip()));
                }
            }
            mergedBlocks.addAll(imageBlocks);
        }
        return UserMessage.builder()
                .content(mergedBlocks)
                .name(MULTIMODAL_SKILL_USER_MESSAGE_NAME)
                .build();
    }
}
