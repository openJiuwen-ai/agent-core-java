/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.file_connector;

import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Mirrors Python's
 * {@code openjiuwen.extensions.context_evolver.core.file_connector.json_file_connector.JSONFileConnector}.
 * Generic connector for saving and loading JSON data to/from files.
 * 
 * @since 0.1.7
 */
public class JSONFileConnector {
    private static final Logger log = LoggerFactory.getLogger(JSONFileConnector.class);

    private final ObjectMapper objectMapper;
    private final int indent;
    private final boolean shouldEnsureAscii;
    private final Path allowedRoot;

    /**
     * JSONFileConnector using the current working directory as the allowed root.
     * 
     * @since 0.1.7
     */
    public JSONFileConnector() {
        this(Path.of(""), 2, false);
    }

    /**
     * JSONFileConnector using the current working directory as the allowed root.
     * 
     * @param indent indent
     * @param shouldEnsureAscii shouldEnsureAscii
     * @since 0.1.7
     */
    public JSONFileConnector(int indent, boolean shouldEnsureAscii) {
        this(Path.of(""), indent, shouldEnsureAscii);
    }

    /**
     * JSONFileConnector.
     *
     * @param allowedRoot allowed root directory for JSON file writes
     * @since 0.1.13
     */
    public JSONFileConnector(Path allowedRoot) {
        this(allowedRoot, 2, false);
    }

    /**
     * JSONFileConnector.
     *
     * @param allowedRoot allowed root directory for JSON file writes
     * @param indent indent
     * @param shouldEnsureAscii shouldEnsureAscii
     * @since 0.1.13
     */
    public JSONFileConnector(Path allowedRoot, int indent, boolean shouldEnsureAscii) {
        if (allowedRoot == null) {
            throw new IllegalArgumentException("Allowed root must not be null.");
        }
        this.allowedRoot = allowedRoot.toAbsolutePath().normalize();
        this.indent = indent;
        this.shouldEnsureAscii = shouldEnsureAscii;
        this.objectMapper = new ObjectMapper();
        if (shouldEnsureAscii) {
            this.objectMapper.getFactory().configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), true);
        }
    }

    /**
     * Save dictionary data to a JSON file.
     * 
     * @param filePath path to save the JSON file
     * @param data dictionary data to save
     * @throws SecurityException if the target path is outside the allowed root
     * @since 0.1.7
     */
    public void saveToFile(String filePath, Map<String, Object> data) {
        try {
            Path path = resolveSafeWritePath(filePath);

            String json = buildWriter().writeValueAsString(data);
            Files.writeString(path, json, StandardCharsets.UTF_8);

            log.info("Saved data to {} ({} top-level keys)", filePath, data.size());
        } catch (IOException e) {
            log.error("Failed to save data to {}: {}", filePath, e.getMessage());
            throw new RuntimeException("Failed to save data to " + filePath, e);
        }
    }

    private Path resolveSafeWritePath(String filePath) throws IOException {
        Files.createDirectories(allowedRoot);
        Path requestedPath = Paths.get(filePath);
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

    /**
     * loadFromFile.
     * 
     * @param filePath filePath
     * @return the result
     * @throws SecurityException if the source path is outside the allowed root
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadFromFile(String filePath) {
        try {
            Path path = resolveSafeReadPath(filePath);
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Map<String, Object> data = objectMapper.readValue(json, Map.class);

            log.info("Loaded data from {} ({} top-level keys)", filePath, data.size());
            return data;
        } catch (IOException e) {
            log.error("Failed to load data from {}: {}", filePath, e.getMessage());
            throw new RuntimeException("Failed to load data from " + filePath, e);
        }
    }

    private Path resolveSafeReadPath(String filePath) throws IOException {
        Files.createDirectories(allowedRoot);
        Path requestedPath = Paths.get(filePath);
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

    /**
     * Check whether a file exists within the allowed root.
     *
     * @param filePath relative or absolute file path
     * @return true when the real file exists within the allowed root
     * @since 0.1.13
     */
    public boolean existsWithinRoot(String filePath) {
        try {
            return Files.isRegularFile(resolveSafeReadPath(filePath));
        } catch (java.nio.file.NoSuchFileException e) {
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to resolve file " + filePath, e);
        }
    }

    /**
     * Check if a file exists.
     * 
     * @param filePath filePath
     * @return the result
     * @since 0.1.7
     */
    public static boolean isExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }

    /**
     * exists.
     * 
     * @param filePath filePath
     * @return the result
     * @since 0.1.7
     */
    public static boolean exists(String filePath) {
        return isExists(filePath);
    }

    /**
     * Delete a file if it exists.
     * 
     * @param filePath filePath
     * @return true if file was deleted, false if it didn't exist
     * @since 0.1.7
     */
    public static boolean delete(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.deleteIfExists(path)) {
                log.info("Deleted file: {}", filePath);
                return true;
            }
            return false;
        } catch (IOException e) {
            log.error("Failed to delete {}: {}", filePath, e.getMessage());
            throw new RuntimeException("Failed to delete " + filePath, e);
        }
    }

    /**
     * buildWriter.
     * 
     * @return the result
     * @since 0.1.7
     */
    private ObjectWriter buildWriter() {
        if (indent <= 0) {
            return objectMapper.writer();
        }

        String indentValue = " ".repeat(indent);
        DefaultIndenter indenter = new DefaultIndenter(indentValue, System.lineSeparator());
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentObjectsWith(indenter);
        prettyPrinter.indentArraysWith(indenter);
        return objectMapper.writer(prettyPrinter);
    }
}
