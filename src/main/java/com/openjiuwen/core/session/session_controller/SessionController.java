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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Session controller for a single agent.
 *
 * <p>Mirrors Python's {@code SessionController} in
 * {@code openjiuwen/core/session/session_controller/session_controller.py}.</p>
 */
public class SessionController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final String agentId;
    private final Path rootPath;
    private final Path basePath;
    private final String dataContainerType;
    private final Map<String, ChainSession<?>> sessionCache = new LinkedHashMap<>();
    private final Map<SessionScope, ScopeSessionsMeta> metaMap = new LinkedHashMap<>();

    public SessionController(String agentId, Path basePath) {
        this(agentId, basePath, DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE);
    }

    public SessionController(String agentId, Path basePath, String dataContainerType) {
        this.agentId = Objects.requireNonNull(agentId, "agentId must not be null");
        this.rootPath = Objects.requireNonNull(basePath, "basePath must not be null");
        this.basePath = SessionPaths.sessionsDir(basePath, agentId);
        this.dataContainerType = dataContainerType == null || dataContainerType.isBlank()
                ? DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE
                : dataContainerType;
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create session directory: " + this.basePath, exception);
        }
    }

    public synchronized boolean flush() {
        try {
            Loggers.SESSION.debug("Flushing all sessions for agent {}, cache_size={}", agentId, sessionCache.size());
            for (ChainSession<?> session : sessionCache.values()) {
                if (!session.flush()) {
                    Loggers.SESSION.error("Error flushing session for agent {}", agentId);
                    return false;
                }
            }
            writeMetaFile();
            Loggers.SESSION.info("Flushed all sessions for agent {}", agentId);
            return true;
        } catch (RuntimeException exception) {
            Loggers.SESSION.exception("Error flushing controller for agent {}", exception, agentId);
            return false;
        }
    }

    public synchronized boolean flushSession(String sessionId) {
        try {
            ChainSession<?> session = sessionCache.get(sessionId);
            if (session == null) {
                return true;
            }
            if (!session.flush()) {
                Loggers.SESSION.error("Error flushing session {}: false", sessionId);
                return false;
            }
            writeMetaFile();
            Loggers.SESSION.debug("Flushed session {} for agent {}", sessionId, agentId);
            return true;
        } catch (RuntimeException exception) {
            Loggers.SESSION.exception("Error flushing session {} for agent {}", exception, sessionId, agentId);
            return false;
        }
    }

    public synchronized boolean flushScope(SessionScope sessionScope) {
        try {
            if (!metaMap.containsKey(sessionScope)) {
                return true;
            }
            for (ChainSession<?> session : sessionCache.values()) {
                if (session.getSessionScope().equals(sessionScope) && !session.flush()) {
                    Loggers.SESSION.error("Error flushing session in scope {}: false", sessionScope);
                    return false;
                }
            }
            writeMetaFile();
            Loggers.SESSION.debug("Flushed scope {} for agent {}", sessionScope, agentId);
            return true;
        } catch (RuntimeException exception) {
            Loggers.SESSION.exception("Error flushing scope for agent {}", exception, agentId);
            return false;
        }
    }

    public synchronized boolean load() {
        return load(true);
    }

    public synchronized boolean load(boolean loadActiveOnly) {
        try {
            Loggers.SESSION.debug("Loading sessions for agent {}, load_active_only={}", agentId, loadActiveOnly);
            Path metaFile = SessionPaths.metaFile(rootPath, agentId);
            if (!Files.exists(metaFile)) {
                return true;
            }

            Map<String, Object> metaData = readMap(metaFile);
            metaMap.clear();
            for (Map.Entry<String, Object> entry : metaData.entrySet()) {
                try {
                    SessionScopeKey sessionScopeKey = SessionScopeKey.fromString(entry.getKey());
                    SessionScope sessionScope = sessionScopeKey.sessionScope();
                    ScopeSessionsMeta scopeMeta = ScopeSessionsMeta.fromMap(toStringObjectMap(entry.getValue()));
                    metaMap.put(sessionScope, scopeMeta);

                    if (loadActiveOnly) {
                        if (scopeMeta.getActiveSession() != null && !scopeMeta.getActiveSession().isBlank()) {
                            loadSession(sessionScope, scopeMeta.getActiveSession(), Map.of());
                        }
                    } else {
                        for (SessionMeta sessionMeta : scopeMeta.getSessions()) {
                            loadSession(sessionScope, sessionMeta.getSessionId(), Map.of());
                        }
                    }
                } catch (RuntimeException exception) {
                    Loggers.SESSION.error("Error loading scope {}: {}", entry.getKey(), exception.getMessage());
                }
            }
            Loggers.SESSION.info("Loaded sessions for agent {}, scopes={}, cache_size={}",
                    agentId, metaMap.size(), sessionCache.size());
            return true;
        } catch (RuntimeException | IOException exception) {
            Loggers.SESSION.exception("Error loading controller for agent {}", exception, agentId);
            return false;
        }
    }

    public synchronized boolean loadScope(SessionScope sessionScope) {
        return loadScope(sessionScope, true);
    }

    public synchronized boolean loadScope(SessionScope sessionScope, boolean loadActiveOnly) {
        try {
            Path metaFile = SessionPaths.metaFile(rootPath, agentId);
            if (!Files.exists(metaFile)) {
                return true;
            }

            Map<String, Object> metaData = readMap(metaFile);
            for (Map.Entry<String, Object> entry : metaData.entrySet()) {
                try {
                    SessionScopeKey sessionScopeKey = SessionScopeKey.fromString(entry.getKey());
                    if (!sessionScopeKey.sessionScope().equals(sessionScope)) {
                        continue;
                    }
                    ScopeSessionsMeta scopeMeta = ScopeSessionsMeta.fromMap(toStringObjectMap(entry.getValue()));
                    metaMap.put(sessionScope, scopeMeta);
                    if (loadActiveOnly) {
                        if (scopeMeta.getActiveSession() != null && !scopeMeta.getActiveSession().isBlank()) {
                            loadSession(sessionScope, scopeMeta.getActiveSession(), Map.of());
                        }
                    } else {
                        for (SessionMeta sessionMeta : scopeMeta.getSessions()) {
                            loadSession(sessionScope, sessionMeta.getSessionId(), Map.of());
                        }
                    }
                    break;
                } catch (RuntimeException exception) {
                    Loggers.SESSION.error("Error loading scope {}: {}", entry.getKey(), exception.getMessage());
                }
            }
            Loggers.SESSION.debug("Loaded scope {} for agent {}", sessionScope, agentId);
            return true;
        } catch (RuntimeException | IOException exception) {
            Loggers.SESSION.exception("Error loading scope for agent {}", exception, agentId);
            return false;
        }
    }

    public synchronized CreateIfNotExistsResult createIfNotExists(SessionScope sessionScope, String sessionId) {
        return createIfNotExists(sessionScope, sessionId, Map.of());
    }

    public synchronized CreateIfNotExistsResult createIfNotExists(SessionScope sessionScope,
                                                                 String sessionId,
                                                                 Map<String, Object> params) {
        try {
            if (sessionCache.containsKey(sessionId)) {
                throw new IllegalArgumentException("Session ID " + sessionId + " already exists");
            }
            for (ScopeSessionsMeta existingScopeMeta : metaMap.values()) {
                if (existingScopeMeta.getSession(sessionId) != null) {
                    throw new IllegalArgumentException("Session ID " + sessionId + " already exists");
                }
            }

            ScopeSessionsMeta scopeMeta = metaMap.computeIfAbsent(sessionScope, scope -> new ScopeSessionsMeta(
                    new SessionScopeKey(agentId, scope).toString(),
                    null,
                    List.of()
            ));

            SessionMeta activeSession = scopeMeta.getActiveSessionMeta();
            if (activeSession != null) {
                if (!sessionCache.containsKey(activeSession.getSessionId())) {
                    loadSession(sessionScope, activeSession.getSessionId(), Map.of());
                }
                return new CreateIfNotExistsResult(false, sessionCache.get(activeSession.getSessionId()));
            }

            Path sessionDir = SessionPaths.sessionDir(rootPath, agentId, sessionId);
            Files.createDirectories(sessionDir);
            Map<String, Object> safeParams = params == null ? Map.of() : new LinkedHashMap<>(params);
            DataContainer dataContainer = DataContainerFactory.create(dataContainerType, safeParams);
            ChainSession<?> session = new ChainSession<>(agentId, sessionScope, sessionId, dataContainer, sessionDir);
            SessionMeta sessionMeta = SessionMeta.createNew(sessionId, 1, dataContainerType);
            session.updateFromMeta(sessionMeta);

            scopeMeta.addSession(sessionMeta);
            sessionCache.put(sessionId, session);
            session.flush();
            writeMetaFile();

            Loggers.SESSION.info("Created new session {} for agent {}, scope={}, container_type={}",
                    sessionId, agentId, sessionScope, dataContainerType);
            return new CreateIfNotExistsResult(true, session);
        } catch (IOException exception) {
            Loggers.SESSION.exception("Error creating session {} for agent {}", exception, sessionId, agentId);
            throw new IllegalStateException("Unable to create session " + sessionId, exception);
        } catch (RuntimeException exception) {
            Loggers.SESSION.exception("Error creating session {} for agent {}", exception, sessionId, agentId);
            throw exception;
        }
    }

    public synchronized Optional<ChainSession<?>> getScopeActiveSession(SessionScope sessionScope) {
        ScopeSessionsMeta scopeMeta = metaMap.get(sessionScope);
        if (scopeMeta == null || scopeMeta.getActiveSession() == null || scopeMeta.getActiveSession().isBlank()) {
            return Optional.empty();
        }
        String activeSessionId = scopeMeta.getActiveSession();
        if (!sessionCache.containsKey(activeSessionId) && !loadSession(sessionScope, activeSessionId, Map.of())) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionCache.get(activeSessionId));
    }

    public synchronized List<ChainSession<?>> getScopeSessions(SessionScope sessionScope) {
        ScopeSessionsMeta scopeMeta = metaMap.get(sessionScope);
        if (scopeMeta == null) {
            return List.of();
        }
        Loggers.SESSION.debug("Getting sessions for agent {}, scope={}", agentId, sessionScope);
        List<ChainSession<?>> sessions = new ArrayList<>();
        for (SessionMeta sessionMeta : scopeMeta.getSessions()) {
            ChainSession<?> session = sessionCache.get(sessionMeta.getSessionId());
            if (session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    public synchronized void activateSession(String sessionId) {
        ChainSession<?> session = null;
        SessionScope targetSessionScope = null;
        for (ChainSession<?> cachedSession : sessionCache.values()) {
            if (cachedSession.getSessionId().equals(sessionId)) {
                session = cachedSession;
                targetSessionScope = cachedSession.getSessionScope();
                break;
            }
        }

        if (session == null) {
            for (Map.Entry<SessionScope, ScopeSessionsMeta> entry : metaMap.entrySet()) {
                for (SessionMeta sessionMeta : entry.getValue().getSessions()) {
                    if (sessionMeta.getSessionId().equals(sessionId)) {
                        targetSessionScope = entry.getKey();
                        loadSession(entry.getKey(), sessionId, Map.of());
                        session = sessionCache.get(sessionId);
                        break;
                    }
                }
                if (session != null) {
                    break;
                }
            }
        }

        if (session == null || targetSessionScope == null) {
            throw new NoSuchElementException("Session " + sessionId + " not found");
        }

        Loggers.SESSION.debug("Activating session {} for agent {}", sessionId, agentId);
        ScopeSessionsMeta scopeMeta = metaMap.get(targetSessionScope);
        if (scopeMeta != null && scopeMeta.activateSession(sessionId)) {
            session.setActive(true);
            session.flush();
            writeMetaFile();
            Loggers.SESSION.info("Session {} activated for agent {}", sessionId, agentId);
        }
    }

    public synchronized ScopeSessionsMeta getScopeMeta(SessionScope sessionScope) {
        ScopeSessionsMeta scopeMeta = metaMap.get(sessionScope);
        if (scopeMeta != null) {
            return scopeMeta;
        }
        return new ScopeSessionsMeta(new SessionScopeKey(agentId, sessionScope).toString(), null, List.of());
    }

    public synchronized Map<SessionScope, ScopeSessionsMeta> listMetas() {
        return new LinkedHashMap<>(metaMap);
    }

    public synchronized List<ScopeCleanupResult> cleanupScopeInactiveSessions(SessionScope sessionScope) {
        ScopeSessionsMeta scopeMeta = metaMap.get(sessionScope);
        if (scopeMeta == null) {
            throw new IllegalArgumentException("Session scope " + sessionScope + " not found");
        }
        Loggers.SESSION.debug("Cleaning up inactive sessions for agent {}, scope={}", agentId, sessionScope);
        List<SessionMeta> cleanedSessions = new ArrayList<>();
        for (SessionMeta sessionMeta : scopeMeta.getSessions()) {
            if (!sessionMeta.isActive()) {
                sessionCache.remove(sessionMeta.getSessionId());
                deleteRecursively(SessionPaths.sessionDir(rootPath, agentId, sessionMeta.getSessionId()));
                SessionMeta removed = scopeMeta.removeSession(sessionMeta.getSessionId());
                if (removed != null) {
                    cleanedSessions.add(removed);
                }
            }
        }
        if (!cleanedSessions.isEmpty()) {
            writeMetaFile();
        }
        Loggers.SESSION.info("Cleaned up {} inactive sessions for agent {}, scope={}",
                cleanedSessions.size(), agentId, sessionScope);
        return List.of(new ScopeCleanupResult(sessionScope, List.copyOf(cleanedSessions)));
    }

    public synchronized List<RemovedSession> removeSession(String sessionId) {
        return removeSession(sessionId, null);
    }

    public synchronized List<RemovedSession> removeSession(String sessionId, SessionScope sessionScope) {
        List<RemovedSession> removedSessions = new ArrayList<>();
        Loggers.SESSION.debug("Removing session {} for agent {}, scope={}", sessionId, agentId, sessionScope);

        List<SessionScope> scopesToSearch = sessionScope == null
                ? new ArrayList<>(metaMap.keySet())
                : List.of(sessionScope);
        for (SessionScope scope : scopesToSearch) {
            ScopeSessionsMeta scopeMeta = metaMap.get(scope);
            if (scopeMeta == null) {
                continue;
            }
            SessionMeta sessionMeta = scopeMeta.getSession(sessionId);
            if (sessionMeta == null) {
                continue;
            }
            sessionCache.remove(sessionId);
            deleteRecursively(SessionPaths.sessionDir(rootPath, agentId, sessionId));
            SessionMeta removed = scopeMeta.removeSession(sessionId);
            if (removed != null) {
                removedSessions.add(new RemovedSession(scope, removed));
            }
        }

        if (!removedSessions.isEmpty()) {
            writeMetaFile();
            Loggers.SESSION.info("Removed {} session(s) for agent {}, session_id={}",
                    removedSessions.size(), agentId, sessionId);
        }
        return List.copyOf(removedSessions);
    }

    public synchronized List<SessionMeta> removeScopeSessions(SessionScope sessionScope) {
        ScopeSessionsMeta scopeMeta = metaMap.get(sessionScope);
        if (scopeMeta == null) {
            return List.of();
        }

        List<SessionMeta> removedSessions = new ArrayList<>();
        for (SessionMeta sessionMeta : scopeMeta.getSessions()) {
            String sessionId = sessionMeta.getSessionId();
            sessionCache.remove(sessionId);
            deleteRecursively(SessionPaths.sessionDir(rootPath, agentId, sessionId));
            removedSessions.add(sessionMeta);
        }

        scopeMeta.setSessions(List.of());
        scopeMeta.setActiveSession(null);
        metaMap.remove(sessionScope);
        writeMetaFile();
        Loggers.SESSION.info("Removed all sessions for agent {}, scope={}, count={}",
                agentId, sessionScope, removedSessions.size());
        return List.copyOf(removedSessions);
    }

    public synchronized void removeAll() {
        Loggers.SESSION.debug("Removing all session data for agent {}", agentId);
        sessionCache.clear();
        metaMap.clear();
        deleteRecursively(basePath);
        Loggers.SESSION.info("Removed all session data for agent {}", agentId);
    }

    private boolean loadSession(SessionScope sessionScope, String sessionId, Map<String, Object> params) {
        try {
            if (sessionCache.containsKey(sessionId)) {
                return true;
            }

            String resolvedContainerType = dataContainerType;
            ScopeSessionsMeta scopeMeta = metaMap.get(sessionScope);
            if (scopeMeta != null) {
                SessionMeta sessionMeta = scopeMeta.getSession(sessionId);
                if (sessionMeta != null
                        && sessionMeta.getDataContainerType() != null
                        && !sessionMeta.getDataContainerType().isBlank()) {
                    resolvedContainerType = sessionMeta.getDataContainerType();
                }
            }

            DataContainer dataContainer = params == null || params.isEmpty()
                    ? DataContainerFactory.load(resolvedContainerType, agentId, sessionId, null).toCompletableFuture().join()
                    : DataContainerFactory.create(resolvedContainerType, params);
            Path sessionDir = SessionPaths.sessionDir(rootPath, agentId, sessionId);
            ChainSession<?> session = new ChainSession<>(agentId, sessionScope, sessionId, dataContainer, sessionDir);
            sessionCache.put(sessionId, session);
            Loggers.SESSION.debug("Loaded session {} for agent {}, container_type={}",
                    sessionId, agentId, resolvedContainerType);
            return true;
        } catch (RuntimeException exception) {
            Loggers.SESSION.exception("Error loading session {}", exception, sessionId);
            throw exception;
        }
    }

    private boolean writeMetaFile() {
        try {
            LinkedHashMap<String, Object> metaData = new LinkedHashMap<>();
            for (Map.Entry<SessionScope, ScopeSessionsMeta> entry : metaMap.entrySet()) {
                SessionScopeKey sessionScopeKey = new SessionScopeKey(agentId, entry.getKey());
                metaData.put(sessionScopeKey.toString(), entry.getValue().toMap());
            }
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(SessionPaths.metaFile(rootPath, agentId).toFile(), metaData);
            return true;
        } catch (RuntimeException | IOException exception) {
            Loggers.SESSION.exception("Error writing meta file for agent {}", exception, agentId);
            return false;
        }
    }

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted((left, right) -> right.compareTo(left)).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException ignored) {
                    // Python uses shutil.rmtree(..., ignore_errors=True).
                }
            });
        } catch (IOException ignored) {
            // Python ignores delete failures in cleanup paths.
        }
    }

    private static Map<String, Object> readMap(Path path) throws IOException {
        return OBJECT_MAPPER.readValue(path.toFile(), new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    public String getAgentId() {
        return agentId;
    }

    public Path getRootPath() {
        return rootPath;
    }

    public Path getBasePath() {
        return basePath;
    }

    public String getDataContainerType() {
        return dataContainerType;
    }

    /**
     * Result tuple for session creation.
     *
     * <p>Mirrors Python's {@code tuple[bool, ChainSession]} returned by
     * {@code SessionController.create_if_not_exists} in
     * {@code openjiuwen/core/session/session_controller/session_controller.py}.</p>
     *
     * @param created whether a new session was created
     * @param session session object returned to the caller
     */
    public record CreateIfNotExistsResult(boolean created, ChainSession<?> session) {
    }

    /**
     * Result tuple for inactive-scope cleanup.
     *
     * <p>Mirrors Python's {@code tuple[SessionScope, list[SessionMeta]]} in
     * {@code openjiuwen/core/session/session_controller/session_controller.py}.</p>
     *
     * @param sessionScope cleaned session scope
     * @param sessions metadata removed from that scope
     */
    public record ScopeCleanupResult(SessionScope sessionScope, List<SessionMeta> sessions) {
    }

    /**
     * Result tuple for a removed session.
     *
     * <p>Mirrors Python's {@code tuple[SessionScope, SessionMeta]} in
     * {@code openjiuwen/core/session/session_controller/session_controller.py}.</p>
     *
     * @param sessionScope scope that contained the removed session
     * @param session metadata removed from that scope
     */
    public record RemovedSession(SessionScope sessionScope, SessionMeta session) {
    }
}
