/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.graph_memory.memory_data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data loading utilities for graph memory test data.
 *
 * <p>Mirrors Python's {@code examples.graph_memory.memory_data.dataloader}.</p>
 */
public final class GraphMemoryDataLoader {

    public static final Map<String, String> MAP_AGENT_TO_FULLNAME;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MESSAGE_MAP_TYPE = new TypeReference<>() {
    };

    static {
        Map<String, String> agents = new LinkedHashMap<>();
        agents.put("小智", "技术向导型AI助手");
        agents.put("小赢", "营销推荐型AI助手");
        agents.put("小优", "贴心关怀型AI客服");
        agents.put("小鑫", "规则严谨型AI客服");
        agents.put("小艺", "手机内置个人生活助手");
        MAP_AGENT_TO_FULLNAME = Collections.unmodifiableMap(agents);
    }

    private GraphMemoryDataLoader() {
    }

    public static <T> List<List<T>> chunkConv(List<T> messages, int chunk) {
        return chunkConv(messages, chunk, 0);
    }

    public static <T> List<List<T>> chunkConv(List<T> messages, int chunk, int overlapLast) {
        Objects.requireNonNull(messages, "messages");
        if (chunk == 0) {
            throw new IllegalArgumentException("chunk must not be zero");
        }
        if (chunk < 0) {
            return List.of();
        }
        List<List<T>> chunks = new ArrayList<>();
        for (int startIdx = 0; startIdx < messages.size(); startIdx += chunk) {
            int adjustedStart = Math.max(startIdx - overlapLast, 0);
            int nextIdx = Math.min(adjustedStart + chunk, messages.size());
            chunks.add(new ArrayList<>(messages.subList(adjustedStart, nextIdx)));
        }
        return chunks;
    }

    public static Map<String, String> convertTestData(Map<String, ?> msg) {
        Objects.requireNonNull(msg, "msg");
        if ("user".equals(requiredString(msg, "role"))) {
            return normalized(
                    requiredString(msg, "name") + "（用户）",
                    requiredString(msg, "content"),
                    requiredString(msg, "iso_time")
            );
        }

        String agentId = nullableString(msg.get("agent_id"));
        String agentName = nullableString(msg.get("agent_name"));
        String agent;
        if (MAP_AGENT_TO_FULLNAME.containsKey(agentId)) {
            agent = agentId;
        } else if (MAP_AGENT_TO_FULLNAME.containsKey(agentName)) {
            agent = agentName;
        } else {
            throw new IllegalArgumentException(String.valueOf(msg));
        }

        String fullName = MAP_AGENT_TO_FULLNAME.get(agent);
        String content = removePrefix(requiredString(msg, "content"), fullName).stripLeading();
        return normalized(agent + "（" + fullName + "）", content, requiredString(msg, "iso_time"));
    }

    public static List<Map<String, String>> loadTestData(String file) throws IOException {
        return loadTestData(Path.of(file));
    }

    public static List<Map<String, String>> loadTestData(Path file) throws IOException {
        JsonNode conversation = OBJECT_MAPPER.readTree(file.toFile()).get("conversation");
        if (conversation == null || !conversation.isArray()) {
            throw new IllegalArgumentException("JSON file must contain a conversation array: " + file);
        }

        List<Map<String, String>> converted = new ArrayList<>();
        for (JsonNode message : conversation) {
            converted.add(convertTestData(OBJECT_MAPPER.convertValue(message, MESSAGE_MAP_TYPE)));
        }
        return converted;
    }

    public static List<String> listDataFiles() {
        return listDataFiles(defaultMockDataDirectory());
    }

    public static List<String> listDataFiles(Path mockDataDirectory) {
        if (mockDataDirectory == null || !Files.isDirectory(mockDataDirectory)) {
            return List.of();
        }

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(mockDataDirectory, "conversation_*.json")) {
            for (Path file : stream) {
                files.add(file.toAbsolutePath().normalize());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list graph memory mock data files: " + mockDataDirectory, e);
        }
        files.sort(Comparator.comparing(Path::toString));
        return files.stream().map(Path::toString).toList();
    }

    public static Path defaultMockDataDirectory() {
        String relative = Path.of("examples", "graph_memory", "memory_data", "mock_data").toString();
        List<Path> candidates = List.of(
                Path.of(relative),
                Path.of("..", "agent-core-0.1.12", relative),
                Path.of("agent-core-0.1.12", relative),
                Path.of("src", "main", "resources", relative)
        );
        for (Path candidate : candidates) {
            Path absolute = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(absolute)) {
                return absolute;
            }
        }
        return candidates.getFirst().toAbsolutePath().normalize();
    }

    private static Map<String, String> normalized(String role, String content, String isoTime) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("role", role);
        result.put("content", content);
        result.put("iso_time", isoTime);
        return result;
    }

    private static String removePrefix(String value, String prefix) {
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }

    private static String requiredString(Map<String, ?> msg, String key) {
        Object value = msg.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required message key '" + key + "': " + msg);
        }
        return String.valueOf(value);
    }

    private static String nullableString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
