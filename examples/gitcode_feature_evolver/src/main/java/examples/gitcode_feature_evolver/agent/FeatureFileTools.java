/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.agent;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Repository read/search and dynamically scoped write tools for one feature stage Agent.
 *
 * @since 0.1.12
 */
public final class FeatureFileTools {
    private static final long MAX_READ_BYTES = 1024L * 1024L;
    private static final int MAX_SEARCH_RESULTS = 100;
    private final Path root;
    private final Path realRoot;
    private final List<String> writeScopes;
    private final String toolPrefix;

    /**
     * Bind tools to one Worktree and a controller-approved write scope.
     *
     * @param worktree isolated feature Worktree
     * @param approvedWriteScopes exact files or trailing-slash directories
     * @param agentId unique Agent ID
     */
    public FeatureFileTools(Path worktree, List<String> approvedWriteScopes, String agentId) {
        try {
            this.root = Objects.requireNonNull(worktree, "worktree must not be null")
                    .toAbsolutePath().normalize();
            this.realRoot = root.toRealPath();
        } catch (IOException ex) {
            throw new IllegalStateException("Feature Worktree is unavailable", ex);
        }
        this.writeScopes = FeaturePathPolicy.normalizeScopes(approvedWriteScopes);
        this.toolPrefix = Objects.requireNonNull(agentId, "agentId must not be null") + ".";
    }

    /** @return read, search, and dynamically restricted write tools */
    public List<Tool> create() {
        return List.of(readTool(), searchTool(), writeTool());
    }

    private Tool readTool() {
        ToolCard card = ToolCard.builder()
                .id(toolPrefix + "readFile")
                .name("readFile")
                .description("Read one non-sensitive UTF-8 Worktree file by relative path.")
                .inputParams(schema(Map.of("path", Map.of("type", "string")), List.of("path")))
                .build();
        return new LocalFunction(card, input -> read(required(input, "path")));
    }

    private Tool searchTool() {
        ToolCard card = ToolCard.builder()
                .id(toolPrefix + "searchFiles")
                .name("searchFiles")
                .description("Search non-sensitive UTF-8 Worktree files for literal text.")
                .inputParams(schema(Map.of(
                        "query", Map.of("type", "string"),
                        "path", Map.of("type", "string")), List.of("query")))
                .build();
        return new LocalFunction(card, input -> search(required(input, "query"),
                optional(input, "path", ".")));
    }

    private Tool writeTool() {
        ToolCard card = ToolCard.builder()
                .id(toolPrefix + "writeFile")
                .name("writeFile")
                .description("Replace one UTF-8 file inside the exact controller-approved write scope.")
                .inputParams(schema(Map.of(
                        "path", Map.of("type", "string"),
                        "content", Map.of("type", "string")), List.of("path", "content")))
                .build();
        return new LocalFunction(card, input -> write(required(input, "path"),
                required(input, "content")));
    }

    private Map<String, Object> read(String supplied) {
        String normalized = FeaturePathPolicy.normalize(supplied);
        if (FeaturePathPolicy.isSensitiveRead(normalized)) {
            throw new IllegalArgumentException("Sensitive repository paths are unavailable");
        }
        Path file = resolveExisting(normalized);
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_READ_BYTES) {
                throw new IllegalArgumentException("File is unavailable or too large");
            }
            return Map.of("path", relative(file), "content", Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read repository file", ex);
        }
    }

    private Map<String, Object> search(String query, String directory) {
        if (query.length() > 200) {
            throw new IllegalArgumentException("Search query is too long");
        }
        Path searchRoot = ".".equals(directory) ? realRoot : resolveExisting(directory);
        if (!Files.isDirectory(searchRoot)) {
            throw new IllegalArgumentException("Search path is not a directory");
        }
        List<String> matches = new ArrayList<>();
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        try (Stream<Path> paths = Files.walk(searchRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isSearchable)
                    .forEach(file -> collectMatches(file, lowerQuery, matches));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to search repository files", ex);
        }
        return Map.of("matches", List.copyOf(matches),
                "truncated", matches.size() >= MAX_SEARCH_RESULTS);
    }

    private void collectMatches(Path file, String query, List<String> matches) {
        if (matches.size() >= MAX_SEARCH_RESULTS) {
            return;
        }
        try {
            if (Files.size(file) > MAX_READ_BYTES) {
                return;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size() && matches.size() < MAX_SEARCH_RESULTS; index++) {
                if (lines.get(index).toLowerCase(Locale.ROOT).contains(query)) {
                    matches.add(relative(file) + ":" + (index + 1) + ": " + lines.get(index).strip());
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to search repository file", ex);
        }
    }

    private boolean isSearchable(Path file) {
        String relative = relative(file);
        return !Files.isSymbolicLink(file) && !FeaturePathPolicy.isSensitiveRead(relative)
                && !relative.startsWith("target/");
    }

    private Map<String, Object> write(String supplied, String content) {
        String normalized = FeaturePathPolicy.normalize(supplied);
        if (!FeaturePathPolicy.isAllowedWrite(normalized, writeScopes)) {
            throw new IllegalArgumentException("Write path is outside the controller-approved scope");
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_READ_BYTES * 2L) {
            throw new IllegalArgumentException("File content is too large");
        }
        Path file = resolveForWrite(normalized);
        try {
            Files.createDirectories(file.getParent());
            rejectSymbolicLinks(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), ".feature-write-", ".tmp");
            try {
                Files.writeString(temporary, content, StandardCharsets.UTF_8);
                moveReplacing(temporary, file);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return Map.of("path", normalized, "written", true);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to write repository file", ex);
        }
    }

    private Path resolveExisting(String supplied) {
        Path candidate = resolveRelative(supplied);
        try {
            Path real = candidate.toRealPath();
            if (!real.startsWith(realRoot)) {
                throw new IllegalArgumentException("Path escapes the Worktree");
            }
            return real;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Repository path does not exist", ex);
        }
    }

    private Path resolveForWrite(String supplied) {
        Path candidate = resolveRelative(supplied);
        rejectSymbolicLinks(candidate.getParent());
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(candidate)) {
            throw new IllegalArgumentException("Symbolic-link writes are not allowed");
        }
        return candidate;
    }

    private Path resolveRelative(String supplied) {
        Path candidate = root.resolve(FeaturePathPolicy.normalize(supplied)).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes the Worktree");
        }
        return candidate;
    }

    private void rejectSymbolicLinks(Path candidate) {
        Path current = root;
        Path relative = root.relativize(candidate.toAbsolutePath().normalize());
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("Symbolic-link paths are not allowed");
            }
        }
    }

    private String relative(Path file) {
        return realRoot.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required);
    }

    private static String required(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return text;
    }

    private static String optional(Map<String, Object> input, String key, String fallback) {
        Object value = input.get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
