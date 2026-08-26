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
    private static final int DEFAULT_READ_LINES = 500;
    private static final int MAX_READ_LINES = 2_000;
    private static final int MAX_LINE_CHARACTERS = 2_000;
    private static final int MAX_INLINE_CHARACTERS = 40_000;
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
        return List.of(readTool(), searchTool(), writeTool(), replaceTool());
    }

    private Tool readTool() {
        ToolCard card = ToolCard.builder()
                .id(toolPrefix + "readFile")
                .name("readFile")
                .description("Read a bounded UTF-8 line range. Continue from nextOffset while hasMore is true.")
                .inputParams(objectSchema(Map.of(
                        "path", Map.of("type", "string", "description", "Worktree-relative file path"),
                        "offset", Map.of("type", "integer", "minimum", 1),
                        "limit", Map.of("type", "integer", "minimum", 1,
                                "maximum", MAX_READ_LINES)),
                        List.of("path")))
                .build();
        return new LocalFunction(card, inputs -> read(requiredString(inputs, "path"),
                optionalInteger(inputs, "offset", 1, 1, Integer.MAX_VALUE),
                optionalInteger(inputs, "limit", DEFAULT_READ_LINES, 1, MAX_READ_LINES)));
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
                .description("Replace one UTF-8 source, test, or runtime-resource file inside the Worktree.")
                .inputParams(objectSchema(Map.of(
                        "path", Map.of("type", "string", "description", "Worktree-relative file path"),
                        "content", Map.of("type", "string", "description", "Complete UTF-8 file content")),
                        List.of("path", "content")))
                .build();
        return new LocalFunction(card, inputs -> write(
                requiredString(inputs, "path"), requiredString(inputs, "content")));
    }

    private Tool replaceTool() {
        ToolCard card = ToolCard.builder()
                .id(toolPrefix + "replaceInFile")
                .name("replaceInFile")
                .description("Replace exactly one matching block in an in-scope UTF-8 file.")
                .inputParams(objectSchema(Map.of(
                        "path", Map.of("type", "string"),
                        "oldContent", Map.of("type", "string"),
                        "newContent", Map.of("type", "string")),
                        List.of("path", "oldContent", "newContent")))
                .build();
        return new LocalFunction(card, inputs -> replace(requiredString(inputs, "path"),
                requiredString(inputs, "oldContent"), stringValue(inputs, "newContent")));
    }

    private Map<String, Object> read(String path, int offset, int limit) {
        if (isSensitivePath(path)) {
            throw new IllegalArgumentException("Sensitive repository paths are unavailable");
        }
        Path file = resolveExisting(path);
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_READ_BYTES) {
                throw new IllegalArgumentException("File is unavailable or too large");
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (offset > lines.size() && !(lines.isEmpty() && offset == 1)) {
                throw new IllegalArgumentException("Read offset is past the end of the file");
            }
            int start = Math.min(offset - 1, lines.size());
            int maximumEnd = Math.min(lines.size(), start + limit);
            List<String> page = new ArrayList<>();
            int retainedCharacters = 0;
            int end = start;
            while (end < maximumEnd) {
                String line = boundedLine(lines.get(end));
                int nextCharacters = retainedCharacters + line.length() + (page.isEmpty() ? 0 : 1);
                if (nextCharacters > MAX_INLINE_CHARACTERS) {
                    break;
                }
                page.add(line);
                retainedCharacters = nextCharacters;
                end++;
            }
            boolean hasMore = end < lines.size();
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("path", relative(file));
            result.put("offset", offset);
            result.put("returnedLines", page.size());
            result.put("totalLines", lines.size());
            result.put("nextOffset", hasMore ? end + 1 : 0);
            result.put("hasMore", hasMore);
            result.put("outputCapped", end < maximumEnd);
            result.put("content", String.join("\n", page));
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read repository file", ex);
        }
    }

    private Map<String, Object> search(String query, String directory) {
        if (query.length() > 200) {
            throw new IllegalArgumentException("Search query is too long");
        }
        if (!".".equals(directory) && isSensitivePath(directory)) {
            throw new IllegalArgumentException("Sensitive repository paths are unavailable");
        }
        Path searchRoot = ".".equals(directory) ? realRoot : resolveExisting(directory);
        if (!Files.isDirectory(searchRoot)) {
            throw new IllegalArgumentException("Search path is not a directory");
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        int[] skipped = new int[1];
        int[] resultCharacters = new int[1];
        boolean[] outputCapped = new boolean[1];
        try (Stream<Path> paths = Files.walk(searchRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isSearchable)
                    .takeWhile(ignored -> matches.size() < MAX_SEARCH_RESULTS)
                    .forEach(file -> collectMatches(file, normalizedQuery, matches, skipped,
                            resultCharacters, outputCapped));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to search repository files", ex);
        }
        return Map.of("matches", List.copyOf(matches),
                "truncated", matches.size() >= MAX_SEARCH_RESULTS || outputCapped[0],
                "outputCapped", outputCapped[0],
                "skippedUnreadableFiles", skipped[0], "scanComplete", skipped[0] == 0);
    }

    private void collectMatches(Path file, String query, List<String> matches, int[] skipped,
                                int[] resultCharacters, boolean[] outputCapped) {
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
                    String match = relative(file) + ":" + (index + 1) + ": "
                            + boundedLine(line.strip());
                    int nextCharacters = resultCharacters[0] + match.length()
                            + (matches.isEmpty() ? 0 : 1);
                    if (nextCharacters > MAX_INLINE_CHARACTERS) {
                        outputCapped[0] = true;
                        continue;
                    }
                    matches.add(match);
                    resultCharacters[0] = nextCharacters;
                }
            }
        } catch (IOException ex) {
            skipped[0]++;
        }
    }

    private boolean isSearchable(Path file) {
        String relative = relative(file);
        return !".git".equals(relative) && !relative.startsWith(".git/")
                && !"target".equals(relative) && !relative.startsWith("target/")
                && !isSensitivePath(relative)
                && !Files.isSymbolicLink(file);
    }

    private static boolean isSensitivePath(String path) {
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        return ".git".equals(normalized) || normalized.startsWith(".git/")
                || "target".equals(normalized) || normalized.startsWith("target/")
                || "apiconfig.json".equals(name) || "gitcode-config.json".equals(name)
                || ".env".equals(name)
                || "evolver-secrets.json".equals(name)
                || "feature-secrets.json".equals(name)
                || name.endsWith(".pem") || name.endsWith(".key");
    }

    private Map<String, Object> write(String path, String content) {
        Path file = resolveForWrite(path);
        String relative = relative(file);
        requireWriteScope(relative);
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

    private Map<String, Object> replace(String path, String oldContent, String newContent) {
        Path file = resolveExisting(path);
        String relative = relative(file);
        requireWriteScope(relative);
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            int first = content.indexOf(oldContent);
            if (first < 0) {
                throw new IllegalArgumentException("Replacement block was not found");
            }
            if (content.indexOf(oldContent, first + oldContent.length()) >= 0) {
                throw new IllegalArgumentException("Replacement block is not unique");
            }
            String updated = content.substring(0, first) + newContent
                    + content.substring(first + oldContent.length());
            return write(relative, updated);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to replace repository file", ex);
        }
    }

    private static void requireWriteScope(String relative) {
        if (!relative.startsWith("src/main/") && !relative.startsWith("src/test/")) {
            throw new IllegalArgumentException("Write path is outside the demo source and test scope");
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
        return Map.of("type", "object", "properties", properties, "required", required,
                "additionalProperties", false);
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

    private static String stringValue(Map<String, Object> inputs, String key) {
        Object value = inputs.get(key);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return text;
    }

    private static int optionalInteger(Map<String, Object> inputs, String key, int fallback,
                                       int minimum, int maximum) {
        Object value = inputs.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        long supplied = number.longValue();
        if (supplied < minimum || supplied > maximum || number.doubleValue() != supplied) {
            throw new IllegalArgumentException(key + " is outside the supported range");
        }
        return (int) supplied;
    }

    private static String boundedLine(String value) {
        if (value.length() <= MAX_LINE_CHARACTERS) {
            return value;
        }
        return value.substring(0, MAX_LINE_CHARACTERS) + "... (line truncated)";
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
