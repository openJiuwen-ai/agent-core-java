/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.session.utils.SessionUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON-backed file store with the same read/merge semantics as {@link MemoryStore}.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.store.FileStore}.
 * 
 * @since 0.1.7
 */
public class FileStore extends Store {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path storePath;

    /**
     * FileStore.
     * 
     * @since 0.1.7
     */
    public FileStore() {
        this(Path.of("session_store.json"));
    }

    /**
     * FileStore.
     * 
     * @param storePath storePath
     * @since 0.1.7
     */
    public FileStore(Path storePath) {
        this.storePath = storePath.toAbsolutePath().normalize();
    }

    /**
     * read.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    @Override
    public synchronized Object read(Object key) {
        return SessionUtils.getBySchema(key, loadData());
    }

    /**
     * write.
     * 
     * @param value value
     * @since 0.1.7
     */
    @Override
    public synchronized void write(Map<String, Object> value) {
        Map<String, Object> data = loadData();
        SessionUtils.updateDict(value, data);
        persist(data);
    }

    /**
     * getStorePath.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Path getStorePath() {
        return storePath;
    }

    /**
     * getDataSnapshot.
     * 
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> getDataSnapshot() {
        return new LinkedHashMap<>(loadData());
    }

    /**
     * loadData.
     * 
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> loadData() {
        if (!Files.exists(storePath)) {
            return new LinkedHashMap<>();
        }
        try {
            String raw = Files.readString(storePath);
            if (raw.isBlank()) {
                return new LinkedHashMap<>();
            }
            return MAPPER.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * persist.
     * 
     * @param data data
     * @since 0.1.7
     */
    private void persist(Map<String, Object> data) {
        try {
            if (storePath.getParent() != null) {
                Files.createDirectories(storePath.getParent());
            }
            Files.writeString(storePath, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
