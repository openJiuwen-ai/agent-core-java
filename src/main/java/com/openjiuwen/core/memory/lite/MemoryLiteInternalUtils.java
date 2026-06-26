/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.harness.workspace.Workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Internal utilities for lite memory indexing and retrieval.
 *
 * <p>Mirrors Python's {@code openjiuwen/core/memory/lite/internal.py}.</p>
 */
public final class MemoryLiteInternalUtils {

    private static final Pattern WORD_PATTERN = Pattern.compile("\\w+");

    private MemoryLiteInternalUtils() {
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / 4;
    }

    public static void ensureDir(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(Path.of(path));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create directory: " + path, exception);
        }
    }

    public static List<String> listMemoryFiles(Workspace workspace) {
        return listMemoryFiles(workspace, null, "memory");
    }

    public static List<String> listMemoryFiles(Workspace workspace, List<String> extraPaths, String nodeName) {
        List<String> files = new ArrayList<>();
        List<String> resolvedExtraPaths = extraPaths == null ? List.of() : extraPaths;
        String effectiveNodeName = nodeName == null || nodeName.isBlank() ? "memory" : nodeName;

        Path memoryDir = workspace == null ? null : workspace.getNodePath(effectiveNodeName);
        if (memoryDir != null && Files.isDirectory(memoryDir)) {
            files.addAll(listMarkdownFiles(memoryDir));

            String dailyRel = workspace.getDirectory("daily_memory");
            if (dailyRel != null && !dailyRel.isBlank()) {
                Path dailyMemoryDir = memoryDir.resolve(dailyRel).normalize();
                if (Files.isDirectory(dailyMemoryDir)) {
                    files.addAll(listMarkdownFiles(dailyMemoryDir));
                }
            }
        }

        Path userMdPath = workspace == null ? null : workspace.getNodePath("USER.md");
        if (userMdPath != null && Files.isRegularFile(userMdPath)) {
            files.add(userMdPath.toString());
        }

        for (String extra : resolvedExtraPaths) {
            if (extra == null || extra.isBlank()) {
                continue;
            }
            Path fullPath = memoryDir != null ? memoryDir.resolve(extra).normalize() : Path.of(extra).normalize();
            if (Files.isRegularFile(fullPath) && fullPath.toString().endsWith(".md")) {
                files.add(fullPath.toString());
            } else if (Files.isDirectory(fullPath)) {
                files.addAll(listMarkdownFiles(fullPath));
            }
        }

        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (String file : files) {
            if (Objects.nonNull(file)) {
                deduplicated.add(file);
            }
        }
        List<String> sorted = new ArrayList<>(deduplicated);
        sorted.sort(String::compareTo);
        return sorted;
    }

    public static CompletableFuture<Map<String, Object>> buildFileEntry(String absPath, String workspaceDir) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path absolutePath = Path.of(absPath);
                String content = Files.readString(absolutePath, StandardCharsets.UTF_8);
                Path workspacePath = Path.of(workspaceDir);
                return Map.of(
                        "path", workspacePath.relativize(absolutePath).toString(),
                        "absPath", absPath,
                        "hash", hashText(content),
                        "mtimeMs", Files.getLastModifiedTime(absolutePath).toMillis(),
                        "size", Files.size(absolutePath)
                );
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to build file entry for " + absPath, exception);
            }
        });
    }

    public static List<MemoryChunk> chunkMarkdown(String content) {
        return chunkMarkdown(content, null);
    }

    public static List<MemoryChunk> chunkMarkdown(String content, Map<String, Object> settings) {
        Map<String, Object> effectiveSettings = settings == null ? Map.of() : settings;
        int targetTokens = intSetting(effectiveSettings, "tokens", 256);
        int overlap = intSetting(effectiveSettings, "overlap", 32);

        String[] lines = (content == null ? "" : content).split("\n", -1);
        List<MemoryChunk> chunks = new ArrayList<>();
        List<String> currentChunk = new ArrayList<>();
        int currentTokens = 0;
        int startLine = 1;

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            int lineNumber = index + 1;
            int lineTokens = estimateTokens(line);

            if (currentTokens + lineTokens > targetTokens && !currentChunk.isEmpty()) {
                chunks.add(new MemoryChunk(String.join("\n", currentChunk), startLine, lineNumber - 1));

                List<String> overlapLines = new ArrayList<>();
                int overlapTokens = 0;
                for (int j = currentChunk.size() - 1; j >= 0; j--) {
                    int tokens = estimateTokens(currentChunk.get(j));
                    if (overlapTokens + tokens > overlap) {
                        break;
                    }
                    overlapLines.add(0, currentChunk.get(j));
                    overlapTokens += tokens;
                }
                currentChunk = overlapLines;
                currentTokens = overlapTokens;
                startLine = lineNumber - overlapLines.size();
            }

            currentChunk.add(line);
            currentTokens += lineTokens;
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(new MemoryChunk(String.join("\n", currentChunk), startLine, lines.length));
        }

        return chunks;
    }

    public static String hashText(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Float> parseEmbedding(Object data) {
        if (data instanceof List<?> list) {
            List<Float> embedding = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Number number) {
                    embedding.add(number.floatValue());
                }
            }
            return embedding;
        }
        if (data instanceof Map<?, ?> map) {
            if (map.containsKey("embedding")) {
                return parseEmbedding(map.get("embedding"));
            }
            Object nested = map.get("data");
            if (nested instanceof List<?> nestedList && !nestedList.isEmpty() && nestedList.get(0) instanceof Map<?, ?> firstItem) {
                if (firstItem.containsKey("embedding")) {
                    return parseEmbedding(((Map<String, Object>) firstItem).get("embedding"));
                }
            }
        }
        return null;
    }

    public static String buildFtsQuery(String query) {
        String cleaned = query == null ? "" : query.trim();
        if (cleaned.isEmpty()) {
            return "";
        }

        Matcher matcher = WORD_PATTERN.matcher(cleaned);
        List<String> tokens = new ArrayList<>();
        while (matcher.find() && tokens.size() < 10) {
            tokens.add("\"" + matcher.group() + "\"");
        }
        return String.join(" OR ", tokens);
    }

    public static double bm25RankToScore(double rank) {
        if (rank >= 0) {
            return 1.0d / (1.0d + rank);
        }
        return 1.0d / (1.0d - rank);
    }

    public static boolean isMemoryPath(String relPath) {
        String normalized = relPath == null ? "" : relPath.replace("\\", "/");
        return normalized.endsWith(".md");
    }

    public static List<String> normalizeExtraMemoryPaths(List<String> paths, String workspaceDir) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        Path workspacePath = Path.of(workspaceDir);
        List<String> normalized = new ArrayList<>();
        for (String path : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            Path candidate = Path.of(path);
            normalized.add(candidate.isAbsolute() ? candidate.toString() : workspacePath.resolve(path).normalize().toString());
        }
        return normalized;
    }

    public static double cosineSimilarity(List<Float> vec1, List<Float> vec2) {
        if (vec1 == null || vec2 == null || vec1.size() != vec2.size()) {
            return 0.0d;
        }
        double dot = 0.0d;
        double norm1 = 0.0d;
        double norm2 = 0.0d;
        for (int index = 0; index < vec1.size(); index++) {
            float left = vec1.get(index);
            float right = vec2.get(index);
            dot += left * right;
            norm1 += left * left;
            norm2 += right * right;
        }
        if (norm1 < 1e-10 || norm2 < 1e-10) {
            return 0.0d;
        }
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private static List<String> listMarkdownFiles(Path directory) {
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(Path::toString)
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private static int intSetting(Map<String, Object> settings, String key, int defaultValue) {
        Object value = settings.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
