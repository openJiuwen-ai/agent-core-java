/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.skill_branch;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors Python's manifest helpers in
 * {@code openjiuwen/harness/tools/mobile_gui/skill_branch/manifest.py}.
 */
public final class SkillBranchManifest {

    private static final Pattern MARKDOWN_IMAGE_RE = Pattern.compile("!\\[([^\\]]*)\\]\\((.*?)\\)");

    private SkillBranchManifest() {
    }

    public static List<SkillImageEntry> buildSkillImageManifest(String skillMarkdown, String skillDirectory) {
        Path base = Path.of(skillDirectory).toAbsolutePath().normalize();
        List<SkillImageEntry> entries = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();

        Matcher matcher = MARKDOWN_IMAGE_RE.matcher(skillMarkdown == null ? "" : skillMarkdown);
        int index = 0;
        while (matcher.find()) {
            String alt = safeTrim(matcher.group(1));
            String rawUrl = safeTrim(matcher.group(2));
            if (rawUrl.isEmpty()
                    || rawUrl.startsWith("http://")
                    || rawUrl.startsWith("https://")
                    || rawUrl.startsWith("data:")) {
                index += 1;
                continue;
            }

            String decoded = urlDecode(rawUrl);
            String relPath = decoded.replace("\\", "/");
            Path candidate = base.resolve(decoded).normalize();
            if (!Files.isRegularFile(candidate)) {
                index += 1;
                continue;
            }

            String imageId = stableImageId(relPath, index);
            if (seenIds.contains(imageId)) {
                imageId = imageId + "_" + index;
            }
            seenIds.add(imageId);

            String resolvedAlt = alt.isEmpty() ? Path.of(relPath).getFileName().toString() : alt;
            entries.add(new SkillImageEntry(imageId, resolvedAlt, relPath, candidate.toString()));
            index += 1;
        }

        return entries;
    }

    public static String formatManifestForPrompt(List<SkillImageEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "(no local reference images)";
        }

        List<String> lines = new ArrayList<>();
        for (SkillImageEntry entry : entries) {
            lines.add("- " + entry.imageId() + ": alt=" + quote(entry.alt()) + ", path=" + entry.relPath());
        }
        return String.join("\n", lines);
    }

    private static String stableImageId(String relPath, int index) {
        String decoded = urlDecode(relPath);
        String fileName = Path.of(decoded).getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        if (!stem.isBlank()) {
            return stem;
        }
        return "image_" + index;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "\\'") + "'";
    }

    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }
}
