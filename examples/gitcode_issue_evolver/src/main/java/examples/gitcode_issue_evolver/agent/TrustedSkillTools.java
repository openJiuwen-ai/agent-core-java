/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.agent;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** Read-only, bounded access to the Controller-staged trusted Skill bundle. */
public final class TrustedSkillTools {
    private static final long MAX_FILE_BYTES = 2L * 1024L * 1024L;
    private static final int DEFAULT_READ_LINES = 500;
    private static final int MAX_READ_LINES = 2_000;
    private static final int MAX_RESULT_CHARACTERS = 40_000;
    private static final int MAX_SEARCH_RESULTS = 100;
    private final Path root;
    private final Path realRoot;
    private final String toolPrefix;

    /** Bind immutable Skill tools to one trusted staging root. */
    public TrustedSkillTools(Path skillsRoot, String agentId) {
        try {
            this.root = Objects.requireNonNull(skillsRoot, "skillsRoot must not be null")
                    .toAbsolutePath().normalize();
            this.realRoot = root.toRealPath();
        } catch (IOException ex) {
            throw new IllegalStateException("Trusted Skill root is unavailable", ex);
        }
        this.toolPrefix = Objects.requireNonNull(agentId, "agentId must not be null") + ".";
    }

    /** @return bounded read-only Skill tools */
    public List<Tool> create() {
        return List.of(readTool(), searchTool());
    }

    private Tool readTool() {
        ToolCard card = ToolCard.builder()
                .id(toolPrefix + "readSkillFile")
                .name("readSkillFile")
                .description("Read a bounded UTF-8 file from the immutable trusted Skill bundle. "
                        + "Use paths such as coding-standard-full/rules/G.OTH.md.")
                .inputParams(schema(Map.of(
                        "path", Map.of("type", "string"),
                        "offset", Map.of("type", "integer", "minimum", 1),
                        "limit", Map.of("type", "integer", "minimum", 1,
                                "maximum", MAX_READ_LINES)), List.of("path")))
                .build();
        return new LocalFunction(card, input -> read(required(input, "path"),
                optionalInteger(input, "offset", 1, 1, Integer.MAX_VALUE),
                optionalInteger(input, "limit", DEFAULT_READ_LINES, 1, MAX_READ_LINES)));
    }

    private Tool searchTool() {
        ToolCard card = ToolCard.builder()
                .id(toolPrefix + "searchSkillFiles")
                .name("searchSkillFiles")
                .description("Search immutable trusted Skill files for literal text. "
                        + "The optional path is relative to the staged Skill root.")
                .inputParams(schema(Map.of(
                        "query", Map.of("type", "string"),
                        "path", Map.of("type", "string")), List.of("query")))
                .build();
        return new LocalFunction(card, input -> search(required(input, "query"),
                optional(input, "path", ".")));
    }

    private Map<String, Object> read(String supplied, int offset, int limit) {
        Path file = resolve(supplied);
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("Trusted Skill file is unavailable or too large");
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (offset > lines.size() && !(lines.isEmpty() && offset == 1)) {
                throw new IllegalArgumentException("Read offset is past the end of the Skill file");
            }
            int start = Math.min(offset - 1, lines.size());
            int maximumEnd = Math.min(lines.size(), start + limit);
            List<String> page = new ArrayList<>();
            int characters = 0;
            int end = start;
            while (end < maximumEnd) {
                String line = lines.get(end);
                int next = characters + line.length() + (page.isEmpty() ? 0 : 1);
                if (next > MAX_RESULT_CHARACTERS) {
                    break;
                }
                page.add(line);
                characters = next;
                end++;
            }
            boolean hasMore = end < lines.size();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", relative(file));
            result.put("offset", offset);
            result.put("returnedLines", page.size());
            result.put("totalLines", lines.size());
            result.put("nextOffset", hasMore ? end + 1 : 0);
            result.put("hasMore", hasMore);
            result.put("content", String.join("\n", page));
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read trusted Skill file", ex);
        }
    }

    private Map<String, Object> search(String query, String supplied) {
        if (query.length() > 200) {
            throw new IllegalArgumentException("Skill search query is too long");
        }
        Path start = ".".equals(supplied) ? realRoot : resolve(supplied);
        List<String> matches = new ArrayList<>();
        try (Stream<Path> paths = Files.isDirectory(start) ? Files.walk(start) : Stream.of(start)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                if (Files.size(file) > MAX_FILE_BYTES) {
                    continue;
                }
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int index = 0; index < lines.size() && matches.size() < MAX_SEARCH_RESULTS; index++) {
                    if (lines.get(index).toLowerCase(Locale.ROOT)
                            .contains(query.toLowerCase(Locale.ROOT))) {
                        matches.add(relative(file) + ":" + (index + 1) + ": " + lines.get(index));
                    }
                }
                if (matches.size() >= MAX_SEARCH_RESULTS) {
                    break;
                }
            }
            return Map.of("matches", List.copyOf(matches),
                    "truncated", matches.size() >= MAX_SEARCH_RESULTS);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to search trusted Skill files", ex);
        }
    }

    private Path resolve(String supplied) {
        if (supplied == null || supplied.isBlank()) {
            throw new IllegalArgumentException("Skill path is required");
        }
        Path relative = Path.of(supplied);
        if (relative.isAbsolute() || Stream.of(relative).anyMatch(path -> "..".equals(path.toString()))) {
            throw new IllegalArgumentException("Trusted Skill path must remain relative");
        }
        try {
            Path resolved = root.resolve(relative).normalize().toRealPath();
            if (!resolved.startsWith(realRoot)) {
                throw new IllegalArgumentException("Trusted Skill path escapes the staging root");
            }
            return resolved;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Trusted Skill path does not exist", ex);
        }
    }

    private String relative(Path file) {
        return realRoot.relativize(file).toString().replace('\\', '/');
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required,
                "additionalProperties", false);
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

    private static int optionalInteger(Map<String, Object> input, String key, int fallback,
                                       int minimum, int maximum) {
        Object value = input.get(key);
        int result = value instanceof Number number ? number.intValue() : fallback;
        if (result < minimum || result > maximum) {
            throw new IllegalArgumentException(key + " is outside the allowed range");
        }
        return result;
    }
}
