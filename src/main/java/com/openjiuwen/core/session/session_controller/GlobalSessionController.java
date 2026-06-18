/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.callback.AgentTeamEvents;
import com.openjiuwen.core.runner.callback.CallbackUtils;
import com.openjiuwen.core.runner.callback.SessionEvents;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.single_agent.schema.AgentCard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Process-global session controller facade.
 *
 * <p>Mirrors Python's {@code GlobalSessionController} in
 * {@code openjiuwen/core/session/session_controller/global_controller.py}.</p>
 */
public final class GlobalSessionController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String DEFAULT_BASE_PATH = "./agents";
    private static volatile GlobalSessionController instance;

    private Path basePath = Path.of(DEFAULT_BASE_PATH);
    private final Map<String, SessionController> controllers = new LinkedHashMap<>();
    private String dataContainerType = DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE;

    private GlobalSessionController() {
        registerTeamEventCallbacks();
    }

    public static GlobalSessionController getGlobalSessionController() {
        GlobalSessionController current = instance;
        if (current != null) {
            return current;
        }
        synchronized (GlobalSessionController.class) {
            if (instance == null) {
                instance = new GlobalSessionController();
            }
            return instance;
        }
    }

    public static GlobalSessionController getInstance() {
        return getGlobalSessionController();
    }

    public static void configureGlobalSessionController(GlobalSessionConfig config) {
        getGlobalSessionController().setConfig(config);
    }

    public static void configureGlobalSessionController(Map<String, ?> config) {
        getGlobalSessionController().setConfig(config);
    }

    static synchronized void resetForTesting() {
        instance = null;
    }

    public synchronized void setConfig(GlobalSessionConfig config) {
        if (config == null) {
            basePath = Path.of(DEFAULT_BASE_PATH);
            return;
        }
        basePath = Path.of(config.getBasePath());
    }

    public synchronized void setConfig(Map<String, ?> config) {
        if (config == null) {
            basePath = Path.of(DEFAULT_BASE_PATH);
            return;
        }
        Object configuredBasePath = firstPresent(config, "base_path", "basePath");
        basePath = configuredBasePath == null ? Path.of(DEFAULT_BASE_PATH) : Path.of(String.valueOf(configuredBasePath));

        Object configuredContainerType = firstPresent(config, "data_container_type", "dataContainerType");
        if (configuredContainerType != null && !String.valueOf(configuredContainerType).isBlank()) {
            dataContainerType = String.valueOf(configuredContainerType);
        }
    }

    public synchronized boolean loadAgent(String agentId) {
        return loadAgent(agentId, true);
    }

    public synchronized boolean loadAgent(String agentId, boolean loadActiveOnly) {
        Loggers.SESSION.debug("Loading agent {}, load_active_only={}", agentId, loadActiveOnly);
        SessionController controller = getOrCreateController(agentId);
        return controller.load(loadActiveOnly);
    }

    public synchronized void loadScope(SessionScope sessionScope) {
        loadScope(sessionScope, true);
    }

    public synchronized void loadScope(SessionScope sessionScope, boolean loadActiveOnly) {
        Loggers.SESSION.debug("Loading scope {} across all agents", sessionScope);
        for (SessionController controller : controllers.values()) {
            controller.loadScope(sessionScope, loadActiveOnly);
        }
        Loggers.SESSION.info("Loaded scope {} across {} agents", sessionScope, controllers.size());
    }

    public synchronized void loadAll() {
        loadAll(true);
    }

    public synchronized void loadAll(boolean loadActiveOnly) {
        Loggers.SESSION.debug("Loading all agents, load_active_only={}", loadActiveOnly);
        if (!Files.exists(basePath)) {
            return;
        }
        try (Stream<Path> entries = Files.list(basePath)) {
            for (Path item : entries.filter(Files::isDirectory).toList()) {
                String agentId = item.getFileName().toString();
                SessionController controller = getOrCreateController(agentId);
                controller.load(loadActiveOnly);
            }
            Loggers.SESSION.info("Loaded all agents from {}", basePath);
        } catch (IOException exception) {
            Loggers.SESSION.exception("Error loading all agents from {}", exception, basePath);
        }
    }

    public synchronized boolean flushAgent(String agentId) {
        SessionController controller = controllers.get(agentId);
        if (controller == null) {
            Loggers.SESSION.warning("Agent {} not found, skip flushing", agentId);
            return false;
        }
        Loggers.SESSION.debug("Flushing agent {}", agentId);
        return controller.flush();
    }

    public synchronized void flushSession(String sessionId) {
        Loggers.SESSION.debug("Flushing session {} across agents", sessionId);
        int flushed = 0;
        for (SessionController controller : controllers.values()) {
            if (controller.flushSession(sessionId)) {
                flushed += 1;
            }
        }
        if (flushed == 0) {
            Loggers.SESSION.warning("Session {} not found in any agent cache", sessionId);
        } else {
            Loggers.SESSION.info("Flushed session {} across {} agents", sessionId, flushed);
        }
    }

    public synchronized void flushScope(SessionScope sessionScope) {
        Loggers.SESSION.debug("Flushing scope {} across all agents", sessionScope);
        int flushed = 0;
        for (SessionController controller : controllers.values()) {
            if (controller.flushScope(sessionScope)) {
                flushed += 1;
            }
        }
        Loggers.SESSION.info("Flushed scope {} across {} agents", sessionScope, flushed);
    }

    public synchronized void flushAll() {
        Loggers.SESSION.debug("Flushing all agents");
        for (SessionController controller : controllers.values()) {
            controller.flush();
        }
        Loggers.SESSION.info("Flushed all {} agents", controllers.size());
    }

    public synchronized Map<String, List<SessionController.ScopeCleanupResult>> cleanupAgentInactiveSessions(
            String agentId
    ) {
        SessionController controller = controllers.get(agentId);
        if (controller == null) {
            throw new IllegalArgumentException("Agent " + agentId + " not found");
        }

        List<SessionController.ScopeCleanupResult> cleaned = new ArrayList<>();
        for (SessionScope sessionScope : controller.listMetas().keySet()) {
            List<SessionController.ScopeCleanupResult> scopeCleaned =
                    controller.cleanupScopeInactiveSessions(sessionScope);
            if (!scopeCleaned.isEmpty()) {
                cleaned.addAll(scopeCleaned);
            }
        }

        LinkedHashMap<String, List<SessionController.ScopeCleanupResult>> result = new LinkedHashMap<>();
        if (!cleaned.isEmpty()) {
            result.put(agentId, List.copyOf(cleaned));
        }
        Loggers.SESSION.info("Cleaned up inactive sessions for agent {}, scopes_cleaned={}",
                agentId, cleaned.size());
        return result;
    }

    public synchronized Map<String, List<SessionMeta>> cleanupScopeInactiveSessions(SessionScope sessionScope) {
        LinkedHashMap<String, List<SessionMeta>> cleanedSessions = new LinkedHashMap<>();
        for (Map.Entry<String, SessionController> entry : controllers.entrySet()) {
            SessionController controller = entry.getValue();
            if (!controller.listMetas().containsKey(sessionScope)) {
                continue;
            }
            List<SessionMeta> sessionMetas = new ArrayList<>();
            for (SessionController.ScopeCleanupResult cleaned : controller.cleanupScopeInactiveSessions(sessionScope)) {
                sessionMetas.addAll(cleaned.sessions());
            }
            if (!sessionMetas.isEmpty()) {
                cleanedSessions.put(entry.getKey(), List.copyOf(sessionMetas));
            }
        }
        return cleanedSessions;
    }

    public synchronized Optional<SessionController> getAgent(String agentId) {
        return Optional.ofNullable(controllers.get(agentId));
    }

    public synchronized CreateAgentResult createIfNotExistAgent(String agentId) {
        SessionController controller = controllers.get(agentId);
        if (controller != null) {
            return new CreateAgentResult(false, controller);
        }

        ensureBasePath();
        controller = new SessionController(agentId, basePath, dataContainerType);
        controller.load();
        controllers.put(agentId, controller);
        return new CreateAgentResult(true, controller);
    }

    public synchronized boolean removeAgent(String agentId) {
        SessionController controller = controllers.remove(agentId);
        if (controller == null) {
            return false;
        }
        controller.removeAll();
        deleteRecursively(SessionPaths.agentDir(basePath, agentId));
        return true;
    }

    public synchronized void removeAll() {
        for (SessionController controller : controllers.values()) {
            controller.removeAll();
        }
        controllers.clear();
        deleteRecursively(basePath);
    }

    public synchronized Map<String, List<String>> cleanupOrphanFiles() {
        return cleanupOrphanFiles(null, false);
    }

    public synchronized Map<String, List<String>> cleanupOrphanFiles(String agentId, boolean dryRun) {
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        for (String currentAgentId : agentsToProcess(agentId)) {
            Path sessionsDir = SessionPaths.sessionsDir(basePath, currentAgentId);
            if (!Files.exists(sessionsDir)) {
                continue;
            }

            List<String> orphanDirs = orphanSessionDirs(currentAgentId, sessionsDir);
            if (orphanDirs.isEmpty()) {
                continue;
            }
            result.put(currentAgentId, orphanDirs);
            if (dryRun) {
                Loggers.SESSION.info("Found {} orphan dirs for agent {} (dry_run=True)",
                        orphanDirs.size(), currentAgentId);
                continue;
            }
            for (String orphanDir : orphanDirs) {
                deleteRecursively(SessionPaths.sessionDir(basePath, currentAgentId, orphanDir));
            }
            Loggers.SESSION.info("Deleted {} orphan dirs for agent {}", orphanDirs.size(), currentAgentId);
        }
        return result;
    }

    public static CreateSessionResult createDirectSession(String agentId, String userId, String sessionId) {
        return createDirectSession(agentId, userId, sessionId, Map.of());
    }

    public static CreateSessionResult createDirectSession(
            String agentId,
            String userId,
            String sessionId,
            Map<String, Object> dataParams
    ) {
        SessionScope sessionScope = SessionScopeFactory.createDirect(userId);
        SessionController controller = getGlobalSessionController().createIfNotExistAgent(agentId).controller();
        SessionController.CreateIfNotExistsResult result = controller.createIfNotExists(
                sessionScope,
                sessionId,
                safeDataParams(dataParams)
        );
        return new CreateSessionResult(result.created(), result.session());
    }

    public static CreateSessionResult createGroupSession(String agentId, String groupId, String sessionId) {
        return createGroupSession(agentId, groupId, sessionId, Map.of());
    }

    public static CreateSessionResult createGroupSession(
            String agentId,
            String groupId,
            String sessionId,
            Map<String, Object> dataParams
    ) {
        SessionScope sessionScope = SessionScopeFactory.createGroup(groupId);
        SessionController controller = getGlobalSessionController().createIfNotExistAgent(agentId).controller();
        SessionController.CreateIfNotExistsResult result = controller.createIfNotExists(
                sessionScope,
                sessionId,
                safeDataParams(dataParams)
        );
        return new CreateSessionResult(result.created(), result.session());
    }

    public static Optional<Object> getDirectSessionData(String agentId, String userId) {
        Optional<SessionController> controller = getGlobalSessionController().getAgent(agentId);
        if (controller.isEmpty()) {
            return Optional.empty();
        }
        SessionScope sessionScope = SessionScopeFactory.createDirect(userId);
        return controller.orElseThrow().getScopeActiveSession(sessionScope).map(ChainSession::getData);
    }

    public static boolean updateDirectSessionData(String agentId, String userId, Map<String, Object> data) {
        Optional<SessionController> controller = getGlobalSessionController().getAgent(agentId);
        if (controller.isEmpty()) {
            return false;
        }
        SessionScope sessionScope = SessionScopeFactory.createDirect(userId);
        return controller.orElseThrow()
                .getScopeActiveSession(sessionScope)
                .map(session -> session.updateData(safeDataParams(data)))
                .orElse(false);
    }

    public static boolean addDirectSessionDownstream(String callerAgentId,
                                                    String callerUserId,
                                                    String targetAgentId,
                                                    String targetUserId) {
        return addDirectSessionDownstream(
                callerAgentId,
                callerUserId,
                targetAgentId,
                targetUserId,
                new SharingPolicy()
        );
    }

    public static boolean addDirectSessionDownstream(String callerAgentId,
                                                    String callerUserId,
                                                    String targetAgentId,
                                                    String targetUserId,
                                                    SharingPolicy policy) {
        GlobalSessionController instance = getGlobalSessionController();
        Optional<SessionController> callerController = instance.getAgent(callerAgentId);
        Optional<SessionController> targetController = instance.getAgent(targetAgentId);
        if (callerController.isEmpty() || targetController.isEmpty()) {
            return false;
        }

        Optional<ChainSession<?>> callerSession = callerController.orElseThrow()
                .getScopeActiveSession(SessionScopeFactory.createDirect(callerUserId));
        Optional<ChainSession<?>> targetSession = targetController.orElseThrow()
                .getScopeActiveSession(SessionScopeFactory.createDirect(targetUserId));
        if (callerSession.isEmpty() || targetSession.isEmpty()) {
            return false;
        }

        callerSession.orElseThrow().addDownstream(
                targetAgentId,
                targetSession.orElseThrow().getSessionId(),
                policy == null ? new SharingPolicy() : policy
        );
        callerSession.orElseThrow().flush();
        return true;
    }

    public static List<SessionController.ScopeCleanupResult> cleanupUserSessions(String agentId, String userId) {
        Optional<SessionController> controller = getGlobalSessionController().getAgent(agentId);
        if (controller.isEmpty()) {
            return List.of();
        }
        return controller.orElseThrow().cleanupScopeInactiveSessions(SessionScopeFactory.createDirect(userId));
    }

    public static List<ChainSession<?>> getUserSessionHistory(String agentId, String userId) {
        Optional<SessionController> controller = getGlobalSessionController().getAgent(agentId);
        if (controller.isEmpty()) {
            return List.of();
        }
        return controller.orElseThrow().getScopeSessions(SessionScopeFactory.createDirect(userId));
    }

    public static boolean flushUserSession(String agentId, String userId) {
        Optional<SessionController> controller = getGlobalSessionController().getAgent(agentId);
        if (controller.isEmpty()) {
            Loggers.SESSION.warning("Agent {} not found for flush_user_session", agentId);
            return false;
        }
        return controller.orElseThrow().flushScope(SessionScopeFactory.createDirect(userId));
    }

    public static String visualizeCallChain(String agentId, String sessionId) {
        return visualizeCallChain(agentId, sessionId, 3);
    }

    public static String visualizeCallChain(String agentId, String sessionId, int depth) {
        GlobalSessionController instance = getGlobalSessionController();
        Optional<SessionController> controller = instance.getAgent(agentId);
        if (controller.isEmpty()) {
            return "Agent " + agentId + " not found";
        }
        Optional<ChainSession<?>> session = instance.findCachedSession(controller.orElseThrow(), sessionId);
        if (session.isEmpty()) {
            return "Session " + sessionId + " not found in agent " + agentId;
        }

        List<String> lines = new ArrayList<>();
        ChainSession<?> chainSession = session.orElseThrow();
        lines.add("ChainSession Call Chain Visualization");
        lines.add("=".repeat(50));
        SessionScopeKey scopeKey = new SessionScopeKey(agentId, chainSession.getSessionScope());
        lines.add("Current session: " + scopeKey + " [" + abbreviate(sessionId) + "...]");
        lines.add("Status: " + (chainSession.isActive() ? "Active" : "Inactive"));
        lines.add("");
        lines.add("Call chain relationships (depth: " + depth + "):");
        lines.add("-".repeat(50));
        instance.buildCallChainTree(chainSession, lines, "", 1, depth);
        return String.join("\n", lines);
    }

    public synchronized Path getBasePath() {
        return basePath;
    }

    public synchronized String getDataContainerType() {
        return dataContainerType;
    }

    public synchronized Map<String, SessionController> getControllers() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(controllers));
    }

    private static Object firstPresent(Map<String, ?> config, String first, String second) {
        return config.containsKey(first) ? config.get(first) : config.get(second);
    }

    private static Map<String, Object> safeDataParams(Map<String, Object> dataParams) {
        return dataParams == null ? Map.of() : new LinkedHashMap<>(dataParams);
    }

    private synchronized Optional<ChainSession<?>> findCachedSession(SessionController controller, String sessionId) {
        for (SessionScope sessionScope : controller.listMetas().keySet()) {
            Optional<ChainSession<?>> activeSession = controller.getScopeActiveSession(sessionScope);
            if (activeSession.isPresent() && Objects.equals(activeSession.orElseThrow().getSessionId(), sessionId)) {
                return activeSession;
            }
            for (ChainSession<?> session : controller.getScopeSessions(sessionScope)) {
                if (Objects.equals(session.getSessionId(), sessionId)) {
                    return Optional.of(session);
                }
            }
        }
        return Optional.empty();
    }

    private synchronized void buildCallChainTree(ChainSession<?> session,
                                                List<String> lines,
                                                String prefix,
                                                int currentDepth,
                                                int maxDepth) {
        if (currentDepth > maxDepth) {
            return;
        }
        for (Map.Entry<ChainSession.DownstreamKey, SharingPolicy> entry : session.getDownstreams().entrySet()) {
            ChainSession.DownstreamKey key = entry.getKey();
            SharingPolicy policy = entry.getValue();
            String connector = currentDepth < maxDepth ? "├─►" : "└─►";
            lines.add(prefix + connector + " " + key.targetAgent() + " [" + abbreviate(key.targetSession()) + "...]");
            lines.add(prefix + "│   ├─ Permissions: " + policy.getPermission().name());
            if (policy.getFieldScopes() == null || policy.getFieldScopes().isEmpty()) {
                lines.add(prefix + "│   ├─ Field scope: All fields");
            } else {
                lines.add(prefix + "│   ├─ Field scope: " + policy.getFieldScopes());
            }

            Optional<SessionController> targetController = getAgent(key.targetAgent());
            Optional<ChainSession<?>> targetSession = targetController
                    .flatMap(controller -> findCachedSession(controller, key.targetSession()));
            if (targetSession.isPresent()) {
                buildCallChainTree(targetSession.orElseThrow(), lines, prefix + "│   ", currentDepth + 1, maxDepth);
            } else {
                lines.add(prefix + "│   └─ (not loaded)");
            }
        }
    }

    private void registerTeamEventCallbacks() {
        try {
            CallbackUtils.getCallbackFramework().registerSync(
                    AgentTeamEvents.AGENT_P2P_RECEIVED,
                    GlobalSessionController::onAgentP2pReceived,
                    0,
                    false,
                    "default",
                    Collections.emptySet(),
                    Collections.emptyList(),
                    null,
                    null,
                    0,
                    0.0D,
                    null,
                    "sync"
            );
            CallbackUtils.getCallbackFramework().registerSync(
                    AgentTeamEvents.AGENT_PUBSUB_RECEIVED,
                    GlobalSessionController::onAgentPubsubReceived,
                    0,
                    false,
                    "default",
                    Collections.emptySet(),
                    Collections.emptyList(),
                    null,
                    null,
                    0,
                    0.0D,
                    null,
                    "sync"
            );
            CallbackUtils.getCallbackFramework().registerSync(
                    SessionEvents.AGENT_SESSION_CREATED,
                    GlobalSessionController::onAgentSessionCreated,
                    0,
                    false,
                    "default",
                    Collections.emptySet(),
                    Collections.emptyList(),
                    null,
                    null,
                    0,
                    0.0D,
                    null,
                    "sync"
            );
        } catch (RuntimeException exception) {
            Loggers.SESSION.debug("Skip team event callbacks registration: {}", exception.getMessage());
        }
    }

    private static Object onAgentP2pReceived(Map<String, Object> kwargs) {
        recordDownstream(
                stringValue(kwargs.get("sender")),
                stringValue(kwargs.get("recipient")),
                stringValue(kwargs.get("session_id"))
        );
        return null;
    }

    private static Object onAgentPubsubReceived(Map<String, Object> kwargs) {
        recordDownstream(
                stringValue(kwargs.get("sender")),
                stringValue(kwargs.get("subscriber")),
                stringValue(kwargs.get("session_id"))
        );
        return null;
    }

    private static Object onAgentSessionCreated(Map<String, Object> kwargs) {
        if (!RunnerConfig.getRunnerConfig().isEnableSessionController()) {
            return null;
        }
        Object cardValue = kwargs.get("card");
        Object sessionValue = kwargs.get("session");
        String sessionId = stringValue(kwargs.get("session_id"));
        if (!(cardValue instanceof AgentCard card) || !(sessionValue instanceof AgentSession agentSession)
                || sessionId == null) {
            return null;
        }

        try {
            GlobalSessionController instance = getGlobalSessionController();
            Optional<SessionController> controller = instance.getAgent(card.getId());
            if (controller.isEmpty()) {
                Loggers.SESSION.debug("[session_created] agent '{}' not found, skip", card.getId());
                return null;
            }
            Optional<ChainSession<?>> chainSession = instance.findCachedSession(controller.orElseThrow(), sessionId);
            if (chainSession.isEmpty()) {
                Loggers.SESSION.debug(
                        "[session_created] chain_session '{}' not found for agent '{}', skip",
                        sessionId,
                        card.getId()
                );
                return null;
            }
            DataContainer dataContainer = chainSession.orElseThrow().getDataContainer();
            if (dataContainer instanceof AgentSessionContainer container) {
                container.setSession(agentSession);
                Loggers.SESSION.debug("[session_created] updated data_container session for agent '{}', session_id '{}'",
                        card.getId(), sessionId);
            }
        } catch (RuntimeException exception) {
            Loggers.SESSION.exception("[session_created] error updating data_container session", exception);
        }
        return null;
    }

    private static void recordDownstream(String sender, String recipient, String sessionId) {
        if (!RunnerConfig.getRunnerConfig().isEnableSessionController()) {
            return;
        }
        if (sender == null || recipient == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            GlobalSessionController instance = getGlobalSessionController();
            Optional<SessionController> senderController = instance.getAgent(sender);
            if (senderController.isEmpty()) {
                Loggers.SESSION.debug("[downstream] sender agent '{}' not found, skip", sender);
                return;
            }
            Optional<ChainSession<?>> senderSession = instance.firstActiveSession(senderController.orElseThrow());
            if (senderSession.isEmpty()) {
                Loggers.SESSION.debug("[downstream] no active session for sender '{}', skip", sender);
                return;
            }
            ChainSession<?> session = senderSession.orElseThrow();
            if (session.hasDownstream(recipient, sessionId)) {
                return;
            }
            session.addDownstream(recipient, sessionId, new SharingPolicy());
            session.flush();
            Loggers.SESSION.debug("[downstream] {} -> {}/{}", sender, recipient, sessionId);
        } catch (RuntimeException exception) {
            Loggers.SESSION.exception("[downstream] Error recording downstream", exception);
        }
    }

    private synchronized Optional<ChainSession<?>> firstActiveSession(SessionController controller) {
        for (SessionScope sessionScope : controller.listMetas().keySet()) {
            Optional<ChainSession<?>> activeSession = controller.getScopeActiveSession(sessionScope);
            if (activeSession.isPresent()) {
                return activeSession;
            }
        }
        return Optional.empty();
    }

    private synchronized SessionController getOrCreateController(String agentId) {
        return controllers.computeIfAbsent(agentId, id -> {
            ensureBasePath();
            return new SessionController(id, basePath, dataContainerType);
        });
    }

    private synchronized void ensureBasePath() {
        try {
            Files.createDirectories(basePath);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create base path: " + basePath, exception);
        }
    }

    private synchronized List<String> agentsToProcess(String agentId) {
        if (agentId != null && !agentId.isBlank()) {
            if (controllers.containsKey(agentId) || Files.exists(SessionPaths.agentDir(basePath, agentId))) {
                return List.of(agentId);
            }
            return List.of();
        }
        if (!Files.exists(basePath)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(basePath)) {
            return entries.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .toList();
        } catch (IOException exception) {
            Loggers.SESSION.exception("Error listing agents under {}", exception, basePath);
            return List.of();
        }
    }

    private List<String> orphanSessionDirs(String agentId, Path sessionsDir) {
        List<String> registeredSessions = registeredSessionIds(agentId);
        try (Stream<Path> entries = Files.list(sessionsDir)) {
            return entries.filter(Files::isDirectory)
                    .filter(path -> !"downstreams".equals(path.getFileName().toString()))
                    .filter(path -> Files.exists(SessionPaths.stateFile(path)))
                    .map(path -> path.getFileName().toString())
                    .filter(sessionId -> !registeredSessions.contains(sessionId))
                    .toList();
        } catch (IOException exception) {
            Loggers.SESSION.exception("Error scanning orphan sessions for agent {}", exception, agentId);
            return List.of();
        }
    }

    private List<String> registeredSessionIds(String agentId) {
        Path metaFile = SessionPaths.metaFile(basePath, agentId);
        if (!Files.exists(metaFile)) {
            return List.of();
        }
        try {
            Map<String, Object> metaData = OBJECT_MAPPER.readValue(
                    metaFile.toFile(),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
            List<String> registeredSessions = new ArrayList<>();
            for (Object scopeValue : metaData.values()) {
                if (!(scopeValue instanceof Map<?, ?> scopeMap)) {
                    continue;
                }
                Object sessionsValue = scopeMap.get("sessions");
                if (!(sessionsValue instanceof List<?> sessions)) {
                    continue;
                }
                for (Object sessionValue : sessions) {
                    if (sessionValue instanceof Map<?, ?> sessionMap && sessionMap.containsKey("session_id")) {
                        registeredSessions.add(String.valueOf(sessionMap.get("session_id")));
                    }
                }
            }
            return registeredSessions;
        } catch (IOException | RuntimeException exception) {
            Loggers.SESSION.error("Error reading sessions.json for agent {}: {}", agentId, exception.getMessage());
            return List.of();
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
            // Python ignores cleanup failures.
        }
    }

    private static String abbreviate(String sessionId) {
        if (sessionId == null) {
            return "";
        }
        return sessionId.length() <= 8 ? sessionId : sessionId.substring(0, 8);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Result tuple for creating or retrieving an agent controller.
     *
     * <p>Mirrors Python's {@code tuple[bool, SessionController]} returned by
     * {@code GlobalSessionController.create_if_not_exist_agent} in
     * {@code openjiuwen/core/session/session_controller/global_controller.py}.</p>
     *
     * @param created whether a new controller was created
     * @param controller session controller for the agent
     */
    public record CreateAgentResult(boolean created, SessionController controller) {
    }

    /**
     * Result tuple for convenience session creation.
     *
     * <p>Mirrors Python's {@code tuple[bool, ChainSession]} returned by
     * {@code GlobalSessionController.create_direct_session} and
     * {@code GlobalSessionController.create_group_session} in
     * {@code openjiuwen/core/session/session_controller/global_controller.py}.</p>
     *
     * @param created whether a new session was created
     * @param session created or existing chain session
     */
    public record CreateSessionResult(boolean created, ChainSession<?> session) {
    }
}
