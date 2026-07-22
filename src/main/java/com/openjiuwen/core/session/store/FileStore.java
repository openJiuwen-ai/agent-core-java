/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;
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
    private final TenantWorkspaceResolver workspaceResolver;

    public FileStore() {
        this(Path.of("session_store.json"));
    }

    public FileStore(Path storePath) {
        this.storePath = storePath.toAbsolutePath().normalize();
        this.workspaceResolver = null;
    }

    public FileStore(Path storePath, TenantWorkspaceResolver workspaceResolver) {
        this.storePath = storePath.toAbsolutePath().normalize();
        this.workspaceResolver = workspaceResolver;
    }

    private Path resolveStorePath() {
        if (workspaceResolver != null) {
            TenantContext ctx = TenantContextHolder.getCurrentTenant();
            if (ctx != null && ctx.isTenantAware()) {
                Path tenantWorkspace = workspaceResolver.resolveWorkspaceRoot(ctx);
                return tenantWorkspace.resolve(storePath.getFileName()).toAbsolutePath().normalize();
            }
        }
        return storePath;
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
        Path resolvedPath = resolveStorePath();
        if (!Files.exists(resolvedPath)) {
            return new LinkedHashMap<>();
        }
        try {
            String raw = Files.readString(resolvedPath);
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
            Path resolvedPath = resolveStorePath();
            if (resolvedPath.getParent() != null) {
                Files.createDirectories(resolvedPath.getParent());
            }
            Files.writeString(resolvedPath, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
