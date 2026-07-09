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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    private final boolean ensureAscii;

    /**
     * JSONFileConnector.
     * 
     * @since 0.1.7
     */
    public JSONFileConnector() {
        this(2, false);
    }

    /**
     * JSONFileConnector.
     * 
     * @param indent indent
     * @param ensureAscii ensureAscii
     * @since 0.1.7
     */
    public JSONFileConnector(int indent, boolean ensureAscii) {
        this.indent = indent;
        this.ensureAscii = ensureAscii;
        this.objectMapper = new ObjectMapper();
        if (ensureAscii) {
            this.objectMapper.getFactory().configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), true);
        }
    }

    /**
     * Save dictionary data to a JSON file.
     * 
     * @param filePath path to save the JSON file
     * @param data dictionary data to save
     * @since 0.1.7
     */
    public void saveToFile(String filePath, Map<String, Object> data) {
        try {
            Path path = Paths.get(filePath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            String json = buildWriter().writeValueAsString(data);
            Files.writeString(path, json, StandardCharsets.UTF_8);

            log.info("Saved data to {} ({} top-level keys)", filePath, data.size());
        } catch (IOException e) {
            log.error("Failed to save data to {}: {}", filePath, e.getMessage());
            throw new RuntimeException("Failed to save data to " + filePath, e);
        }
    }

    /**
     * loadFromFile.
     * 
     * @param filePath filePath
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadFromFile(String filePath) {
        try {
            String json = Files.readString(Paths.get(filePath), StandardCharsets.UTF_8);
            Map<String, Object> data = objectMapper.readValue(json, Map.class);

            log.info("Loaded data from {} ({} top-level keys)", filePath, data.size());
            return data;
        } catch (IOException e) {
            log.error("Failed to load data from {}: {}", filePath, e.getMessage());
            throw new RuntimeException("Failed to load data from " + filePath, e);
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
