/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.callback.AsyncCallbackFramework;
import com.openjiuwen.core.runner.callback.CallbackUtils;
import com.openjiuwen.core.runner.callback.SessionEvents;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused tests for the process-global session controller facade.
 *
 * <p>Mirrors Python's {@code GlobalSessionController} in
 * {@code openjiuwen/core/session/session_controller/global_controller.py}.</p>
 */
class GlobalSessionControllerTest {

    private static final String CONTAINER_TYPE = "global-controller-test";

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        GlobalSessionController.resetForTesting();
        CallbackUtils.setCallbackFramework(new AsyncCallbackFramework());
        RunnerConfig.setRunnerConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG.copy());
        DataContainerFactory.register(CONTAINER_TYPE, new DataContainerFactory.DataContainerProvider() {
            @Override
            public DataContainer create(Map<String, Object> kwargs) {
                Object data = kwargs.get("data");
                if (data instanceof Map<?, ?> map) {
                    return new MapContainer(stringObjectMap(map));
                }
                return new MapContainer(Map.of());
            }

            @Override
            public java.util.concurrent.CompletionStage<DataContainer> load(String agentId,
                                                                            String sessionId,
                                                                            Object serialized,
                                                                            Map<String, Object> kwargs) {
                if (serialized instanceof Map<?, ?> map) {
                    return java.util.concurrent.CompletableFuture.completedFuture(new MapContainer(stringObjectMap(map)));
                }
                return java.util.concurrent.CompletableFuture.completedFuture(new MapContainer(Map.of()));
            }
        });
        controller().setConfig(Map.of(
                "base_path", tempDir.resolve("agents").toString(),
                "data_container_type", CONTAINER_TYPE
        ));
    }

    @AfterEach
    void tearDown() {
        GlobalSessionController.resetForTesting();
        CallbackUtils.resetFrameworkSupplier();
        RunnerConfig.setRunnerConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG.copy());
    }

    @Test
    void singletonAndConfigMirrorPythonDefaultsAndOverrides() {
        GlobalSessionController.resetForTesting();
        GlobalSessionController first = controller();
        GlobalSessionController second = controller();

        assertThat(first).isSameAs(second);
        assertThat(new GlobalSessionConfig().getBasePath()).isEqualTo("./agents");

        Path configured = tempDir.resolve("configured");
        first.setConfig(new GlobalSessionConfig(configured.toString()));
        assertThat(first.getBasePath()).isEqualTo(configured);
        first.setConfig(Map.of("base_path", tempDir.resolve("map").toString(), "data_container_type", "agent"));
        assertThat(first.getBasePath()).isEqualTo(tempDir.resolve("map"));
        assertThat(first.getDataContainerType()).isEqualTo("agent");
    }

    @Test
    void createGetRemoveAndRemoveAllManageAgentControllersAndDisk() {
        GlobalSessionController controller = controller();

        GlobalSessionController.CreateAgentResult created = controller.createIfNotExistAgent("agent1");
        GlobalSessionController.CreateAgentResult existing = controller.createIfNotExistAgent("agent1");

        assertThat(created.created()).isTrue();
        assertThat(existing.created()).isFalse();
        assertThat(controller.getAgent("agent1")).isPresent();
        assertThat(controller.getAgent("missing")).isEmpty();

        SessionScope mainScope = SessionScopeFactory.createMain();
        created.controller().createIfNotExists(mainScope, "session-1");
        controller.flushAgent("agent1");
        assertThat(SessionPaths.metaFile(controller.getBasePath(), "agent1")).exists();

        assertThat(controller.removeAgent("agent1")).isTrue();
        assertThat(controller.removeAgent("agent1")).isFalse();
        assertThat(SessionPaths.agentDir(controller.getBasePath(), "agent1")).doesNotExist();

        controller.createIfNotExistAgent("agent2").controller().createIfNotExists(mainScope, "session-2");
        controller.removeAll();
        assertThat(controller.getControllers()).isEmpty();
        assertThat(controller.getBasePath()).doesNotExist();
    }

    @Test
    void persistenceAndCleanupScanAgentsScopesAndOrphans() throws Exception {
        GlobalSessionController controller = controller();
        SessionScope directScope = SessionScopeFactory.createDirect("user1");
        SessionController first = controller.createIfNotExistAgent("agent1").controller();
        SessionController second = controller.createIfNotExistAgent("agent2").controller();
        first.createIfNotExists(directScope, "session-1");
        second.createIfNotExists(directScope, "session-2");
        controller.flushAll();

        Path existingBasePath = controller.getBasePath();
        GlobalSessionController.resetForTesting();
        CallbackUtils.setCallbackFramework(new AsyncCallbackFramework());
        GlobalSessionController reloadedController = controller();
        reloadedController.setConfig(Map.of(
                "base_path", existingBasePath.toString(),
                "data_container_type", CONTAINER_TYPE
        ));
        reloadedController.loadAll(false);
        assertThat(reloadedController.getAgent("agent1")).isPresent();
        reloadedController.loadScope(directScope, false);
        reloadedController.flushSession("session-1");
        reloadedController.flushScope(directScope);

        Path orphan = SessionPaths.sessionsDir(reloadedController.getBasePath(), "agent1").resolve("orphan-session");
        Files.createDirectories(orphan);
        Files.writeString(SessionPaths.stateFile(orphan), "{}");

        Map<String, List<String>> dryRun = reloadedController.cleanupOrphanFiles("agent1", true);
        assertThat(dryRun).containsKey("agent1");
        assertThat(dryRun.get("agent1")).contains("orphan-session");
        assertThat(orphan).exists();

        Map<String, List<String>> deleted = reloadedController.cleanupOrphanFiles("agent1", false);
        assertThat(deleted.get("agent1")).contains("orphan-session");
        assertThat(orphan).doesNotExist();
    }

    @Test
    void inactiveCleanupReportsByAgentAndScope() {
        GlobalSessionController controller = controller();
        SessionScope directScope = SessionScopeFactory.createDirect("user1");
        SessionController first = controller.createIfNotExistAgent("agent1").controller();
        SessionController second = controller.createIfNotExistAgent("agent2").controller();
        first.createIfNotExists(directScope, "session-1");
        second.createIfNotExists(directScope, "session-2");
        first.getScopeActiveSession(directScope).orElseThrow().setActive(false);
        second.getScopeActiveSession(directScope).orElseThrow().setActive(false);
        first.getScopeMeta(directScope).deactivateAllSessions();
        second.getScopeMeta(directScope).deactivateAllSessions();
        first.createIfNotExists(directScope, "session-1-new");
        second.createIfNotExists(directScope, "session-2-new");

        Map<String, List<SessionController.ScopeCleanupResult>> byAgent =
                controller.cleanupAgentInactiveSessions("agent1");
        Map<String, List<SessionMeta>> byScope = controller.cleanupScopeInactiveSessions(directScope);

        assertThat(byAgent).containsKey("agent1");
        assertThat(byScope).containsKeys("agent2");
        assertThatThrownBy(() -> controller.cleanupAgentInactiveSessions("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void convenienceMethodsCreateDirectAndGroupSessionsAndUpdateData() {
        Map<String, Object> data = Map.of("data", Map.of("key", "value"));

        GlobalSessionController.CreateSessionResult direct =
                GlobalSessionController.createDirectSession("agent1", "user1", "session-1", data);
        GlobalSessionController.CreateSessionResult existing =
                GlobalSessionController.createDirectSession("agent1", "user1", "session-2", data);
        GlobalSessionController.CreateSessionResult group =
                GlobalSessionController.createGroupSession("agent1", "group1", "group-session", data);

        assertThat(direct.created()).isTrue();
        assertThat(existing.created()).isFalse();
        assertThat(existing.session().getSessionId()).isEqualTo("session-1");
        assertThat(direct.session().getSessionScope().toString()).contains("direct:user1");
        assertThat(group.session().getSessionScope().toString()).contains("group:group1");

        Optional<Object> before = GlobalSessionController.getDirectSessionData("agent1", "user1");
        assertThat(before).isPresent();
        assertThat(GlobalSessionController.updateDirectSessionData(
                "agent1",
                "user1",
                Map.of("new_key", "new_value")
        )).isTrue();
        assertThat(GlobalSessionController.updateDirectSessionData("missing", "user1", Map.of())).isFalse();
        assertThat(GlobalSessionController.getDirectSessionData("missing", "user1")).isEmpty();
    }

    @Test
    void downstreamHistoryFlushAndCallChainMirrorConvenienceBehavior() {
        GlobalSessionController.createDirectSession("agent1", "user1", "session-1");
        GlobalSessionController.createDirectSession("agent2", "user2", "session-2");

        boolean linked = GlobalSessionController.addDirectSessionDownstream(
                "agent1",
                "user1",
                "agent2",
                "user2",
                new SharingPolicy(Permission.READ, java.util.Set.of("field1", "field2"))
        );

        assertThat(linked).isTrue();
        assertThat(GlobalSessionController.addDirectSessionDownstream("missing", "user1", "agent2", "user2"))
                .isFalse();
        assertThat(GlobalSessionController.getUserSessionHistory("agent1", "user1")).hasSize(1);
        assertThat(GlobalSessionController.cleanupUserSessions("missing", "user1")).isEmpty();
        assertThat(GlobalSessionController.flushUserSession("agent1", "user1")).isTrue();
        assertThat(GlobalSessionController.flushUserSession("missing", "user1")).isFalse();

        String callChain = GlobalSessionController.visualizeCallChain("agent1", "session-1", 3);
        assertThat(callChain).contains("ChainSession Call Chain Visualization");
        assertThat(callChain).contains("agent:agent1:main:direct:user1");
        assertThat(callChain).contains("agent2");
        assertThat(callChain).contains("Permissions: READ");
        assertThat(callChain).contains("field1").contains("field2");
        assertThat(GlobalSessionController.visualizeCallChain("missing", "session-1")).contains("not found");
        assertThat(GlobalSessionController.visualizeCallChain("agent1", "missing-session")).contains("not found");
    }

    @Test
    void callbackRegistrationIsBestEffortAndSessionCreatedUpdatesContainerSession() {
        RunnerConfig config = RunnerConfig.DEFAULT_RUNNER_CONFIG.copy();
        config.setEnableSessionController(true);
        RunnerConfig.setRunnerConfig(config);
        GlobalSessionController.resetForTesting();
        CallbackUtils.setCallbackFramework(new AsyncCallbackFramework());
        GlobalSessionController controller = controller();
        controller.setConfig(Map.of(
                "base_path", tempDir.resolve("callbacks").toString(),
                "data_container_type", DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE
        ));

        AgentSessionContainer container = new AgentSessionContainer();
        GlobalSessionController.createDirectSession("agent1", "user1", "session-1", Map.of("session", container));
        AgentSession session = AgentSession.createAgentSession(
                "session-1",
                null,
                new AgentCard("agent1", "agent1", "")
        );

        CallbackUtils.trigger(SessionEvents.AGENT_SESSION_CREATED, Map.of(
                "session_id", "session-1",
                "card", new AgentCard("agent1", "agent1", ""),
                "session", session
        ));

        ChainSession<?> chainSession = controller.getAgent("agent1").orElseThrow()
                .getScopeActiveSession(SessionScopeFactory.createDirect("user1"))
                .orElseThrow();
        assertThat(((AgentSessionContainer) chainSession.getDataContainer()).getSession()).isSameAs(session);
    }

    private static GlobalSessionController controller() {
        return GlobalSessionController.getGlobalSessionController();
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> map) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * Test data container for Python mock session data behavior.
     *
     * <p>Mirrors Python's patched {@code AgentSessionContainer} in
     * {@code openjiuwen/core/session/session_controller/global_controller.py} tests.</p>
     */
    private static final class MapContainer implements DataContainer {
        private final java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();

        private MapContainer(Map<String, Object> values) {
            this.values.putAll(values);
        }

        @Override
        public Object get(Object key) {
            if (key == null) {
                return new java.util.LinkedHashMap<>(values);
            }
            return values.get(String.valueOf(key));
        }

        @Override
        public boolean update(Map<String, Object> data) {
            values.putAll(data);
            return true;
        }

        @Override
        public java.util.concurrent.CompletionStage<Object> dump() {
            return java.util.concurrent.CompletableFuture.completedFuture(new java.util.LinkedHashMap<>(values));
        }
    }
}
