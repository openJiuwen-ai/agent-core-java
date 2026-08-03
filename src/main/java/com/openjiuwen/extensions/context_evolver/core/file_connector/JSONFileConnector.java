/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.file_connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code JSONFileConnector} in
 * {@code openjiuwen/extensions/context_evolver/core/file_connector/json_file_connector.py}.
 */
public class JSONFileConnector {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final ObjectMapper mapper;

    public JSONFileConnector() {
        this(2, false);
    }

    public JSONFileConnector(int indent, boolean ensureAscii) {
        this.mapper = new ObjectMapper();
        if (indent > 0) {
            this.mapper.writerWithDefaultPrettyPrinter();
        }
        if (ensureAscii) {
            this.mapper.getFactory().configure(com.fasterxml.jackson.core.JsonGenerator.Feature.ESCAPE_NON_ASCII, true);
        }
    }

    public void saveToFile(String filePath, Map<String, Object> data) {
        try {
            Path path = Path.of(filePath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), data);
            LOGGER.info("Saved data to %s (%s top-level keys)", filePath, data.size());
        } catch (IOException exception) {
            LOGGER.error("Failed to save data to %s: %s", filePath, exception.getMessage());
            throw new RuntimeException(exception);
        }
    }

    public Map<String, Object> loadFromFile(String filePath) {
        try {
            Map<String, Object> data = mapper.readValue(Path.of(filePath).toFile(), MAP_TYPE);
            LOGGER.info("Loaded data from %s (%s top-level keys)", filePath, data.size());
            return data;
        } catch (IOException exception) {
            LOGGER.error("Failed to load data from %s: %s", filePath, exception.getMessage());
            throw new RuntimeException(exception);
        }
    }

    public boolean exists(String filePath) {
        return Files.exists(Path.of(filePath));
    }

    public boolean delete(String filePath) {
        try {
            Path path = Path.of(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                LOGGER.info("Deleted file: %s", filePath);
                return true;
            }
            return false;
        } catch (IOException exception) {
            LOGGER.error("Failed to delete %s: %s", filePath, exception.getMessage());
            throw new RuntimeException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> safeModelDump(Object obj) {
        for (String methodName : new String[]{"modelDump", "toDict", "dict"}) {
            try {
                Method method = obj.getClass().getMethod(methodName);
                Object result = method.invoke(obj);
                if (result instanceof Map<?, ?> map) {
                    return new LinkedHashMap<>((Map<String, Object>) map);
                }
            } catch (NoSuchMethodException ignored) {
                // Try the next Python-compatible serialization method.
            } catch (IllegalAccessException | InvocationTargetException exception) {
                LOGGER.debug("%s() failed, trying fallback methods: %s", methodName, exception.getMessage());
            }
        }
        throw new IllegalArgumentException(
                "Object of type " + obj.getClass().getSimpleName()
                        + " has no serialization method (modelDump, toDict, or dict)"
        );
    }
}
