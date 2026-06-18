/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused tests for session controller lifecycle behavior.
 *
 * <p>Mirrors Python's {@code SessionController} in
 * {@code openjiuwen/core/session/session_controller/session_controller.py}.</p>
 */
class SessionControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONTAINER_TYPE = "controller-test";
    private static final String FAILING_CONTAINER_TYPE = "controller-failing-dump";

    @TempDir
    Path tempDir;

    @BeforeAll
    static void registerContainer() {
        DataContainerFactory.register(CONTAINER_TYPE, new DataContainerFactory.DataContainerProvider() {
            @Override
            public DataContainer create(Map<String, Object> kwargs) {
                Object data = kwargs.get("data");
                return new MapContainer(data instanceof Map<?, ?> map ? toStringObjectMap(map) : Map.of());
            }

            @Override
            public CompletionStage<DataContainer> load(String agentId,
                                                       String sessionId,
                                                       Object serialized,
                                                       Map<String, Object> kwargs) {
                if (serialized instanceof Map<?, ?> map) {
                    return CompletableFuture.completedFuture(new MapContainer(toStringObjectMap(map)));
                }
                return CompletableFuture.completedFuture(new MapContainer(Map.of("loaded", sessionId)));
            }
        });
        DataContainerFactory.register(FAILING_CONTAINER_TYPE, new DataContainerFactory.DataContainerProvider() {
            @Override
            public DataContainer create(Map<String, Object> kwargs) {
                return new FailingDumpContainer();
            }

            @Override
            public CompletionStage<DataContainer> load(String agentId,
                                                       String sessionId,
                                                       Object serialized,
                                                       Map<String, Object> kwargs) {
                return CompletableFuture.completedFuture(new FailingDumpContainer());
            }
        });
    }

    @TestFactory
    List<DynamicTest> pythonSessionControllerParityCases() {
        return List.of(
                parity("test_creates_base_path", this::caseCreatesBasePath),
                parity("test_initial_state", this::caseInitialState),
                parity("test_create_new_session", this::caseCreateNewSession),
                parity("test_create_returns_existing_active", this::caseCreateReturnsExistingActive),
                parity("test_create_duplicate_session_id_raises", this::caseCreateDuplicateSessionIdRaises),
                parity("test_create_with_custom_container_factory", this::caseCreateWithCustomContainerFactory),
                parity("test_create_persists_to_disk", this::caseCreatePersistsToDisk),
                parity("test_get_scope_active_session", this::caseGetScopeActiveSession),
                parity("test_get_scope_active_session_none", this::caseGetScopeActiveSessionNone),
                parity("test_get_scope_sessions", this::caseGetScopeSessions),
                parity("test_activate_session", this::caseActivateSession),
                parity("test_activate_nonexistent_session", this::caseActivateNonexistentSession),
                parity("test_flush", this::caseFlush),
                parity("test_flush_session", this::caseFlushSession),
                parity("test_flush_session_not_in_cache", this::caseFlushSessionNotInCache),
                parity("test_flush_scope", this::caseFlushScope),
                parity("test_load_after_flush", this::caseLoadAfterFlush),
                parity("test_load_no_meta_file", this::caseLoadNoMetaFile),
                parity("test_load_scope", this::caseLoadScope),
                parity("test_remove_session", this::caseRemoveSession),
                parity("test_remove_session_deletes_disk", this::caseRemoveSessionDeletesDisk),
                parity("test_remove_scope_sessions", this::caseRemoveScopeSessions),
                parity("test_remove_all", this::caseRemoveAll),
                parity("test_cleanup_scope_inactive_sessions", this::caseCleanupScopeInactiveSessions),
                parity("test_cleanup_nonexistent_scope", this::caseCleanupNonexistentScope),
                parity("test_get_scope_meta", this::caseGetScopeMeta),
                parity("test_get_scope_meta_empty", this::caseGetScopeMetaEmpty),
                parity("test_list_metas", this::caseListMetas),
                parity("test_create_new_scope_auto_created", this::caseCreateNewScopeAutoCreated),
                parity("test_multi_scope_isolation", this::caseMultiScopeIsolation),
                parity("test_flush_empty_cache", this::caseFlushEmptyCache),
                parity("test_flush_session_failure", this::caseFlushSessionFailure),
                parity("test_flush_scope_nonexistent", this::caseFlushScopeNonexistent),
                parity("test_flush_scope_session_failure", this::caseFlushScopeSessionFailure),
                parity("test_flush_partial_failure", this::caseFlushPartialFailure),
                parity("test_load_active_only", this::caseLoadActiveOnly),
                parity("test_load_all_sessions", this::caseLoadAllSessions),
                parity("test_load_corrupted_meta_file", this::caseLoadCorruptedMetaFile),
                parity("test_load_partial_corrupted_scope", this::caseLoadPartialCorruptedScope),
                parity("test_load_scope_nonexistent", this::caseLoadScopeNonexistent),
                parity("test_load_session_idempotent", this::caseLoadSessionIdempotent),
                parity("test_get_scope_active_session_auto_loads", this::caseGetScopeActiveSessionAutoLoads),
                parity("test_get_scope_sessions_unknown_scope", this::caseGetScopeSessionsUnknownScope),
                parity("test_get_scope_sessions_unloaded_not_in_result", this::caseGetScopeSessionsUnloadedNotInResult),
                parity("test_activate_from_meta_map", this::caseActivateFromMetaMap),
                parity("test_activate_persists", this::caseActivatePersists),
                parity("test_remove_session_without_scope", this::caseRemoveSessionWithoutScope),
                parity("test_remove_nonexistent_session", this::caseRemoveNonexistentSession),
                parity("test_remove_active_session_clears_active", this::caseRemoveActiveSessionClearsActive),
                parity("test_remove_scope_sessions_nonexistent", this::caseRemoveScopeSessionsNonexistent),
                parity("test_remove_session_updates_meta_file", this::caseRemoveSessionUpdatesMetaFile),
                parity("test_cleanup_all_active", this::caseCleanupAllActive),
                parity("test_cleanup_all_inactive", this::caseCleanupAllInactive),
                parity("test_concurrent_create_same_scope", this::caseConcurrentCreateSameScope),
                parity("test_concurrent_flush_and_load", this::caseConcurrentFlushAndLoad),
                parity("test_concurrent_remove_and_get_sessions", this::caseConcurrentRemoveAndGetSessions),
                parity("test_sessions_json_no_sensitive_data", this::caseSessionsJsonNoSensitiveData),
                parity("test_path_traversal_session_id", this::casePathTraversalSessionId),
                parity("test_session_dir_deleted_externally", this::caseSessionDirDeletedExternally)
        );
    }

    @Test
    void createIfNotExistsCreatesActiveSessionAndPersistsMetadata() throws IOException {
        SessionController controller = new SessionController("agent-a", tempDir, CONTAINER_TYPE);
        SessionScope scope = mainScope();

        SessionController.CreateIfNotExistsResult created = controller.createIfNotExists(
                scope,
                "session-1",
                Map.of("data", Map.of("answer", 42))
        );

        assertThat(created.created()).isTrue();
        assertThat(created.session().getSessionId()).isEqualTo("session-1");
        assertThat(created.session().getData()).isEqualTo(Map.of("answer", 42));
        assertThat(SessionPaths.stateFile(SessionPaths.sessionDir(tempDir, "agent-a", "session-1"))).exists();

        Map<String, Object> metaData = readMap(SessionPaths.metaFile(tempDir, "agent-a"));
        Map<String, Object> scopeData = mapValue(metaData.get("agent:agent-a:main"));
        assertThat(scopeData).containsEntry("active_session", "session-1");
        assertThat((List<?>) scopeData.get("sessions")).hasSize(1);

        SessionController.CreateIfNotExistsResult reused = controller.createIfNotExists(scope, "session-2");
        assertThat(reused.created()).isFalse();
        assertThat(reused.session().getSessionId()).isEqualTo("session-1");
        assertThat(SessionPaths.sessionDir(tempDir, "agent-a", "session-2")).doesNotExist();
        assertThatThrownBy(() -> controller.createIfNotExists(scope, "session-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void loadRestoresMetadataAndOnlyLoadsActiveSessionsByDefault() throws IOException {
        SessionScope scope = mainScope();
        writeJson(SessionPaths.metaFile(tempDir, "agent-a"), Map.of(
                "agent:agent-a:main", Map.of(
                        "session_scope_key", "agent:agent-a:main",
                        "active_session", "active",
                        "sessions", List.of(
                                Map.of(
                                        "session_id", "active",
                                        "created_at", 1.0D,
                                        "updated_at", 3.0D,
                                        "version", 1,
                                        "is_active", true,
                                        "data_container_type", CONTAINER_TYPE
                                ),
                                Map.of(
                                        "session_id", "inactive",
                                        "created_at", 1.0D,
                                        "updated_at", 2.0D,
                                        "version", 1,
                                        "is_active", false,
                                        "data_container_type", CONTAINER_TYPE
                                )
                        )
                )
        ));

        SessionController controller = new SessionController("agent-a", tempDir, CONTAINER_TYPE);

        assertThat(controller.load()).isTrue();

        Optional<ChainSession<?>> activeSession = controller.getScopeActiveSession(scope);
        assertThat(activeSession).isPresent();
        assertThat(activeSession.orElseThrow().getSessionId()).isEqualTo("active");
        assertThat(controller.getScopeSessions(scope)).extracting(ChainSession::getSessionId).containsExactly("active");
        assertThat(controller.getScopeMeta(scope).getSessions()).extracting(SessionMeta::getSessionId)
                .containsExactly("active", "inactive");

        assertThat(controller.loadScope(scope, false)).isTrue();
        assertThat(controller.getScopeSessions(scope)).extracting(ChainSession::getSessionId)
                .containsExactly("active", "inactive");
    }

    @Test
    void activateSessionLoadsFromMetadataAndCleanupRemovesInactiveSessions() throws IOException {
        SessionController controller = new SessionController("agent-a", tempDir, CONTAINER_TYPE);
        SessionScope scope = mainScope();
        controller.createIfNotExists(scope, "active");
        ScopeSessionsMeta scopeMeta = controller.getScopeMeta(scope);
        SessionMeta inactive = new SessionMeta("inactive", 1.0D, 1.0D, 1, false, CONTAINER_TYPE);
        scopeMeta.addSession(inactive);
        Files.createDirectories(SessionPaths.sessionDir(tempDir, "agent-a", "inactive"));

        controller.activateSession("inactive");

        assertThat(controller.getScopeMeta(scope).getActiveSession()).isEqualTo("inactive");
        assertThat(controller.getScopeActiveSession(scope)).isPresent()
                .get()
                .extracting(ChainSession::getSessionId)
                .isEqualTo("inactive");

        List<SessionController.ScopeCleanupResult> cleaned = controller.cleanupScopeInactiveSessions(scope);
        assertThat(cleaned).hasSize(1);
        assertThat(cleaned.getFirst().sessions()).extracting(SessionMeta::getSessionId).containsExactly("active");
        assertThat(SessionPaths.sessionDir(tempDir, "agent-a", "active")).doesNotExist();
        assertThat(controller.getScopeMeta(scope).getSessions()).extracting(SessionMeta::getSessionId)
                .containsExactly("inactive");
        assertThatThrownBy(() -> controller.activateSession("missing"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void removeSessionScopeAndAllMirrorPythonCleanupReturnValues() {
        SessionController controller = new SessionController("agent-a", tempDir, CONTAINER_TYPE);
        SessionScope scope = mainScope();
        controller.createIfNotExists(scope, "session-1");
        ScopeSessionsMeta scopeMeta = controller.getScopeMeta(scope);
        scopeMeta.addSession(new SessionMeta("session-2", 1.0D, 1.0D, 1, false, CONTAINER_TYPE));
        Path sessionTwoDir = SessionPaths.sessionDir(tempDir, "agent-a", "session-2");
        assertThat(sessionTwoDir.toFile().mkdirs()).isTrue();

        List<SessionController.RemovedSession> removed = controller.removeSession("session-2");

        assertThat(removed).hasSize(1);
        assertThat(removed.getFirst().sessionScope()).isEqualTo(scope);
        assertThat(removed.getFirst().session().getSessionId()).isEqualTo("session-2");
        assertThat(sessionTwoDir).doesNotExist();
        assertThat(controller.removeSession("missing")).isEmpty();

        List<SessionMeta> scopeRemoved = controller.removeScopeSessions(scope);
        assertThat(scopeRemoved).extracting(SessionMeta::getSessionId).containsExactly("session-1");
        assertThat(SessionPaths.sessionDir(tempDir, "agent-a", "session-1")).doesNotExist();
        assertThat(controller.removeScopeSessions(scope)).isEmpty();

        controller.createIfNotExists(scope, "session-3");
        controller.removeAll();
        assertThat(SessionPaths.sessionsDir(tempDir, "agent-a")).doesNotExist();
        assertThat(controller.listMetas()).isEmpty();
    }

    private void caseCreatesBasePath() throws IOException {
        Path root = caseRoot("creates-base-path");
        newController(root);
        assertThat(SessionPaths.sessionsDir(root, "agent1")).exists();
    }

    private void caseInitialState() throws IOException {
        SessionController controller = newController(caseRoot("initial-state"));
        assertThat(controller.getAgentId()).isEqualTo("agent1");
        assertThat(controller.listMetas()).isEmpty();
        assertThat(controller.getScopeSessions(mainScope())).isEmpty();
    }

    private void caseCreateNewSession() throws IOException {
        SessionController controller = newController(caseRoot("create-new"));
        SessionController.CreateIfNotExistsResult result = controller.createIfNotExists(mainScope(), "session-1");
        assertThat(result.created()).isTrue();
        assertThat(result.session().getSessionId()).isEqualTo("session-1");
        assertThat(result.session().isActive()).isTrue();
        assertThat(controller.getScopeSessions(mainScope())).extracting(ChainSession::getSessionId)
                .containsExactly("session-1");
    }

    private void caseCreateReturnsExistingActive() throws IOException {
        SessionController controller = newController(caseRoot("create-existing-active"));
        SessionController.CreateIfNotExistsResult first = controller.createIfNotExists(mainScope(), "session-1");
        SessionController.CreateIfNotExistsResult second = controller.createIfNotExists(mainScope(), "session-2");
        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.session().getSessionId()).isEqualTo("session-1");
    }

    private void caseCreateDuplicateSessionIdRaises() throws IOException {
        SessionController controller = newController(caseRoot("create-duplicate"));
        controller.createIfNotExists(mainScope(), "session-1");
        assertThatThrownBy(() -> controller.createIfNotExists(directScope(), "session-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    private void caseCreateWithCustomContainerFactory() throws IOException {
        SessionController controller = newController(caseRoot("create-custom-container"));
        SessionController.CreateIfNotExistsResult result = controller.createIfNotExists(
                mainScope(),
                "session-1",
                Map.of("data", Map.of("custom", true))
        );
        assertThat(result.created()).isTrue();
        assertThat(result.session().getData()).isEqualTo(Map.of("custom", true));
    }

    private void caseCreatePersistsToDisk() throws IOException {
        Path root = caseRoot("create-persists");
        SessionController controller = newController(root);
        controller.createIfNotExists(mainScope(), "session-1");
        assertThat(SessionPaths.metaFile(root, "agent1")).exists();
        assertThat(SessionPaths.sessionDir(root, "agent1", "session-1")).exists();
    }

    private void caseGetScopeActiveSession() throws IOException {
        SessionController controller = newController(caseRoot("get-active"));
        controller.createIfNotExists(mainScope(), "session-1");
        assertThat(controller.getScopeActiveSession(mainScope())).isPresent()
                .get()
                .extracting(ChainSession::getSessionId)
                .isEqualTo("session-1");
    }

    private void caseGetScopeActiveSessionNone() throws IOException {
        SessionController controller = newController(caseRoot("get-active-none"));
        assertThat(controller.getScopeActiveSession(mainScope())).isEmpty();
    }

    private void caseGetScopeSessions() throws IOException {
        SessionController controller = newController(caseRoot("get-scope-sessions"));
        controller.createIfNotExists(mainScope(), "session-1");
        assertThat(controller.getScopeSessions(mainScope())).hasSize(1);
    }

    private void caseActivateSession() throws IOException {
        SessionController controller = newController(caseRoot("activate"));
        controller.createIfNotExists(mainScope(), "session-1");
        deactivateCurrent(controller, mainScope());
        controller.createIfNotExists(mainScope(), "session-2");
        controller.activateSession("session-1");
        assertThat(controller.getScopeActiveSession(mainScope())).isPresent()
                .get()
                .extracting(ChainSession::getSessionId)
                .isEqualTo("session-1");
    }

    private void caseActivateNonexistentSession() throws IOException {
        SessionController controller = newController(caseRoot("activate-missing"));
        assertThatThrownBy(() -> controller.activateSession("nonexistent"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("not found");
    }

    private void caseFlush() throws IOException {
        Path root = caseRoot("flush");
        SessionController controller = newController(root);
        controller.createIfNotExists(mainScope(), "session-1");
        assertThat(controller.flush()).isTrue();
        assertThat(SessionPaths.metaFile(root, "agent1")).exists();
    }

    private void caseFlushSession() throws IOException {
        SessionController controller = newController(caseRoot("flush-session"));
        controller.createIfNotExists(mainScope(), "session-1");
        assertThat(controller.flushSession("session-1")).isTrue();
    }

    private void caseFlushSessionNotInCache() throws IOException {
        SessionController controller = newController(caseRoot("flush-session-missing"));
        assertThat(controller.flushSession("nonexistent")).isTrue();
    }

    private void caseFlushScope() throws IOException {
        SessionController controller = newController(caseRoot("flush-scope"));
        controller.createIfNotExists(mainScope(), "session-1");
        assertThat(controller.flushScope(mainScope())).isTrue();
    }

    private void caseLoadAfterFlush() throws IOException {
        Path root = caseRoot("load-after-flush");
        SessionController controller = newController(root);
        controller.createIfNotExists(mainScope(), "session-1");
        controller.flush();
        SessionController loaded = newController(root);
        assertThat(loaded.load()).isTrue();
        assertThat(loaded.listMetas()).containsKey(mainScope());
    }

    private void caseLoadNoMetaFile() throws IOException {
        SessionController controller = newController(caseRoot("load-no-meta"));
        assertThat(controller.load()).isTrue();
    }

    private void caseLoadScope() throws IOException {
        Path root = caseRoot("load-scope");
        SessionController controller = newController(root);
        controller.createIfNotExists(mainScope(), "session-1");
        controller.flush();
        SessionController loaded = newController(root);
        assertThat(loaded.loadScope(mainScope())).isTrue();
        assertThat(loaded.listMetas()).containsKey(mainScope());
    }

    private void caseRemoveSession() throws IOException {
        SessionController controller = newController(caseRoot("remove-session"));
        controller.createIfNotExists(mainScope(), "session-1");
        List<SessionController.RemovedSession> removed = controller.removeSession("session-1");
        assertThat(removed).hasSize(1);
        assertThat(removed.getFirst().session().getSessionId()).isEqualTo("session-1");
        assertThat(controller.getScopeSessions(mainScope())).isEmpty();
    }

    private void caseRemoveSessionDeletesDisk() throws IOException {
        Path root = caseRoot("remove-session-disk");
        SessionController controller = newController(root);
        controller.createIfNotExists(mainScope(), "session-1");
        Path sessionDir = SessionPaths.sessionDir(root, "agent1", "session-1");
        assertThat(sessionDir).exists();
        controller.removeSession("session-1");
        assertThat(sessionDir).doesNotExist();
    }

    private void caseRemoveScopeSessions() throws IOException {
        SessionController controller = newController(caseRoot("remove-scope"));
        controller.createIfNotExists(mainScope(), "session-1");
        List<SessionMeta> removed = controller.removeScopeSessions(mainScope());
        assertThat(removed).hasSize(1);
        assertThat(controller.listMetas()).doesNotContainKey(mainScope());
    }

    private void caseRemoveAll() throws IOException {
        SessionController controller = newController(caseRoot("remove-all"));
        controller.createIfNotExists(mainScope(), "session-1");
        controller.removeAll();
        assertThat(controller.listMetas()).isEmpty();
        assertThat(controller.getScopeSessions(mainScope())).isEmpty();
    }

    private void caseCleanupScopeInactiveSessions() throws IOException {
        SessionController controller = newController(caseRoot("cleanup-inactive"));
        controller.createIfNotExists(mainScope(), "session-1");
        deactivateCurrent(controller, mainScope());
        controller.createIfNotExists(mainScope(), "session-2");
        List<SessionController.ScopeCleanupResult> cleaned = controller.cleanupScopeInactiveSessions(mainScope());
        assertThat(cleaned.getFirst().sessions()).extracting(SessionMeta::getSessionId).containsExactly("session-1");
    }

    private void caseCleanupNonexistentScope() throws IOException {
        SessionController controller = newController(caseRoot("cleanup-missing"));
        assertThatThrownBy(() -> controller.cleanupScopeInactiveSessions(mainScope()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    private void caseGetScopeMeta() throws IOException {
        SessionController controller = newController(caseRoot("get-meta"));
        controller.createIfNotExists(mainScope(), "session-1");
        assertThat(controller.getScopeMeta(mainScope()).getActiveSession()).isEqualTo("session-1");
    }

    private void caseGetScopeMetaEmpty() throws IOException {
        SessionController controller = newController(caseRoot("get-meta-empty"));
        ScopeSessionsMeta meta = controller.getScopeMeta(mainScope());
        assertThat(meta.getActiveSession()).isNull();
        assertThat(meta.getSessions()).isEmpty();
    }

    private void caseListMetas() throws IOException {
        SessionController controller = newController(caseRoot("list-metas"));
        controller.createIfNotExists(mainScope(), "session-1");
        Map<SessionScope, ScopeSessionsMeta> metas = controller.listMetas();
        assertThat(metas).containsKey(mainScope());
        metas.clear();
        assertThat(controller.listMetas()).containsKey(mainScope());
    }

    private void caseCreateNewScopeAutoCreated() throws IOException {
        SessionController controller = newController(caseRoot("create-new-scope"));
        assertThat(controller.listMetas()).doesNotContainKey(directScope());
        controller.createIfNotExists(directScope(), "session-1");
        assertThat(controller.listMetas()).containsKey(directScope());
        assertThat(controller.getScopeMeta(directScope()).getActiveSession()).isEqualTo("session-1");
    }

    private void caseMultiScopeIsolation() throws IOException {
        SessionController controller = newController(caseRoot("multi-scope"));
        controller.createIfNotExists(mainScope(), "session-1");
        controller.createIfNotExists(directScope(), "session-2");
        assertThat(controller.getScopeMeta(mainScope()).getActiveSession()).isEqualTo("session-1");
        assertThat(controller.getScopeMeta(directScope()).getActiveSession()).isEqualTo("session-2");
        assertThat(controller.getScopeActiveSession(mainScope()).orElseThrow())
                .isNotSameAs(controller.getScopeActiveSession(directScope()).orElseThrow());
    }

    private void caseFlushEmptyCache() throws IOException {
        assertThat(newController(caseRoot("flush-empty")).flush()).isTrue();
    }

    private void caseFlushSessionFailure() throws IOException {
        SessionController controller = failingController(caseRoot("flush-session-failure"));
        controller.createIfNotExists(mainScope(), "session-1");
        assertThat(controller.flushSession("session-1")).isFalse();
    }

    private void caseFlushScopeNonexistent() throws IOException {
        SessionController controller = newController(caseRoot("flush-scope-missing"));
        assertThat(controller.flushScope(directScope())).isTrue();
    }

    private void caseFlushScopeSessionFailure() throws IOException {
        SessionController controller = failingController(caseRoot("flush-scope-failure"));
        controller.createIfNotExists(mainScope(), "session-1");
        assertThat(controller.flushScope(mainScope())).isFalse();
    }

    private void caseFlushPartialFailure() throws IOException {
        SessionController controller = failingController(caseRoot("flush-partial-failure"));
        controller.createIfNotExists(mainScope(), "session-1");
        controller.createIfNotExists(directScope(), "session-2");
        assertThat(controller.flush()).isFalse();
    }

    private void caseLoadActiveOnly() throws IOException {
        Path root = caseRoot("load-active-only");
        writeMetaForTwoSessions(root, "session-2");
        SessionController controller = newController(root);
        assertThat(controller.load(true)).isTrue();
        assertThat(controller.getScopeSessions(mainScope())).extracting(ChainSession::getSessionId)
                .containsExactly("session-2");
    }

    private void caseLoadAllSessions() throws IOException {
        Path root = caseRoot("load-all");
        writeMetaForTwoSessions(root, "session-2");
        SessionController controller = newController(root);
        assertThat(controller.load(false)).isTrue();
        assertThat(controller.getScopeSessions(mainScope())).extracting(ChainSession::getSessionId)
                .containsExactly("session-1", "session-2");
    }

    private void caseLoadCorruptedMetaFile() throws IOException {
        Path root = caseRoot("load-corrupt");
        Files.createDirectories(SessionPaths.metaFile(root, "agent1").getParent());
        Files.writeString(SessionPaths.metaFile(root, "agent1"), "NOT VALID JSON{{{");
        SessionController controller = newController(root);
        assertThat(controller.load()).isFalse();
    }

    private void caseLoadPartialCorruptedScope() throws IOException {
        Path root = caseRoot("load-partial-corrupt");
        writeMetaForTwoSessions(root, "session-1");
        Map<String, Object> data = readMap(SessionPaths.metaFile(root, "agent1"));
        data.put("invalid_scope_key_no_agent_prefix", Map.of("sessions", List.of()));
        writeJson(SessionPaths.metaFile(root, "agent1"), data);
        SessionController controller = newController(root);
        assertThat(controller.load()).isTrue();
        assertThat(controller.listMetas()).containsKey(mainScope());
    }

    private void caseLoadScopeNonexistent() throws IOException {
        Path root = caseRoot("load-scope-missing");
        writeMetaForTwoSessions(root, "session-1");
        SessionController controller = newController(root);
        assertThat(controller.loadScope(directScope())).isTrue();
        assertThat(controller.listMetas()).doesNotContainKey(directScope());
    }

    private void caseLoadSessionIdempotent() throws IOException {
        Path root = caseRoot("load-idempotent");
        writeMetaForTwoSessions(root, "session-1");
        SessionController controller = newController(root);
        controller.load();
        int firstSize = controller.getScopeSessions(mainScope()).size();
        controller.load();
        assertThat(controller.getScopeSessions(mainScope())).hasSize(firstSize);
    }

    private void caseGetScopeActiveSessionAutoLoads() throws Exception {
        Path root = caseRoot("active-auto-load");
        writeMetaForTwoSessions(root, "session-1");
        SessionController controller = newController(root);
        controller.load();
        mutableSessionCache(controller).clear();
        assertThat(controller.getScopeActiveSession(mainScope())).isPresent()
                .get()
                .extracting(ChainSession::getSessionId)
                .isEqualTo("session-1");
    }

    private void caseGetScopeSessionsUnknownScope() throws IOException {
        SessionController controller = newController(caseRoot("sessions-unknown-scope"));
        assertThat(controller.getScopeSessions(directScope())).isEmpty();
    }

    private void caseGetScopeSessionsUnloadedNotInResult() throws IOException {
        Path root = caseRoot("sessions-unloaded");
        writeMetaForTwoSessions(root, "session-2");
        SessionController controller = newController(root);
        controller.load(true);
        assertThat(controller.getScopeSessions(mainScope())).extracting(ChainSession::getSessionId)
                .containsExactly("session-2");
    }

    private void caseActivateFromMetaMap() throws IOException {
        Path root = caseRoot("activate-from-meta");
        writeMetaForTwoSessions(root, "session-2");
        SessionController controller = newController(root);
        controller.load(true);
        controller.activateSession("session-1");
        assertThat(controller.getScopeMeta(mainScope()).getActiveSession()).isEqualTo("session-1");
        assertThat(controller.getScopeActiveSession(mainScope()).orElseThrow().isActive()).isTrue();
    }

    private void caseActivatePersists() throws IOException {
        Path root = caseRoot("activate-persists");
        writeMetaForTwoSessions(root, "session-2");
        SessionController controller = newController(root);
        controller.load(true);
        controller.activateSession("session-1");
        assertThat(SessionPaths.metaFile(root, "agent1")).exists();
        assertThat(mapValue(readMap(SessionPaths.metaFile(root, "agent1")).get("agent:agent1:main")))
                .containsEntry("active_session", "session-1");
    }

    private void caseRemoveSessionWithoutScope() throws IOException {
        SessionController controller = newController(caseRoot("remove-no-scope"));
        controller.createIfNotExists(mainScope(), "session-1");
        assertThat(controller.removeSession("session-1")).hasSize(1);
        assertThat(controller.getScopeSessions(mainScope())).isEmpty();
    }

    private void caseRemoveNonexistentSession() throws IOException {
        SessionController controller = newController(caseRoot("remove-missing"));
        assertThat(controller.removeSession("nonexistent")).isEmpty();
    }

    private void caseRemoveActiveSessionClearsActive() throws IOException {
        SessionController controller = newController(caseRoot("remove-active"));
        controller.createIfNotExists(mainScope(), "session-1");
        assertThat(controller.getScopeMeta(mainScope()).getActiveSession()).isEqualTo("session-1");
        controller.removeSession("session-1");
        assertThat(controller.getScopeMeta(mainScope()).getActiveSession()).isNull();
    }

    private void caseRemoveScopeSessionsNonexistent() throws IOException {
        SessionController controller = newController(caseRoot("remove-scope-missing"));
        assertThat(controller.removeScopeSessions(directScope())).isEmpty();
    }

    private void caseRemoveSessionUpdatesMetaFile() throws IOException {
        Path root = caseRoot("remove-updates-meta");
        SessionController controller = newController(root);
        controller.createIfNotExists(mainScope(), "session-1");
        controller.removeSession("session-1");
        Map<String, Object> scopeData = mapValue(readMap(SessionPaths.metaFile(root, "agent1")).get("agent:agent1:main"));
        assertThat((List<?>) scopeData.get("sessions")).isEmpty();
    }

    private void caseCleanupAllActive() throws IOException {
        SessionController controller = newController(caseRoot("cleanup-all-active"));
        controller.createIfNotExists(mainScope(), "session-1");
        List<SessionController.ScopeCleanupResult> cleaned = controller.cleanupScopeInactiveSessions(mainScope());
        assertThat(cleaned).hasSize(1);
        assertThat(cleaned.getFirst().sessions()).isEmpty();
    }

    private void caseCleanupAllInactive() throws IOException {
        SessionController controller = newController(caseRoot("cleanup-all-inactive"));
        controller.createIfNotExists(mainScope(), "session-1");
        deactivateCurrent(controller, mainScope());
        List<SessionController.ScopeCleanupResult> cleaned = controller.cleanupScopeInactiveSessions(mainScope());
        assertThat(cleaned.getFirst().sessions()).hasSize(1);
        assertThat(controller.getScopeSessions(mainScope())).isEmpty();
    }

    private void caseConcurrentCreateSameScope() throws Exception {
        SessionController controller = newController(caseRoot("concurrent-create"));
        CompletableFuture<SessionController.CreateIfNotExistsResult> first = CompletableFuture.supplyAsync(
                () -> controller.createIfNotExists(mainScope(), "session-1"));
        CompletableFuture<SessionController.CreateIfNotExistsResult> second = CompletableFuture.supplyAsync(
                () -> controller.createIfNotExists(mainScope(), "session-2"));
        List<Boolean> created = List.of(first.get().created(), second.get().created());
        assertThat(created).containsExactlyInAnyOrder(true, false);
    }

    private void caseConcurrentFlushAndLoad() throws Exception {
        SessionController controller = newController(caseRoot("concurrent-flush-load"));
        controller.createIfNotExists(mainScope(), "session-1");
        CompletableFuture<Boolean> flushed = CompletableFuture.supplyAsync(controller::flush);
        CompletableFuture<Boolean> loaded = CompletableFuture.supplyAsync(controller::load);
        assertThat(flushed.get()).isTrue();
        assertThat(loaded.get()).isTrue();
        assertThat(controller.getScopeSessions(mainScope())).extracting(ChainSession::getSessionId)
                .contains("session-1");
    }

    private void caseConcurrentRemoveAndGetSessions() throws IOException {
        SessionController controller = newController(caseRoot("concurrent-remove-get"));
        controller.createIfNotExists(mainScope(), "session-1");
        deactivateCurrent(controller, mainScope());
        controller.createIfNotExists(mainScope(), "session-2");
        List<SessionController.ScopeCleanupResult> cleaned = controller.cleanupScopeInactiveSessions(mainScope());
        List<ChainSession<?>> sessions = controller.getScopeSessions(mainScope());
        assertThat(cleaned).isNotEmpty();
        assertThat(sessions).allMatch(ChainSession::isActive);
    }

    private void caseSessionsJsonNoSensitiveData() throws IOException {
        Path root = caseRoot("no-sensitive-meta");
        SessionController controller = newController(root);
        controller.createIfNotExists(mainScope(), "session-1", Map.of("data", Map.of("secret", "sensitive_value")));
        controller.flush();
        assertThat(Files.readString(SessionPaths.metaFile(root, "agent1"))).doesNotContain("sensitive_value");
        assertThat(Files.readString(SessionPaths.stateFile(SessionPaths.sessionDir(root, "agent1", "session-1"))))
                .contains("sensitive_value");
    }

    private void casePathTraversalSessionId() throws IOException {
        Path root = caseRoot("path-traversal");
        SessionController controller = newController(root);
        SessionController.CreateIfNotExistsResult result = controller.createIfNotExists(mainScope(), "../escape_session");
        Path resolvedBase = root.resolve("agent1").toRealPath();
        Path resolvedSession = root.resolve("agent1").resolve("sessions").resolve(result.session().getSessionId())
                .toRealPath();
        assertThat(resolvedSession.toString()).startsWith(resolvedBase.toString());
    }

    private void caseSessionDirDeletedExternally() throws IOException {
        Path root = caseRoot("session-dir-deleted");
        SessionController controller = newController(root);
        ChainSession<?> session = controller.createIfNotExists(mainScope(), "session-1").session();
        Path sessionDir = SessionPaths.sessionDir(root, "agent1", "session-1");
        deleteRecursively(sessionDir);
        assertThat(session.flush()).isTrue();
        assertThat(sessionDir).exists();
    }

    private static SessionScope mainScope() {
        return new SessionScope(new MainScope(), null);
    }

    private static SessionScope directScope() {
        return new SessionScope(new MainScope(), new DirectSubject("user1"));
    }

    private DynamicTest parity(String pythonTestName, Executable executable) {
        return DynamicTest.dynamicTest("Python parity: " + pythonTestName, executable);
    }

    private Path caseRoot(String name) throws IOException {
        return Files.createDirectories(tempDir.resolve(name + "-" + System.nanoTime()));
    }

    private static SessionController newController(Path root) {
        return new SessionController("agent1", root, CONTAINER_TYPE);
    }

    private static SessionController failingController(Path root) {
        return new SessionController("agent1", root, FAILING_CONTAINER_TYPE);
    }

    private static void deactivateCurrent(SessionController controller, SessionScope scope) {
        controller.getScopeActiveSession(scope).ifPresent(session -> session.setActive(false));
        controller.getScopeMeta(scope).deactivateAllSessions();
    }

    private static void writeMetaForTwoSessions(Path root, String activeSession) throws IOException {
        String inactiveSession = "session-1".equals(activeSession) ? "session-2" : "session-1";
        writeJson(SessionPaths.metaFile(root, "agent1"), Map.of(
                "agent:agent1:main", Map.of(
                        "session_scope_key", "agent:agent1:main",
                        "active_session", activeSession,
                        "sessions", List.of(
                                Map.of(
                                        "session_id", inactiveSession,
                                        "created_at", 1.0D,
                                        "updated_at", 1.0D,
                                        "version", 1,
                                        "is_active", false,
                                        "data_container_type", CONTAINER_TYPE
                                ),
                                Map.of(
                                        "session_id", activeSession,
                                        "created_at", 2.0D,
                                        "updated_at", 2.0D,
                                        "version", 1,
                                        "is_active", true,
                                        "data_container_type", CONTAINER_TYPE
                                )
                        )
                )
        ));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ChainSession<?>> mutableSessionCache(SessionController controller) throws Exception {
        Field field = SessionController.class.getDeclaredField("sessionCache");
        field.setAccessible(true);
        return (Map<String, ChainSession<?>>) field.get(controller);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path current : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(current);
            }
        }
    }

    private static Map<String, Object> readMap(Path path) throws IOException {
        return OBJECT_MAPPER.readValue(path.toFile(), new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    private static void writeJson(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static final class MapContainer implements DataContainer {
        private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

        private MapContainer(Map<String, Object> values) {
            this.values.putAll(values);
        }

        @Override
        public Object get(Object key) {
            if (key == null) {
                return new LinkedHashMap<>(values);
            }
            return values.get(String.valueOf(key));
        }

        @Override
        public boolean update(Map<String, Object> data) {
            values.putAll(data);
            return true;
        }

        @Override
        public CompletionStage<Object> dump() {
            return CompletableFuture.completedFuture(new LinkedHashMap<>(values));
        }
    }

    private static final class FailingDumpContainer implements DataContainer {

        @Override
        public Object get(Object key) {
            return Map.of();
        }

        @Override
        public boolean update(Map<String, Object> data) {
            return true;
        }

        @Override
        public CompletionStage<Object> dump() {
            return CompletableFuture.failedFuture(new IllegalStateException("dump failed"));
        }
    }
}
