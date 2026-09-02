/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Internal utilities for lite memory.
 * 
 * @since 0.1.7
 */
public final class LiteMemoryInternal {
    private static final Pattern TOKEN_RE = Pattern.compile("\\w+");

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern DAILY_RE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}\\.md$");

    /**
     * LiteMemoryInternal.
     * 
     * @since 0.1.7
     */
    private LiteMemoryInternal() {
    }

    /**
     * estimateTokens.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / 4;
    }

    /**
     * ensureDir.
     * 
     * @param path path
     * @throws IOException IOException
     * @since 0.1.7
     */
    public static void ensureDir(Path path) throws IOException {
        if (path != null) {
            Files.createDirectories(path);
        }
    }

    /**
     * listMemoryFiles.
     * 
     * @param workspace workspace
     * @param extraPaths extraPaths
     * @param nodeName nodeName
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public static List<Path> listMemoryFiles(Workspace workspace, List<String> extraPaths, String nodeName)
            throws IOException {
        List<Path> files = new ArrayList<>();
        Path memoryDir = workspace.getNodePath(nodeName);
        if (memoryDir != null && Files.isDirectory(memoryDir)) {
            try (var stream = Files.list(memoryDir)) {
                stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".md"))
                        .forEach(files::add);
            }
            Path daily = memoryDir.resolve("daily_memory");
            if (Files.isDirectory(daily)) {
                try (var stream = Files.list(daily)) {
                    stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(".md"))
                            .forEach(files::add);
                }
            }
        }
        Path userPath = workspace.root().resolve("USER.md");
        if (Files.isRegularFile(userPath)) {
            files.add(userPath);
        }
        if (extraPaths != null) {
            for (String extra : extraPaths) {
                Path path = Path.of(extra);
                if (!path.isAbsolute()) {
                    path = workspace.root().resolve(extra);
                }
                if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".md")) {
                    files.add(path.normalize());
                }
            }
        }
        return files.stream().distinct().sorted().toList();
    }

    /**
     * buildFileEntry.
     * 
     * @param absPath absPath
     * @param workspaceDir workspaceDir
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public static Map<String, Object> buildFileEntry(Path absPath, Path workspaceDir) throws IOException {
        String content = Files.readString(absPath, StandardCharsets.UTF_8);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", workspaceDir.relativize(absPath).toString().replace('\\', '/'));
        entry.put("absPath", absPath.toString());
        entry.put("hash", hashText(content));
        entry.put("mtimeMs", Files.getLastModifiedTime(absPath).toMillis());
        entry.put("size", Files.size(absPath));
        return entry;
    }

    /**
     * chunkMarkdown.
     * 
     * @param content content
     * @param settings settings
     * @return the result
     * @since 0.1.7
     */
    public static List<MemoryChunk> chunkMarkdown(String content, Map<String, Integer> settings) {
        int targetTokens = settings != null ? settings.getOrDefault("tokens", 256) : 256;
        int overlap = settings != null ? settings.getOrDefault("overlap", 32) : 32;
        String[] lines = content.split("\n", -1);
        List<MemoryChunk> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentTokens = 0;
        int startLine = 1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineTokens = estimateTokens(line);
            if (currentTokens + lineTokens > targetTokens && !current.isEmpty()) {
                chunks.add(new MemoryChunk(String.join("\n", current), startLine, i));
                List<String> overlapLines = new ArrayList<>();
                int overlapTokens = 0;
                for (int j = current.size() - 1; j >= 0; j--) {
                    int lt = estimateTokens(current.get(j));
                    if (overlapTokens + lt > overlap) {
                        break;
                    }
                    overlapLines.add(0, current.get(j));
                    overlapTokens += lt;
                }
                current = overlapLines;
                currentTokens = overlapTokens;
                startLine = i + 1 - overlapLines.size();
            }
            current.add(line);
            currentTokens += lineTokens;
        }
        if (!current.isEmpty()) {
            chunks.add(new MemoryChunk(String.join("\n", current), startLine, lines.length));
        }
        return chunks;
    }

    /**
     * hashText.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    public static String hashText(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * buildFtsQuery.
     * 
     * @param query query
     * @return the result
     * @since 0.1.7
     */
    public static String buildFtsQuery(String query) {
        String cleaned = query == null ? "" : query.trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        Matcher matcher = TOKEN_RE.matcher(cleaned);
        List<String> tokens = new ArrayList<>();
        while (matcher.find() && tokens.size() < 10) {
            tokens.add('"' + matcher.group() + '"');
        }
        return String.join(" OR ", tokens);
    }

    /**
     * bm25RankToScore.
     * 
     * @param rank rank
     * @return the result
     * @since 0.1.7
     */
    public static double bm25RankToScore(double rank) {
        return rank >= 0 ? 1.0 / (1.0 + rank) : 1.0 / (1.0 - rank);
    }

    /**
     * isMemoryPath.
     * 
     * @param relPath relPath
     * @return the result
     * @since 0.1.7
     */
    public static boolean isMemoryPath(String relPath) {
        return relPath != null && relPath.replace('\\', '/').endsWith(".md");
    }

    /**
     * isDailyMemoryFilename.
     * 
     * @param basename basename
     * @return the result
     * @since 0.1.7
     */
    public static boolean isDailyMemoryFilename(String basename) {
        return basename != null && DAILY_RE.matcher(basename).matches();
    }
}
