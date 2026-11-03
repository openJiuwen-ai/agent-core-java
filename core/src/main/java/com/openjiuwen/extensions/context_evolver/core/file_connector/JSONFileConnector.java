/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.file_connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
    private final Path allowedRoot;

    public JSONFileConnector() {
        this(Path.of("").toAbsolutePath().normalize(), 2, false);
    }

    public JSONFileConnector(int indent, boolean ensureAscii) {
        this(Path.of("").toAbsolutePath().normalize(), indent, ensureAscii);
    }

    public JSONFileConnector(Path allowedRoot) {
        this(allowedRoot, 2, false);
    }

    public JSONFileConnector(Path allowedRoot, int indent, boolean ensureAscii) {
        if (allowedRoot == null) {
            throw new IllegalArgumentException("Allowed root must not be null.");
        }
        this.allowedRoot = allowedRoot.toAbsolutePath().normalize();
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
            Path path = resolveSafeWritePath(filePath);
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), data);
            LOGGER.info("Saved data to %s (%s top-level keys)", filePath, data.size());
        } catch (SecurityException exception) {
            throw exception;
        } catch (IOException exception) {
            LOGGER.error("Failed to save data to %s: %s", filePath, exception.getMessage());
            throw new RuntimeException(exception);
        }
    }

    public Map<String, Object> loadFromFile(String filePath) {
        try {
            Path path = resolveSafeReadPath(filePath);
            Map<String, Object> data = mapper.readValue(path.toFile(), MAP_TYPE);
            LOGGER.info("Loaded data from %s (%s top-level keys)", filePath, data.size());
            return data;
        } catch (SecurityException exception) {
            throw exception;
        } catch (IOException exception) {
            LOGGER.error("Failed to load data from %s: %s", filePath, exception.getMessage());
            throw new RuntimeException(exception);
        }
    }

    public boolean exists(String filePath) {
        try {
            return Files.isRegularFile(resolveSafeReadPath(filePath));
        } catch (java.nio.file.NoSuchFileException exception) {
            return false;
        } catch (IOException exception) {
            return Files.exists(allowedRoot.resolve(filePath));
        }
    }

    public boolean existsWithinRoot(String filePath) {
        try {
            return Files.isRegularFile(resolveSafeReadPath(filePath));
        } catch (java.nio.file.NoSuchFileException exception) {
            return false;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to resolve file " + filePath, exception);
        }
    }

    public boolean delete(String filePath) {
        try {
            Path path = resolveSafeReadPath(filePath);
            Files.delete(path);
            LOGGER.info("Deleted file: %s", filePath);
            return true;
        } catch (java.nio.file.NoSuchFileException exception) {
            return false;
        } catch (SecurityException exception) {
            throw exception;
        } catch (IOException exception) {
            LOGGER.error("Failed to delete %s: %s", filePath, exception.getMessage());
            throw new RuntimeException(exception);
        }
    }

    private Path resolveSafeWritePath(String filePath) throws IOException {
        Files.createDirectories(allowedRoot);
        Path requestedPath = Path.of(filePath);
        Path targetPath = requestedPath.isAbsolute()
                ? requestedPath.toAbsolutePath().normalize()
                : allowedRoot.resolve(requestedPath).normalize();
        if (!targetPath.startsWith(allowedRoot)) {
            throw new SecurityException("Path is outside the allowed root: " + filePath);
        }

        Path parent = targetPath.getParent();
        Path fileName = targetPath.getFileName();
        if (parent == null || fileName == null) {
            throw new SecurityException("Invalid file path: " + filePath);
        }

        Path existingAncestor = parent;
        while (existingAncestor != null && !Files.exists(existingAncestor, LinkOption.NOFOLLOW_LINKS)) {
            existingAncestor = existingAncestor.getParent();
        }
        Path realRoot = allowedRoot.toRealPath();
        if (existingAncestor == null || !existingAncestor.toRealPath().startsWith(realRoot)) {
            throw new SecurityException("Path is outside the allowed root: " + filePath);
        }

        Files.createDirectories(parent);
        Path realParent = parent.toRealPath();
        if (!realParent.startsWith(realRoot)) {
            throw new SecurityException("Path is outside the allowed root: " + filePath);
        }

        Path safeTarget = realParent.resolve(fileName).normalize();
        if (Files.exists(safeTarget, LinkOption.NOFOLLOW_LINKS)) {
            Path realTarget = safeTarget.toRealPath();
            if (!realTarget.startsWith(realRoot)) {
                throw new SecurityException("Path is outside the allowed root: " + filePath);
            }
            return realTarget;
        }
        return safeTarget;
    }

    private Path resolveSafeReadPath(String filePath) throws IOException {
        Files.createDirectories(allowedRoot);
        Path requestedPath = Path.of(filePath);
        Path targetPath = requestedPath.isAbsolute()
                ? requestedPath.toAbsolutePath().normalize()
                : allowedRoot.resolve(requestedPath).normalize();
        if (!targetPath.startsWith(allowedRoot)) {
            throw new SecurityException("Path is outside the allowed root: " + filePath);
        }

        Path realTarget = targetPath.toRealPath();
        Path realRoot = allowedRoot.toRealPath();
        if (!realTarget.startsWith(realRoot)) {
            throw new SecurityException("Path is outside the allowed root: " + filePath);
        }
        return realTarget;
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
