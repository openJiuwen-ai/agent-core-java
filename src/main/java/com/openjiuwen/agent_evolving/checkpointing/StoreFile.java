// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.checkpointing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Store file utilities for checkpoint management.
 * <p>
 * Mirrors Python's {@code store_file.py} from
 * {@code openjiuwen.agent_evolving.checkpointing.store_file}.
 */
public final class StoreFile {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };
    
    private StoreFile() {
        // Utility class
    }
    
    /**
     * Write checkpoint to file.
     */
    public static void writeFile(Path filePath, Map<String, Object> data) {
        Objects.requireNonNull(filePath, "filePath");
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(toJsonCompatible(data != null ? data : Map.of()));
            Files.writeString(filePath, json, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write checkpoint file: " + filePath, exception);
        }
    }
    
    /**
     * Read checkpoint from file.
     */
    public static Map<String, Object> readFile(Path filePath) {
        Objects.requireNonNull(filePath, "filePath");
        if (!Files.exists(filePath)) {
            return new LinkedHashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(Files.readString(filePath, StandardCharsets.UTF_8), MAP_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read checkpoint file: " + filePath, exception);
        }
    }
    
    /**
     * Delete checkpoint file.
     */
    public static boolean deleteFile(Path filePath) {
        try {
            return Files.deleteIfExists(filePath);
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object toJsonCompatible(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), toJsonCompatible(entry.getValue()));
            }
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            for (Object item : iterable) {
                result.add(toJsonCompatible(item));
            }
            return result;
        }
        if (value instanceof Object[] array) {
            List<Object> result = new ArrayList<>();
            for (Object item : array) {
                result.add(toJsonCompatible(item));
            }
            return result;
        }
        return OBJECT_MAPPER.convertValue(value, new TypeReference<Map<String, Object>>() { });
    }
}
