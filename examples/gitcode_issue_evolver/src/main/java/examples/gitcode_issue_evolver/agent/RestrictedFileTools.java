/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.agent;

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
 * Creates the three repository-scoped file tools available to the Issue worker Agent.
 *
 * @since 0.1.12
 */
public final class RestrictedFileTools {
    private static final long MAX_READ_BYTES = 1024L * 1024L;
    private static final int MAX_SEARCH_RESULTS = 100;
    private final Path root;
    private final Path realRoot;
    private final String toolPrefix;

    /**
     * Bind file tools to one isolated Worktree.
     *
     * @param worktree isolated Worktree root
     * @param agentId unique Agent identifier
     */
    public RestrictedFileTools(Path worktree, String agentId) {
        try {
            this.root = Objects.requireNonNull(worktree, "worktree must not be null")
                    .toAbsolutePath()
                    .normalize();
            this.realRoot = this.root.toRealPath();
        } catch (IOException ex) {
            throw new IllegalStateException("Worktree is unavailable", ex);
        }
        this.toolPrefix = Objects.requireNonNull(agentId, "agentId must not be null") + ".";
    }

    /**
     * Create all tools for one Agent registration.
     *
     * @return read, search, and write tools
     */
    public List<Tool> create() {
        return List.of(readTool(), searchTool(), writeTool());
    }

    private Tool readTool() {
        ToolCard card = ToolCard.builder()
                .id(toolPrefix + "readFile")
                .name("readFile")
                .description("Read one UTF-8 repository file. Path must be relative to the Worktree.")
                .inputParams(objectSchema(Map.of(
                        "path", Map.of("type", "string", "description", "Worktree-relative file path")),
                        List.of("path")))
                .build();
        return new LocalFunction(card, inputs -> {
            Path file = resolveExisting(requiredString(inputs, "path"));
            try {
                if (!Files.isRegularFile(file) || Files.size(file) > MAX_READ_BYTES) {
                    throw new IllegalArgumentException("File is unavailable or too large");
                }
                return Map.of("path", relative(file), "content", Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to read repository file", ex);
            }
        });
    }

    private Tool searchTool() {
        ToolCard card = ToolCard.builder()
                .id(toolPrefix + "searchFiles")
                .name("searchFiles")
                .description("Search UTF-8 repository files for text. Paths must be relative to the Worktree.")
                .inputParams(objectSchema(Map.of(
                        "query", Map.of("type", "string", "description", "Text to find"),
                        "path", Map.of("type", "string", "description", "Optional relative directory")),
                        List.of("query")))
                .build();
        return new LocalFunction(card, inputs -> search(
                requiredString(inputs, "query"), optionalString(inputs, "path", ".")));
    }

    private Tool writeTool() {
        ToolCard card = ToolCard.builder()
                .id(toolPrefix + "writeFile")
                .name("writeFile")
                .description("Replace one UTF-8 Java source or test file inside the Worktree.")
                .inputParams(objectSchema(Map.of(
                        "path", Map.of("type", "string", "description", "Worktree-relative file path"),
                        "content", Map.of("type", "string", "description", "Complete UTF-8 file content")),
                        List.of("path", "content")))
                .build();
        return new LocalFunction(card, inputs -> write(
                requiredString(inputs, "path"), requiredString(inputs, "content")));
    }

    private Map<String, Object> search(String query, String directory) {
        if (query.length() > 200) {
            throw new IllegalArgumentException("Search query is too long");
        }
        Path searchRoot = ".".equals(directory) ? realRoot : resolveExisting(directory);
        if (!Files.isDirectory(searchRoot)) {
            throw new IllegalArgumentException("Search path is not a directory");
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(searchRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isSearchable)
                    .takeWhile(ignored -> matches.size() < MAX_SEARCH_RESULTS)
                    .forEach(file -> collectMatches(file, normalizedQuery, matches));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to search repository files", ex);
        }
        return Map.of("matches", List.copyOf(matches), "truncated", matches.size() >= MAX_SEARCH_RESULTS);
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
                String line = lines.get(index);
                if (line.toLowerCase(Locale.ROOT).contains(query)) {
                    matches.add(relative(file) + ":" + (index + 1) + ": " + line.strip());
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to search repository file", ex);
        }
    }

    private boolean isSearchable(Path file) {
        String relative = relative(file);
        return !relative.startsWith(".git/") && !relative.startsWith("target/")
                && !Files.isSymbolicLink(file);
    }

    private Map<String, Object> write(String path, String content) {
        Path file = resolveForWrite(path);
        String relative = relative(file);
        if (!relative.startsWith("src/main/java/") && !relative.startsWith("src/test/java/")) {
            throw new IllegalArgumentException("Write path is outside the demo Java scope");
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_READ_BYTES * 2L) {
            throw new IllegalArgumentException("File content is too large");
        }
        try {
            Files.createDirectories(file.getParent());
            rejectSymbolicLinks(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), ".evolver-write-", ".tmp");
            try {
                Files.writeString(temporary, content, StandardCharsets.UTF_8);
                moveReplacing(temporary, file);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return Map.of("path", relative, "written", true);
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
        if (supplied == null || supplied.isBlank()) {
            throw new IllegalArgumentException("Path is required");
        }
        Path relative = Path.of(supplied);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Absolute paths are not allowed");
        }
        for (Path segment : relative) {
            if (".".equals(segment.toString()) || "..".equals(segment.toString())) {
                throw new IllegalArgumentException("Path traversal is not allowed");
            }
        }
        Path candidate = root.resolve(relative).normalize();
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

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required);
    }

    private static String requiredString(Map<String, Object> inputs, String key) {
        Object value = inputs.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return text;
    }

    private static String optionalString(Map<String, Object> inputs, String key, String fallback) {
        Object value = inputs.get(key);
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
