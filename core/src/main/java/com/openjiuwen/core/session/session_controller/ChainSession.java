/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/**
 * Chained session data container.
 *
 * <p>Mirrors Python's {@code ChainSession} in
 * {@code openjiuwen/core/session/session_controller/chain_session.py}.</p>
 *
 * @param <T> data type exposed by the backing data container
 */
public class ChainSession<T> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final String agentId;
    private final SessionScope sessionScope;
    private final String sessionId;
    private DataContainer dataContainer;
    private final Path sessionDir;
    private String dataContainerType = DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE;
    private final Map<DownstreamKey, SharingPolicy> downstreamPolicies = new LinkedHashMap<>();
    private double createdAt;
    private double updatedAt;
    private int version = 1;
    private boolean active;

    public ChainSession(String agentId,
                        SessionScope sessionScope,
                        String sessionId,
                        DataContainer dataContainer,
                        Path sessionDir) {
        this.agentId = Objects.requireNonNull(agentId, "agentId must not be null");
        this.sessionScope = Objects.requireNonNull(sessionScope, "sessionScope must not be null");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.dataContainer = Objects.requireNonNull(dataContainer, "dataContainer must not be null");
        this.sessionDir = Objects.requireNonNull(sessionDir, "sessionDir must not be null");
    }

    public synchronized boolean load() {
        try {
            Loggers.SESSION.debug("Loading session {} from {}", sessionId, sessionDir);
            Path stateFile = SessionPaths.stateFile(sessionDir);
            if (Files.exists(stateFile)) {
                Map<String, Object> stateData = readMap(stateFile);
                Object metaValue = stateData.get("meta");
                if (metaValue instanceof Map<?, ?> metaMap) {
                    loadMeta(toStringKeyMap(metaMap));
                }

                if (stateData.containsKey("data")) {
                    dataContainer = DataContainerFactory.load(dataContainerType, agentId, sessionId,
                                    stateData.get("data"))
                            .toCompletableFuture()
                            .join();
                }
            }

            loadDownstreamPolicies();
            Loggers.SESSION.info("Session {} loaded successfully, downstreams={}, active={}",
                    sessionId, downstreamPolicies.size(), active);
            return true;
        } catch (RuntimeException | IOException exception) {
            Loggers.SESSION.exception("Error loading session {}", exception, sessionId);
            return false;
        }
    }

    public synchronized boolean flush() {
        try {
            Loggers.SESSION.debug("Flushing session {} to disk", sessionId);
            updatedAt = utcSeconds();

            LinkedHashMap<String, Object> stateData = new LinkedHashMap<>();
            stateData.put("meta", metaMap());
            stateData.put("data", dumpData());

            Files.createDirectories(sessionDir);
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(SessionPaths.stateFile(sessionDir).toFile(), stateData);
            flushDownstreamLinks();

            Loggers.SESSION.info("Session {} flushed successfully, version={}, downstreams={}",
                    sessionId, version, downstreamPolicies.size());
            return true;
        } catch (RuntimeException | IOException exception) {
            Loggers.SESSION.exception("Error flushing session {}", exception, sessionId);
            return false;
        }
    }

    public synchronized void addDownstream(String targetAgent, String targetSession, SharingPolicy policy) {
        DownstreamKey key = new DownstreamKey(targetAgent, targetSession);
        downstreamPolicies.put(key, policy == null ? new SharingPolicy() : policy);
        updatedAt = utcSeconds();
        Loggers.SESSION.debug("Added downstream: {} -> {}/{}, policy={}",
                sessionId, targetAgent, targetSession, downstreamPolicies.get(key).getPermission());
    }

    public synchronized void removeDownstream(String targetAgent, String targetSession) {
        DownstreamKey key = new DownstreamKey(targetAgent, targetSession);
        if (downstreamPolicies.remove(key) != null) {
            updatedAt = utcSeconds();
            Loggers.SESSION.debug("Removed downstream: {} -> {}/{}", sessionId, targetAgent, targetSession);
        }
    }

    public synchronized boolean hasDownstream(String targetAgent, String targetSession) {
        return downstreamPolicies.containsKey(new DownstreamKey(targetAgent, targetSession));
    }

    public synchronized Map<DownstreamKey, SharingPolicy> getDownstreams() {
        return new LinkedHashMap<>(downstreamPolicies);
    }

    public synchronized Optional<SharingPolicy> getDownstreamPolicy(String targetAgent, String targetSession) {
        return Optional.ofNullable(downstreamPolicies.get(new DownstreamKey(targetAgent, targetSession)));
    }

    public synchronized void removeAllDownstreams() {
        downstreamPolicies.clear();
        updatedAt = utcSeconds();
        Loggers.SESSION.debug("Cleared all downstreams for session {}", sessionId);
    }

    @SuppressWarnings("unchecked")
    public T getData() {
        return (T) dataContainer.get(null);
    }

    public synchronized boolean updateData(Map<String, Object> data) {
        try {
            boolean success = dataContainer.update(data);
            if (success) {
                version += 1;
                updatedAt = utcSeconds();
            }
            return success;
        } catch (RuntimeException exception) {
            Loggers.SESSION.exception("Error updating session data {}", exception, sessionId);
            return false;
        }
    }

    public synchronized boolean canSee(String targetAgent, String targetSession) {
        if (Objects.equals(targetAgent, agentId) && Objects.equals(targetSession, sessionId)) {
            return true;
        }
        return hasDownstream(targetAgent, targetSession);
    }

    public synchronized SessionMeta toSessionMeta() {
        return new SessionMeta(sessionId, createdAt, updatedAt, version, active, dataContainerType);
    }

    public synchronized void updateFromMeta(SessionMeta meta) {
        Objects.requireNonNull(meta, "meta must not be null");
        createdAt = meta.getCreatedAt();
        updatedAt = meta.getUpdatedAt();
        version = meta.getVersion();
        active = meta.isActive();
        if (meta.getDataContainerType() != null && !meta.getDataContainerType().isBlank()) {
            dataContainerType = meta.getDataContainerType();
        }
    }

    public SessionScopeKey getSessionKey() {
        return new SessionScopeKey(agentId, sessionScope);
    }

    public String getAgentId() {
        return agentId;
    }

    public SessionScope getSessionScope() {
        return sessionScope;
    }

    public String getSessionId() {
        return sessionId;
    }

    public DataContainer getDataContainer() {
        return dataContainer;
    }

    public Path getSessionDir() {
        return sessionDir;
    }

    public double getCreatedAt() {
        return createdAt;
    }

    public double getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public boolean isActive() {
        return active;
    }

    public synchronized void setActive(boolean active) {
        this.active = active;
        if (active) {
            updatedAt = utcSeconds();
        }
    }

    public String getDataContainerType() {
        return dataContainerType;
    }

    public synchronized void setDataContainerType(String dataContainerType) {
        this.dataContainerType = dataContainerType == null || dataContainerType.isBlank()
                ? DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE
                : dataContainerType;
    }

    private void loadMeta(Map<String, Object> meta) {
        createdAt = doubleValue(meta.getOrDefault("created_at", 0.0D));
        updatedAt = doubleValue(meta.getOrDefault("updated_at", 0.0D));
        version = intValue(meta.getOrDefault("version", 1));
        active = booleanValue(meta.getOrDefault("is_active", false));
        dataContainerType = stringValue(meta.getOrDefault(
                "data_container_type",
                DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE
        ));
    }

    private void loadDownstreamPolicies() throws IOException {
        downstreamPolicies.clear();
        Path downstreamsDir = SessionPaths.downstreamsDir(sessionDir);
        if (!Files.exists(downstreamsDir)) {
            return;
        }
        try (Stream<Path> links = Files.list(downstreamsDir)) {
            for (Path linkFile : links.filter(path -> path.getFileName().toString().endsWith(".link")).toList()) {
                loadDownstreamLink(linkFile);
            }
        }
    }

    private void loadDownstreamLink(Path linkFile) {
        try {
            Map<String, Object> linkData = readMap(linkFile);
            if (booleanValue(linkData.getOrDefault("removed", false))) {
                Files.deleteIfExists(linkFile);
                return;
            }

            String fileStem = stripLinkSuffix(linkFile.getFileName().toString());
            int separator = fileStem.indexOf('_');
            if (separator < 0) {
                return;
            }
            String targetAgent = fileStem.substring(0, separator);
            String targetSession = fileStem.substring(separator + 1);
            Map<String, Object> policyData = toStringKeyMap((Map<?, ?>) linkData.getOrDefault("permission", Map.of()));
            Permission permission = permissionFromLevel(intValue(policyData.getOrDefault("level", Permission.READ.getValue())));
            Object fieldScopesValue = policyData.get("field_scopes");
            Set<String> fieldScopes = fieldScopesValue == null ? null : toStringSet(fieldScopesValue);
            downstreamPolicies.put(
                    new DownstreamKey(targetAgent, targetSession),
                    new SharingPolicy(permission, fieldScopes)
            );
            Loggers.SESSION.debug("Loaded downstream link: {} -> {}/{}", sessionId, targetAgent, targetSession);
        } catch (RuntimeException | IOException exception) {
            Loggers.SESSION.error("Error loading downstream link {}: {}", linkFile, exception.getMessage());
        }
    }

    private void flushDownstreamLinks() throws IOException {
        Path downstreamsDir = SessionPaths.downstreamsDir(sessionDir);
        Files.createDirectories(downstreamsDir);

        Set<Path> existingLinks = existingLinkFiles(downstreamsDir);
        Set<Path> currentLinks = new LinkedHashSet<>();
        for (Map.Entry<DownstreamKey, SharingPolicy> entry : downstreamPolicies.entrySet()) {
            DownstreamKey key = entry.getKey();
            SharingPolicy policy = entry.getValue();
            Path linkFile = SessionPaths.linkFile(sessionDir, key.targetAgent(), key.targetSession());
            currentLinks.add(linkFile);
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(linkFile.toFile(), linkData(policy));
        }

        existingLinks.removeAll(currentLinks);
        for (Path staleLink : existingLinks) {
            markAndDeleteLink(staleLink);
        }
    }

    private Set<Path> existingLinkFiles(Path downstreamsDir) throws IOException {
        try (Stream<Path> paths = Files.list(downstreamsDir)) {
            LinkedHashSet<Path> links = new LinkedHashSet<>();
            paths.filter(path -> path.getFileName().toString().endsWith(".link"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(links::add);
            return links;
        }
    }

    private void markAndDeleteLink(Path linkFile) {
        try {
            Map<String, Object> linkData = Files.exists(linkFile) ? readMap(linkFile) : new LinkedHashMap<>();
            linkData.put("removed", true);
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(linkFile.toFile(), linkData);
        } catch (IOException | RuntimeException ignored) {
            // Python ignores JSON decode and OS errors before attempting unlink.
        }
        try {
            Files.deleteIfExists(linkFile);
        } catch (IOException ignored) {
            // Preserve Python's best-effort cleanup behavior.
        }
    }

    private Map<String, Object> metaMap() {
        LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
        meta.put("created_at", createdAt);
        meta.put("updated_at", updatedAt);
        meta.put("version", version);
        meta.put("is_active", active);
        meta.put("data_container_type", dataContainerType);
        return meta;
    }

    private Map<String, Object> linkData(SharingPolicy policy) {
        LinkedHashMap<String, Object> permissionData = new LinkedHashMap<>();
        permissionData.put("level", policy.getPermission().getValue());
        permissionData.put("field_scopes", policy.getFieldScopes() == null ? null : Set.copyOf(policy.getFieldScopes()));

        LinkedHashMap<String, Object> linkData = new LinkedHashMap<>();
        linkData.put("permission", permissionData);
        linkData.put("created_at", updatedAt);
        return linkData;
    }

    private Object dumpData() {
        try {
            return dataContainer.dump().toCompletableFuture().join();
        } catch (CompletionException exception) {
            throw exception;
        }
    }

    private static Map<String, Object> readMap(Path path) throws IOException {
        return OBJECT_MAPPER.readValue(path.toFile(), new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> map) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static Set<String> toStringSet(Object value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        result.add(String.valueOf(value));
        return result;
    }

    private static Permission permissionFromLevel(int level) {
        for (Permission permission : Permission.values()) {
            if (permission.getValue() == level) {
                return permission;
            }
        }
        throw new IllegalArgumentException("Unknown permission level: " + level);
    }

    private static String stripLinkSuffix(String fileName) {
        return fileName.endsWith(".link") ? fileName.substring(0, fileName.length() - ".link".length()) : fileName;
    }

    private static double utcSeconds() {
        return Instant.now().toEpochMilli() / 1000.0D;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return value == null ? 0.0D : Double.parseDouble(String.valueOf(value));
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * Downstream relationship key.
     *
     * <p>Mirrors Python's {@code tuple[str, str]} downstream key in
     * {@code openjiuwen/core/session/session_controller/chain_session.py}.</p>
     *
     * @param targetAgent target agent id
     * @param targetSession target session id
     */
    public record DownstreamKey(String targetAgent, String targetSession) {
        public DownstreamKey {
            if (targetAgent == null || targetAgent.isBlank()) {
                throw new IllegalArgumentException("targetAgent must not be blank");
            }
            if (targetSession == null || targetSession.isBlank()) {
                throw new IllegalArgumentException("targetSession must not be blank");
            }
        }
    }
}
