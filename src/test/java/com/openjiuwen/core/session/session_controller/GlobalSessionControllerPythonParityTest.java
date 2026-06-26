/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.callback.AsyncCallbackFramework;
import com.openjiuwen.core.runner.callback.CallbackUtils;
import com.openjiuwen.core.runner.callback.SessionEvents;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import org.junit.jupiter.api.DynamicTest;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Supplemental parity tests for the process-global session controller facade.
 *
 * <p>Mirrors Python's supplemental test module in
 * {@code tests/unit_tests/core/session/session_controller/test_global_controller.py}.</p>
 */
class GlobalSessionControllerPythonParityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String CONTAINER_TYPE = "global-controller-python-parity";
    private static final String FAILING_CONTAINER_TYPE = "global-controller-python-parity-failing";

    @TempDir
    Path tempDir;

    @TestFactory
    List<DynamicTest> pythonGlobalControllerParityCases() {
        registerContainers();
        return List.of(
                parity("TestGlobalSessionControllerSingleton::test_singleton", this::caseSingleton),
                parity("TestGlobalSessionConfig::test_defaults", this::caseConfigDefaults),
                parity("TestGlobalSessionConfig::test_custom", this::caseConfigCustom),
                parity("TestGlobalSessionControllerConfig::test_set_config_dict", this::caseSetConfigDict),
                parity("TestGlobalSessionControllerConfig::test_set_config_object", this::caseSetConfigObject),
                parity("TestGlobalSessionControllerAgentManagement::test_create_if_not_exist_agent_new",
                        this::caseCreateIfNotExistAgentNew),
                parity("TestGlobalSessionControllerAgentManagement::test_create_if_not_exist_agent_existing",
                        this::caseCreateIfNotExistAgentExisting),
                parity("TestGlobalSessionControllerAgentManagement::test_get_agent", this::caseGetAgent),
                parity("TestGlobalSessionControllerAgentManagement::test_remove_agent", this::caseRemoveAgent),
                parity("TestGlobalSessionControllerAgentManagement::test_remove_nonexistent_agent",
                        this::caseRemoveNonexistentAgent),
                parity("TestGlobalSessionControllerPersistence::test_load_agent", this::caseLoadAgent),
                parity("TestGlobalSessionControllerPersistence::test_flush_agent", this::caseFlushAgent),
                parity("TestGlobalSessionControllerPersistence::test_flush_all", this::caseFlushAll),
                parity("TestGlobalSessionControllerPersistence::test_flush_session", this::caseFlushSession),
                parity("TestGlobalSessionControllerPersistence::test_flush_scope", this::caseFlushScope),
                parity("TestGlobalSessionControllerCleanup::test_cleanup_agent_inactive_sessions",
                        this::caseCleanupAgentInactiveSessions),
                parity("TestGlobalSessionControllerCleanup::test_cleanup_agent_inactive_sessions_not_found",
                        this::caseCleanupAgentInactiveSessionsNotFound),
                parity("TestGlobalSessionControllerCleanup::test_remove_all", this::caseRemoveAll),
                parity("TestGlobalSessionControllerOrphanCleanup::test_cleanup_orphan_dirs_dry_run",
                        this::caseCleanupOrphanDirsDryRun),
                parity("TestGlobalSessionControllerOrphanCleanup::test_cleanup_orphan_dirs_delete",
                        this::caseCleanupOrphanDirsDelete),
                parity("TestGlobalSessionControllerMultiAgentIntegration::test_cross_agent_downstream_visibility",
                        this::caseCrossAgentDownstreamVisibility),
                parity("TestGlobalSessionControllerMultiAgentIntegration::test_load_all_multiple_agents",
                        this::caseLoadAllMultipleAgents),
                parity("TestGlobalSessionControllerMultiAgentIntegration::test_load_scope_across_agents",
                        this::caseLoadScopeAcrossAgents),
                parity("TestGlobalSessionControllerMultiAgentIntegration::test_flush_session_cross_agent",
                        this::caseFlushSessionCrossAgent),
                parity("TestGlobalSessionControllerMultiAgentIntegration::test_flush_session_not_found",
                        this::caseFlushSessionNotFound),
                parity("TestGlobalSessionControllerMultiAgentIntegration::test_flush_scope_cross_agent",
                        this::caseFlushScopeCrossAgent),
                parity("TestGlobalSessionControllerMultiAgentIntegration::test_remove_agent_deletes_disk_directory",
                        this::caseRemoveAgentDeletesDiskDirectory),
                parity("TestGlobalSessionControllerMultiAgentIntegration::test_cleanup_scope_inactive_sessions_cross_agent",
                        this::caseCleanupScopeInactiveSessionsCrossAgent),
                parity("TestGlobalSessionControllerMultiAgentIntegration::test_cleanup_orphan_files_all_agents",
                        this::caseCleanupOrphanFilesAllAgents),
                parity("TestGlobalSessionControllerMultiAgentIntegration::test_cleanup_orphan_files_no_orphans",
                        this::caseCleanupOrphanFilesNoOrphans),
                parity("TestGlobalSessionControllerConvenienceMethods::test_create_direct_session",
                        this::caseCreateDirectSession),
                parity("TestGlobalSessionControllerConvenienceMethods::test_create_direct_session_returns_existing",
                        this::caseCreateDirectSessionReturnsExisting),
                parity("TestGlobalSessionControllerConvenienceMethods::test_create_group_session",
                        this::caseCreateGroupSession),
                parity("TestGlobalSessionControllerConvenienceMethods::test_get_direct_session_data",
                        this::caseGetDirectSessionData),
                parity("TestGlobalSessionControllerConvenienceMethods::test_get_direct_session_data_no_agent",
                        this::caseGetDirectSessionDataNoAgent),
                parity("TestGlobalSessionControllerConvenienceMethods::test_get_direct_session_data_no_session",
                        this::caseGetDirectSessionDataNoSession),
                parity("TestGlobalSessionControllerConvenienceMethods::test_update_direct_session_data",
                        this::caseUpdateDirectSessionData),
                parity("TestGlobalSessionControllerConvenienceMethods::test_update_direct_session_data_no_agent",
                        this::caseUpdateDirectSessionDataNoAgent),
                parity("TestGlobalSessionControllerConvenienceMethods::test_add_direct_session_downstream",
                        this::caseAddDirectSessionDownstream),
                parity("TestGlobalSessionControllerConvenienceMethods::test_add_direct_session_downstream_missing_target",
                        this::caseAddDirectSessionDownstreamMissingTarget),
                parity("TestGlobalSessionControllerConvenienceMethods::test_add_direct_session_downstream_caller_not_exist",
                        this::caseAddDirectSessionDownstreamCallerNotExist),
                parity("TestGlobalSessionControllerConvenienceMethods::test_add_direct_session_downstream_caller_no_active_session",
                        this::caseAddDirectSessionDownstreamCallerNoActiveSession),
                parity("TestGlobalSessionControllerConvenienceMethods::test_add_direct_session_downstream_target_no_active_session",
                        this::caseAddDirectSessionDownstreamTargetNoActiveSession),
                parity("TestGlobalSessionControllerConvenienceMethods::test_cleanup_user_sessions",
                        this::caseCleanupUserSessions),
                parity("TestGlobalSessionControllerConvenienceMethods::test_cleanup_user_sessions_no_agent",
                        this::caseCleanupUserSessionsNoAgent),
                parity("TestGlobalSessionControllerConvenienceMethods::test_get_user_session_history",
                        this::caseGetUserSessionHistory),
                parity("TestGlobalSessionControllerConvenienceMethods::test_get_user_session_history_no_agent",
                        this::caseGetUserSessionHistoryNoAgent),
                parity("TestGlobalSessionControllerConvenienceMethods::test_flush_user_session",
                        this::caseFlushUserSession),
                parity("TestGlobalSessionControllerConvenienceMethods::test_flush_user_session_no_agent",
                        this::caseFlushUserSessionNoAgent),
                parity("TestGlobalSessionControllerVisualizeCallChain::test_visualize_call_chain_basic",
                        this::caseVisualizeCallChainBasic),
                parity("TestGlobalSessionControllerVisualizeCallChain::test_visualize_call_chain_agent_not_found",
                        this::caseVisualizeCallChainAgentNotFound),
                parity("TestGlobalSessionControllerVisualizeCallChain::test_visualize_call_chain_session_not_found",
                        this::caseVisualizeCallChainSessionNotFound),
                parity("TestGlobalSessionControllerVisualizeCallChain::test_visualize_call_chain_with_field_scopes",
                        this::caseVisualizeCallChainWithFieldScopes),
                parity("TestGlobalSessionControllerVisualizeCallChain::test_visualize_full_header_info",
                        this::caseVisualizeFullHeaderInfo),
                parity("TestGlobalSessionControllerVisualizeCallChain::test_visualize_inactive_session_status",
                        this::caseVisualizeInactiveSessionStatus),
                parity("TestGlobalSessionControllerVisualizeCallChain::test_visualize_no_downstreams",
                        this::caseVisualizeNoDownstreams),
                parity("TestGlobalSessionControllerVisualizeCallChain::test_visualize_single_downstream_all_fields",
                        this::caseVisualizeSingleDownstreamAllFields),
                parity("TestGlobalSessionControllerVisualizeCallChain::test_visualize_single_downstream_with_field_scopes",
                        this::caseVisualizeSingleDownstreamWithFieldScopes),
                parity("TestGlobalSessionControllerVisualizeCallChain::test_visualize_multiple_downstreams",
                        this::caseVisualizeMultipleDownstreams),
                parity("TestGlobalSessionControllerVisualizeCallChain::test_visualize_recursive_downstream_loaded",
                        this::caseVisualizeRecursiveDownstreamLoaded),
                parity("TestGlobalSessionControllerVisualizeCallChain::test_visualize_recursive_downstream_partial_loaded",
                        this::caseVisualizeRecursiveDownstreamPartialLoaded),
                parity("TestGlobalSessionControllerVisualizeCallChain::test_visualize_custom_depth",
                        this::caseVisualizeCustomDepth),
                parity("TestGlobalSessionControllerVisualizeCallChain::test_visualize_session_id_truncation",
                        this::caseVisualizeSessionIdTruncation),
                parity("TestGlobalSessionControllerVisualizeCallChain::test_visualize_direct_scope_in_header",
                        this::caseVisualizeDirectScopeInHeader),
                parity("TestGlobalSessionControllerVisualizeCallChain::test_visualize_group_scope_in_header",
                        this::caseVisualizeGroupScopeInHeader),
                parity("TestGlobalSessionControllerAdvanced::test_flush_agent_not_found",
                        this::caseFlushAgentNotFound),
                parity("TestGlobalSessionControllerAdvanced::test_load_all_no_directory",
                        this::caseLoadAllNoDirectory),
                parity("TestGlobalSessionControllerAdvanced::test_cleanup_orphan_files_agent_dir_exists_no_controller",
                        this::caseCleanupOrphanFilesAgentDirExistsNoController),
                parity("TestGlobalSessionControllerAdvanced::test_cleanup_orphan_files_corrupted_meta",
                        this::caseCleanupOrphanFilesCorruptedMeta),
                parity("TestGlobalSessionControllerAdvanced::test_remove_all_clears_base_directory",
                        this::caseRemoveAllClearsBaseDirectory),
                parity("TestGlobalSessionControllerCallbackRegistration::test_init_without_runner",
                        this::caseInitWithoutRunner),
                parity("TestGlobalSessionControllerSecurity::test_different_direct_subjects_isolated",
                        this::caseDifferentDirectSubjectsIsolated),
                parity("TestGlobalSessionControllerSecurity::test_downstream_unidirectional",
                        this::caseDownstreamUnidirectional),
                parity("TestGlobalSessionControllerSecurity::test_remove_session_complete_cleanup",
                        this::caseRemoveSessionCompleteCleanup),
                parity("TestGlobalSessionControllerCompatibility::test_load_old_meta_without_container_type",
                        this::caseLoadOldMetaWithoutContainerType),
                parity("TestGlobalSessionControllerResilience::test_flush_all_partial_failure",
                        this::caseFlushAllPartialFailure),
                parity("TestGlobalSessionControllerResilience::test_repeated_remove_same_agent",
                        this::caseRepeatedRemoveSameAgent),
                parity("TestGlobalSessionControllerResilience::test_sessions_json_deleted_externally",
                        this::caseSessionsJsonDeletedExternally)
        );
    }

    private void caseSingleton() throws IOException {
        GlobalSessionController first = configuredController("singleton");
        GlobalSessionController second = GlobalSessionController.getGlobalSessionController();
        assertThat(first).isSameAs(second);
    }

    private void caseConfigDefaults() {
        assertThat(new GlobalSessionConfig().getBasePath()).isEqualTo("./agents");
    }

    private void caseConfigCustom() {
        assertThat(new GlobalSessionConfig("/custom/path").getBasePath()).isEqualTo("/custom/path");
    }

    private void caseSetConfigDict() throws IOException {
        Path root = caseRoot("set-config-dict");
        GlobalSessionController controller = GlobalSessionController.getGlobalSessionController();
        controller.setConfig(Map.of("base_path", root.toString(), "data_container_type", "agent"));
        assertThat(controller.getBasePath()).isEqualTo(root);
        assertThat(controller.getDataContainerType()).isEqualTo("agent");
    }

    private void caseSetConfigObject() throws IOException {
        Path root = caseRoot("set-config-object");
        GlobalSessionController controller = GlobalSessionController.getGlobalSessionController();
        controller.setConfig(new GlobalSessionConfig(root.toString()));
        assertThat(controller.getBasePath()).isEqualTo(root);
        assertThat(controller.getDataContainerType()).isEqualTo(DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE);
    }

    private void caseCreateIfNotExistAgentNew() throws IOException {
        GlobalSessionController controller = configuredController("agent-new");
        GlobalSessionController.CreateAgentResult result = controller.createIfNotExistAgent("agent1");
        assertThat(result.created()).isTrue();
        assertThat(result.controller()).isInstanceOf(SessionController.class);
        assertThat(controller.getControllers()).containsKey("agent1");
    }

    private void caseCreateIfNotExistAgentExisting() throws IOException {
        GlobalSessionController controller = configuredController("agent-existing");
        controller.createIfNotExistAgent("agent1");
        assertThat(controller.createIfNotExistAgent("agent1").created()).isFalse();
    }

    private void caseGetAgent() throws IOException {
        GlobalSessionController controller = configuredController("get-agent");
        controller.createIfNotExistAgent("agent1");
        assertThat(controller.getAgent("agent1")).isPresent();
        assertThat(controller.getAgent("nonexistent")).isEmpty();
    }

    private void caseRemoveAgent() throws IOException {
        GlobalSessionController controller = configuredController("remove-agent");
        controller.createIfNotExistAgent("agent1");
        assertThat(controller.removeAgent("agent1")).isTrue();
        assertThat(controller.getControllers()).doesNotContainKey("agent1");
    }

    private void caseRemoveNonexistentAgent() throws IOException {
        assertThat(configuredController("remove-missing").removeAgent("nonexistent")).isFalse();
    }

    private void caseLoadAgent() throws Exception {
        GlobalSessionController controller = configuredController("load-agent");
        SessionController sessionController = controller.createIfNotExistAgent("agent1").controller();
        sessionController.createIfNotExists(mainScope(), "session-1");
        sessionController.flush();
        mutableControllers(controller).clear();

        assertThat(controller.loadAgent("agent1")).isTrue();
        assertThat(controller.getControllers()).containsKey("agent1");
    }

    private void caseFlushAgent() throws IOException {
        GlobalSessionController controller = configuredController("flush-agent");
        SessionController sessionController = controller.createIfNotExistAgent("agent1").controller();
        sessionController.createIfNotExists(mainScope(), "session-1");
        assertThat(controller.flushAgent("agent1")).isTrue();
        assertThat(SessionPaths.metaFile(controller.getBasePath(), "agent1")).exists();
    }

    private void caseFlushAll() throws IOException {
        GlobalSessionController controller = configuredController("flush-all");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        controller.flushAll();
        assertThat(SessionPaths.metaFile(controller.getBasePath(), "agent1")).exists();
    }

    private void caseFlushSession() throws IOException {
        GlobalSessionController controller = configuredController("flush-session");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        assertThatCode(() -> controller.flushSession("session-1")).doesNotThrowAnyException();
    }

    private void caseFlushScope() throws IOException {
        GlobalSessionController controller = configuredController("flush-scope");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        assertThatCode(() -> controller.flushScope(mainScope())).doesNotThrowAnyException();
    }

    private void caseCleanupAgentInactiveSessions() throws IOException {
        GlobalSessionController controller = configuredController("cleanup-agent-inactive");
        SessionController sessionController = controller.createIfNotExistAgent("agent1").controller();
        sessionController.createIfNotExists(mainScope(), "session-1");
        deactivateCurrent(sessionController, mainScope());
        sessionController.createIfNotExists(mainScope(), "session-2");
        assertThat(controller.cleanupAgentInactiveSessions("agent1")).containsKey("agent1");
    }

    private void caseCleanupAgentInactiveSessionsNotFound() throws IOException {
        GlobalSessionController controller = configuredController("cleanup-agent-missing");
        assertThatThrownBy(() -> controller.cleanupAgentInactiveSessions("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    private void caseRemoveAll() throws IOException {
        GlobalSessionController controller = configuredController("remove-all");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        controller.removeAll();
        assertThat(controller.getControllers()).isEmpty();
    }

    private void caseCleanupOrphanDirsDryRun() throws IOException {
        GlobalSessionController controller = configuredController("orphan-dry-run");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        Path orphan = orphanDir(controller, "agent1", "orphan-session");
        Map<String, List<String>> result = controller.cleanupOrphanFiles("agent1", true);
        assertThat(result).containsKey("agent1");
        assertThat(result.get("agent1")).contains("orphan-session");
        assertThat(orphan).exists();
    }

    private void caseCleanupOrphanDirsDelete() throws IOException {
        GlobalSessionController controller = configuredController("orphan-delete");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        Path orphan = orphanDir(controller, "agent1", "orphan-session");
        Map<String, List<String>> result = controller.cleanupOrphanFiles("agent1", false);
        assertThat(result.get("agent1")).contains("orphan-session");
        assertThat(orphan).doesNotExist();
    }

    private void caseCrossAgentDownstreamVisibility() throws IOException {
        GlobalSessionController controller = configuredController("cross-agent");
        SessionController first = controller.createIfNotExistAgent("agent1").controller();
        SessionController second = controller.createIfNotExistAgent("agent2").controller();
        ChainSession<?> sessionOne = first.createIfNotExists(mainScope(), "session-1").session();
        ChainSession<?> sessionTwo = second.createIfNotExists(mainScope(), "session-2").session();
        sessionOne.addDownstream("agent2", "session-2", new SharingPolicy(Permission.READ, Set.of()));

        assertThat(sessionOne.canSee("agent2", "session-2")).isTrue();
        assertThat(sessionTwo.canSee("agent1", "session-1")).isFalse();
    }

    private void caseLoadAllMultipleAgents() throws Exception {
        GlobalSessionController controller = configuredController("load-all-multiple");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        controller.createIfNotExistAgent("agent2").controller().createIfNotExists(mainScope(), "session-2");
        controller.flushAll();
        mutableControllers(controller).clear();

        controller.loadAll();
        assertThat(controller.getControllers()).containsKeys("agent1", "agent2");
    }

    private void caseLoadScopeAcrossAgents() throws Exception {
        GlobalSessionController controller = configuredController("load-scope-across");
        SessionScope directScope = directScope("user1");
        SessionController first = controller.createIfNotExistAgent("agent1").controller();
        SessionController second = controller.createIfNotExistAgent("agent2").controller();
        first.createIfNotExists(directScope, "session-d1");
        first.createIfNotExists(mainScope(), "session-m1");
        second.createIfNotExists(directScope, "session-d2");
        controller.flushAll();
        mutableControllers(controller).clear();

        controller.loadAll();
        controller.loadScope(directScope);
        assertThat(controller.getAgent("agent1")).isPresent();
        assertThat(controller.getAgent("agent2")).isPresent();
    }

    private void caseFlushSessionCrossAgent() throws IOException {
        GlobalSessionController controller = configuredController("flush-session-cross-agent");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "shared-session-id");
        controller.flushSession("shared-session-id");
        assertThat(SessionPaths.metaFile(controller.getBasePath(), "agent1")).exists();
    }

    private void caseFlushSessionNotFound() throws IOException {
        GlobalSessionController controller = configuredController("flush-session-not-found");
        controller.createIfNotExistAgent("agent1");
        assertThatCode(() -> controller.flushSession("nonexistent-session")).doesNotThrowAnyException();
    }

    private void caseFlushScopeCrossAgent() throws IOException {
        GlobalSessionController controller = configuredController("flush-scope-cross-agent");
        SessionScope directScope = directScope("user1");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(directScope, "session-d1");
        controller.createIfNotExistAgent("agent2").controller().createIfNotExists(directScope, "session-d2");
        controller.flushScope(directScope);
        assertThat(SessionPaths.metaFile(controller.getBasePath(), "agent1")).exists();
        assertThat(SessionPaths.metaFile(controller.getBasePath(), "agent2")).exists();
    }

    private void caseRemoveAgentDeletesDiskDirectory() throws IOException {
        GlobalSessionController controller = configuredController("remove-agent-disk");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        controller.flushAgent("agent1");
        Path agentDir = SessionPaths.agentDir(controller.getBasePath(), "agent1");
        assertThat(agentDir).exists();
        assertThat(controller.removeAgent("agent1")).isTrue();
        assertThat(agentDir).doesNotExist();
        assertThat(controller.getAgent("agent1")).isEmpty();
    }

    private void caseCleanupScopeInactiveSessionsCrossAgent() throws IOException {
        GlobalSessionController controller = configuredController("cleanup-scope-cross-agent");
        SessionScope directScope = directScope("user1");
        SessionController first = controller.createIfNotExistAgent("agent1").controller();
        SessionController second = controller.createIfNotExistAgent("agent2").controller();
        first.createIfNotExists(directScope, "session-d1");
        second.createIfNotExists(directScope, "session-d2");
        deactivateCurrent(first, directScope);
        first.createIfNotExists(directScope, "session-d1-new");
        deactivateCurrent(second, directScope);
        second.createIfNotExists(directScope, "session-d2-new");

        Map<String, List<SessionMeta>> result = controller.cleanupScopeInactiveSessions(directScope);
        assertThat(result).containsKeys("agent1", "agent2");
    }

    private void caseCleanupOrphanFilesAllAgents() throws IOException {
        GlobalSessionController controller = configuredController("orphan-all-agents");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        controller.createIfNotExistAgent("agent2").controller().createIfNotExists(mainScope(), "session-2");
        controller.flushAll();
        orphanDir(controller, "agent1", "orphan-agent1");
        orphanDir(controller, "agent2", "orphan-agent2");

        Map<String, List<String>> result = controller.cleanupOrphanFiles(null, true);
        assertThat(result).containsKeys("agent1", "agent2");
        assertThat(result.get("agent1")).contains("orphan-agent1");
        assertThat(result.get("agent2")).contains("orphan-agent2");
    }

    private void caseCleanupOrphanFilesNoOrphans() throws IOException {
        GlobalSessionController controller = configuredController("orphan-none");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        controller.flushAll();
        assertThat(controller.cleanupOrphanFiles(null, true)).isEmpty();
    }

    private void caseCreateDirectSession() throws IOException {
        configuredController("create-direct");
        GlobalSessionController.CreateSessionResult result = GlobalSessionController.createDirectSession(
                "agent1", "user1", "session-1", mapData()
        );
        assertThat(result.created()).isTrue();
        assertThat(result.session().getSessionId()).isEqualTo("session-1");
        assertThat(result.session().getSessionScope().toString()).contains("direct:user1");
    }

    private void caseCreateDirectSessionReturnsExisting() throws IOException {
        configuredController("create-direct-existing");
        GlobalSessionController.CreateSessionResult first = GlobalSessionController.createDirectSession(
                "agent1", "user1", "session-1", mapData()
        );
        GlobalSessionController.CreateSessionResult second = GlobalSessionController.createDirectSession(
                "agent1", "user1", "session-2", mapData()
        );
        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.session().getSessionId()).isEqualTo("session-1");
    }

    private void caseCreateGroupSession() throws IOException {
        configuredController("create-group");
        GlobalSessionController.CreateSessionResult result = GlobalSessionController.createGroupSession(
                "agent1", "grp1", "session-1", mapData()
        );
        assertThat(result.created()).isTrue();
        assertThat(result.session().getSessionId()).isEqualTo("session-1");
        assertThat(result.session().getSessionScope().toString()).contains("group:grp1");
    }

    private void caseGetDirectSessionData() throws IOException {
        configuredController("get-direct-data");
        GlobalSessionController.createDirectSession("agent1", "user1", "session-1", mapData());
        assertThat(GlobalSessionController.getDirectSessionData("agent1", "user1")).isPresent();
    }

    private void caseGetDirectSessionDataNoAgent() throws IOException {
        configuredController("get-direct-data-no-agent");
        assertThat(GlobalSessionController.getDirectSessionData("nonexistent", "user1")).isEmpty();
    }

    private void caseGetDirectSessionDataNoSession() throws IOException {
        configuredController("get-direct-data-no-session").createIfNotExistAgent("agent1");
        assertThat(GlobalSessionController.getDirectSessionData("agent1", "user1")).isEmpty();
    }

    private void caseUpdateDirectSessionData() throws IOException {
        configuredController("update-direct-data");
        GlobalSessionController.createDirectSession("agent1", "user1", "session-1", mapData());
        assertThat(GlobalSessionController.updateDirectSessionData("agent1", "user1",
                Map.of("new_key", "new_value"))).isTrue();
    }

    private void caseUpdateDirectSessionDataNoAgent() throws IOException {
        configuredController("update-direct-no-agent");
        assertThat(GlobalSessionController.updateDirectSessionData("nonexistent", "user1", Map.of())).isFalse();
    }

    private void caseAddDirectSessionDownstream() throws Exception {
        GlobalSessionController controller = configuredController("add-direct-downstream");
        GlobalSessionController.createDirectSession("agent1", "user1", "session-1", mapData());
        GlobalSessionController.createDirectSession("agent2", "user2", "session-2", mapData());
        assertThat(GlobalSessionController.addDirectSessionDownstream("agent1", "user1", "agent2", "user2")).isTrue();
        ChainSession<?> caller = mutableSessionCache(controller.getAgent("agent1").orElseThrow()).get("session-1");
        assertThat(caller.hasDownstream("agent2", "session-2")).isTrue();
    }

    private void caseAddDirectSessionDownstreamMissingTarget() throws IOException {
        configuredController("add-direct-missing-target");
        GlobalSessionController.createDirectSession("agent1", "user1", "session-1", mapData());
        assertThat(GlobalSessionController.addDirectSessionDownstream("agent1", "user1", "agent2", "user2"))
                .isFalse();
    }

    private void caseAddDirectSessionDownstreamCallerNotExist() throws IOException {
        configuredController("add-direct-missing-caller");
        GlobalSessionController.createDirectSession("agent2", "user2", "session-2", mapData());
        assertThat(GlobalSessionController.addDirectSessionDownstream("nonexistent", "user1", "agent2", "user2"))
                .isFalse();
    }

    private void caseAddDirectSessionDownstreamCallerNoActiveSession() throws IOException {
        GlobalSessionController controller = configuredController("add-direct-caller-no-session");
        GlobalSessionController.createDirectSession("agent2", "user2", "session-2", mapData());
        controller.createIfNotExistAgent("agent1");
        assertThat(GlobalSessionController.addDirectSessionDownstream("agent1", "user1", "agent2", "user2"))
                .isFalse();
    }

    private void caseAddDirectSessionDownstreamTargetNoActiveSession() throws IOException {
        GlobalSessionController controller = configuredController("add-direct-target-no-session");
        GlobalSessionController.createDirectSession("agent1", "user1", "session-1", mapData());
        controller.createIfNotExistAgent("agent2");
        assertThat(GlobalSessionController.addDirectSessionDownstream("agent1", "user1", "agent2", "user2"))
                .isFalse();
    }

    private void caseCleanupUserSessions() throws IOException {
        GlobalSessionController controller = configuredController("cleanup-user");
        GlobalSessionController.createDirectSession("agent1", "user1", "session-1", mapData());
        SessionController sessionController = controller.getAgent("agent1").orElseThrow();
        SessionScope direct = directScope("user1");
        deactivateCurrent(sessionController, direct);
        sessionController.createIfNotExists(direct, "session-2", mapData());

        assertThat(GlobalSessionController.cleanupUserSessions("agent1", "user1")).isNotEmpty();
    }

    private void caseCleanupUserSessionsNoAgent() throws IOException {
        configuredController("cleanup-user-no-agent");
        assertThat(GlobalSessionController.cleanupUserSessions("nonexistent", "user1")).isEmpty();
    }

    private void caseGetUserSessionHistory() throws IOException {
        configuredController("history");
        GlobalSessionController.createDirectSession("agent1", "user1", "session-1", mapData());
        assertThat(GlobalSessionController.getUserSessionHistory("agent1", "user1")).hasSizeGreaterThanOrEqualTo(1);
    }

    private void caseGetUserSessionHistoryNoAgent() throws IOException {
        configuredController("history-no-agent");
        assertThat(GlobalSessionController.getUserSessionHistory("nonexistent", "user1")).isEmpty();
    }

    private void caseFlushUserSession() throws IOException {
        configuredController("flush-user");
        GlobalSessionController.createDirectSession("agent1", "user1", "session-1", mapData());
        assertThat(GlobalSessionController.flushUserSession("agent1", "user1")).isTrue();
    }

    private void caseFlushUserSessionNoAgent() throws IOException {
        configuredController("flush-user-no-agent");
        assertThat(GlobalSessionController.flushUserSession("nonexistent", "user1")).isFalse();
    }

    private void caseVisualizeCallChainBasic() throws IOException {
        GlobalSessionController controller = configuredController("visualize-basic");
        ChainSession<?> session = controller.createIfNotExistAgent("agent1").controller()
                .createIfNotExists(mainScope(), "session-1").session();
        session.addDownstream("agent2", "session-2", new SharingPolicy(Permission.READ, Set.of()));

        String result = GlobalSessionController.visualizeCallChain("agent1", "session-1");
        assertThat(result).contains("ChainSession Call Chain Visualization", "agent2");
    }

    private void caseVisualizeCallChainAgentNotFound() throws IOException {
        configuredController("visualize-no-agent");
        assertThat(GlobalSessionController.visualizeCallChain("nonexistent", "session-1")).contains("not found");
    }

    private void caseVisualizeCallChainSessionNotFound() throws IOException {
        configuredController("visualize-no-session").createIfNotExistAgent("agent1");
        assertThat(GlobalSessionController.visualizeCallChain("agent1", "nonexistent")).contains("not found");
    }

    private void caseVisualizeCallChainWithFieldScopes() throws IOException {
        GlobalSessionController controller = configuredController("visualize-fields");
        ChainSession<?> session = controller.createIfNotExistAgent("agent1").controller()
                .createIfNotExists(mainScope(), "session-1").session();
        session.addDownstream("agent2", "session-2", new SharingPolicy(Permission.READ, Set.of("field1", "field2")));

        assertThat(GlobalSessionController.visualizeCallChain("agent1", "session-1"))
                .containsAnyOf("field1", "field2");
    }

    private void caseVisualizeFullHeaderInfo() throws IOException {
        GlobalSessionController controller = configuredController("visualize-header");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        String[] lines = GlobalSessionController.visualizeCallChain("agent1", "session-1").split("\\n");
        assertThat(lines[0]).isEqualTo("ChainSession Call Chain Visualization");
        assertThat(lines[1]).isEqualTo("=".repeat(50));
        assertThat(lines[2]).contains("agent:agent1:main", "session-");
        assertThat(lines[3]).isEqualTo("Status: Active");
        assertThat(lines[4]).isEmpty();
        assertThat(lines[5]).contains("Call chain relationships (depth: 3)");
        assertThat(lines[6]).isEqualTo("-".repeat(50));
    }

    private void caseVisualizeInactiveSessionStatus() throws IOException {
        GlobalSessionController controller = configuredController("visualize-inactive");
        ChainSession<?> session = controller.createIfNotExistAgent("agent1").controller()
                .createIfNotExists(mainScope(), "session-1").session();
        session.setActive(false);
        assertThat(GlobalSessionController.visualizeCallChain("agent1", "session-1")).contains("Status: Inactive");
    }

    private void caseVisualizeNoDownstreams() throws IOException {
        GlobalSessionController controller = configuredController("visualize-no-downstream");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        String[] lines = GlobalSessionController.visualizeCallChain("agent1", "session-1").split("\\n");
        int headerEndIndex = java.util.Arrays.asList(lines).indexOf("-".repeat(50));
        assertThat(List.of(lines).subList(headerEndIndex + 1, lines.length))
                .allMatch(line -> line.trim().isEmpty());
    }

    private void caseVisualizeSingleDownstreamAllFields() throws IOException {
        GlobalSessionController controller = configuredController("visualize-single-all");
        ChainSession<?> session = controller.createIfNotExistAgent("agent1").controller()
                .createIfNotExists(mainScope(), "session-1").session();
        session.addDownstream("agent2", "session-2", new SharingPolicy(Permission.READ, Set.of()));

        assertThat(GlobalSessionController.visualizeCallChain("agent1", "session-1"))
                .contains("agent2", "Permissions: READ", "Field scope: All fields", "(not loaded)");
    }

    private void caseVisualizeSingleDownstreamWithFieldScopes() throws IOException {
        GlobalSessionController controller = configuredController("visualize-single-fields");
        ChainSession<?> session = controller.createIfNotExistAgent("agent1").controller()
                .createIfNotExists(mainScope(), "session-1").session();
        session.addDownstream("agent2", "session-2", new SharingPolicy(Permission.READ, Set.of("name", "age")));

        assertThat(GlobalSessionController.visualizeCallChain("agent1", "session-1"))
                .contains("Permissions: READ", "name", "age")
                .doesNotContain("All fields");
    }

    private void caseVisualizeMultipleDownstreams() throws IOException {
        GlobalSessionController controller = configuredController("visualize-multiple");
        ChainSession<?> session = controller.createIfNotExistAgent("agent1").controller()
                .createIfNotExists(mainScope(), "session-1").session();
        session.addDownstream("agent2", "session-2", new SharingPolicy(Permission.READ, Set.of()));
        session.addDownstream("agent3", "session-3", new SharingPolicy(Permission.READ, Set.of()));

        String result = GlobalSessionController.visualizeCallChain("agent1", "session-1");
        assertThat(result).contains("agent2", "agent3");
        assertThat(result.lines().filter(line -> line.contains("agent")).count()).isGreaterThanOrEqualTo(3);
    }

    private void caseVisualizeRecursiveDownstreamLoaded() throws IOException {
        GlobalSessionController controller = configuredController("visualize-recursive-loaded");
        ChainSession<?> first = controller.createIfNotExistAgent("agent1").controller()
                .createIfNotExists(mainScope(), "session-1").session();
        ChainSession<?> second = controller.createIfNotExistAgent("agent2").controller()
                .createIfNotExists(mainScope(), "session-2").session();
        controller.createIfNotExistAgent("agent3").controller().createIfNotExists(mainScope(), "session-3");
        first.addDownstream("agent2", "session-2", new SharingPolicy(Permission.READ, Set.of()));
        second.addDownstream("agent3", "session-3", new SharingPolicy(Permission.READ, Set.of()));

        assertThat(GlobalSessionController.visualizeCallChain("agent1", "session-1", 3))
                .contains("agent2", "agent3")
                .doesNotContain("(not loaded)");
    }

    private void caseVisualizeRecursiveDownstreamPartialLoaded() throws IOException {
        GlobalSessionController controller = configuredController("visualize-recursive-partial");
        ChainSession<?> first = controller.createIfNotExistAgent("agent1").controller()
                .createIfNotExists(mainScope(), "session-1").session();
        ChainSession<?> second = controller.createIfNotExistAgent("agent2").controller()
                .createIfNotExists(mainScope(), "session-2").session();
        first.addDownstream("agent2", "session-2", new SharingPolicy(Permission.READ, Set.of()));
        second.addDownstream("agent3", "session-3", new SharingPolicy(Permission.READ, Set.of()));

        assertThat(GlobalSessionController.visualizeCallChain("agent1", "session-1", 3))
                .contains("agent2", "agent3", "(not loaded)");
    }

    private void caseVisualizeCustomDepth() throws IOException {
        GlobalSessionController controller = configuredController("visualize-depth");
        ChainSession<?> first = controller.createIfNotExistAgent("agent1").controller()
                .createIfNotExists(mainScope(), "session-1").session();
        ChainSession<?> second = controller.createIfNotExistAgent("agent2").controller()
                .createIfNotExists(mainScope(), "session-2").session();
        controller.createIfNotExistAgent("agent3").controller().createIfNotExists(mainScope(), "session-3");
        first.addDownstream("agent2", "session-2", new SharingPolicy(Permission.READ, Set.of()));
        second.addDownstream("agent3", "session-3", new SharingPolicy(Permission.READ, Set.of()));

        assertThat(GlobalSessionController.visualizeCallChain("agent1", "session-1", 1))
                .contains("agent2", "Call chain relationships (depth: 1)");
    }

    private void caseVisualizeSessionIdTruncation() throws IOException {
        GlobalSessionController controller = configuredController("visualize-truncate");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "abcdef1234567890");
        assertThat(GlobalSessionController.visualizeCallChain("agent1", "abcdef1234567890"))
                .contains("abcdef12...");
    }

    private void caseVisualizeDirectScopeInHeader() throws IOException {
        GlobalSessionController controller = configuredController("visualize-direct");
        controller.createIfNotExistAgent("agent1").controller()
                .createIfNotExists(directScope("user1"), "session-1");
        assertThat(GlobalSessionController.visualizeCallChain("agent1", "session-1"))
                .contains("agent:agent1:main:direct:user1");
    }

    private void caseVisualizeGroupScopeInHeader() throws IOException {
        GlobalSessionController controller = configuredController("visualize-group");
        controller.createIfNotExistAgent("agent1").controller()
                .createIfNotExists(SessionScopeFactory.createGroup("group1"), "session-1");
        assertThat(GlobalSessionController.visualizeCallChain("agent1", "session-1"))
                .contains("agent:agent1:main:group:group1");
    }

    private void caseFlushAgentNotFound() throws IOException {
        assertThatCode(() -> configuredController("flush-agent-missing").flushAgent("nonexistent"))
                .doesNotThrowAnyException();
    }

    private void caseLoadAllNoDirectory() throws IOException {
        GlobalSessionController controller = GlobalSessionController.getGlobalSessionController();
        controller.setConfig(Map.of("base_path", caseRoot("missing-base").resolve("nonexistent").toString(),
                "data_container_type", CONTAINER_TYPE));
        controller.loadAll();
        assertThat(controller.getControllers()).isEmpty();
    }

    private void caseCleanupOrphanFilesAgentDirExistsNoController() throws IOException {
        GlobalSessionController controller = configuredController("orphan-no-controller");
        Path sessionsDir = SessionPaths.sessionsDir(controller.getBasePath(), "orphan_agent");
        Path orphan = sessionsDir.resolve("orphan-s1");
        Files.createDirectories(orphan);
        Files.writeString(SessionPaths.stateFile(orphan), "{}");
        writeJson(SessionPaths.metaFile(controller.getBasePath(), "orphan_agent"), Map.of());

        assertThat(controller.cleanupOrphanFiles("orphan_agent", true)).containsKey("orphan_agent");
    }

    private void caseCleanupOrphanFilesCorruptedMeta() throws IOException {
        GlobalSessionController controller = configuredController("orphan-corrupt-meta");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        controller.flushAll();
        Files.writeString(SessionPaths.metaFile(controller.getBasePath(), "agent1"), "NOT VALID JSON{{{{");
        orphanDir(controller, "agent1", "orphan-s1");

        Map<String, List<String>> result = controller.cleanupOrphanFiles(null, true);
        assertThat(result).containsKey("agent1");
        assertThat(result.get("agent1")).contains("orphan-s1");
    }

    private void caseRemoveAllClearsBaseDirectory() throws IOException {
        GlobalSessionController controller = configuredController("remove-all-base");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        controller.flushAll();
        Path basePath = controller.getBasePath();
        assertThat(basePath).exists();
        controller.removeAll();
        assertThat(basePath).doesNotExist();
    }

    private void caseInitWithoutRunner() {
        GlobalSessionController.resetForTesting();
        assertThat(GlobalSessionController.getGlobalSessionController()).isNotNull();
    }

    private void caseDifferentDirectSubjectsIsolated() throws IOException {
        configuredController("direct-isolated");
        GlobalSessionController.CreateSessionResult first = GlobalSessionController.createDirectSession(
                "agent1", "user1", "session-u1", mapData()
        );
        GlobalSessionController.CreateSessionResult second = GlobalSessionController.createDirectSession(
                "agent1", "user2", "session-u2", mapData()
        );
        assertThat(first.session().getSessionScope()).isNotEqualTo(second.session().getSessionScope());
        assertThat(first.session().canSee("agent1", "session-u2")).isFalse();
        assertThat(second.session().canSee("agent1", "session-u1")).isFalse();
    }

    private void caseDownstreamUnidirectional() throws IOException {
        GlobalSessionController controller = configuredController("downstream-unidirectional");
        SessionController first = controller.createIfNotExistAgent("agent1").controller();
        SessionController second = controller.createIfNotExistAgent("agent2").controller();
        ChainSession<?> sessionOne = first.createIfNotExists(mainScope(), "session-1").session();
        ChainSession<?> sessionTwo = second.createIfNotExists(mainScope(), "session-2").session();
        sessionOne.addDownstream("agent2", "session-2", new SharingPolicy(Permission.READ, Set.of()));
        assertThat(sessionOne.canSee("agent2", "session-2")).isTrue();
        assertThat(sessionTwo.canSee("agent1", "session-1")).isFalse();
    }

    private void caseRemoveSessionCompleteCleanup() throws IOException {
        GlobalSessionController controller = configuredController("remove-complete-cleanup");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        controller.flushAll();
        Path agentDir = SessionPaths.agentDir(controller.getBasePath(), "agent1");
        assertThat(agentDir).exists();
        controller.removeAgent("agent1");
        assertThat(agentDir).doesNotExist();
    }

    private void caseLoadOldMetaWithoutContainerType() throws Exception {
        GlobalSessionController controller = configuredController("old-meta-no-container");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        controller.flushAll();
        Path metaFile = SessionPaths.metaFile(controller.getBasePath(), "agent1");
        Map<String, Object> metaData = readMap(metaFile);
        for (Object scopeValue : metaData.values()) {
            Map<String, Object> scopeData = mapValue(scopeValue);
            for (Object sessionValue : (List<?>) scopeData.get("sessions")) {
                mapValue(sessionValue).remove("data_container_type");
            }
        }
        writeJson(metaFile, metaData);
        mutableControllers(controller).clear();

        assertThat(controller.loadAgent("agent1")).isTrue();
        assertThat(controller.getAgent("agent1")).isPresent();
    }

    private void caseFlushAllPartialFailure() throws Exception {
        GlobalSessionController controller = configuredController("flush-partial-failure");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        SessionController failing = new SessionController("agent2", controller.getBasePath(), FAILING_CONTAINER_TYPE);
        failing.createIfNotExists(mainScope(), "session-2");
        mutableControllers(controller).put("agent2", failing);

        assertThatCode(controller::flushAll).doesNotThrowAnyException();
        assertThat(SessionPaths.metaFile(controller.getBasePath(), "agent1")).exists();
    }

    private void caseRepeatedRemoveSameAgent() throws IOException {
        GlobalSessionController controller = configuredController("repeated-remove");
        controller.createIfNotExistAgent("agent1");
        assertThat(controller.removeAgent("agent1")).isTrue();
        assertThat(controller.removeAgent("agent1")).isFalse();
    }

    private void caseSessionsJsonDeletedExternally() throws Exception {
        GlobalSessionController controller = configuredController("sessions-json-deleted");
        controller.createIfNotExistAgent("agent1").controller().createIfNotExists(mainScope(), "session-1");
        controller.flushAll();
        Files.deleteIfExists(SessionPaths.metaFile(controller.getBasePath(), "agent1"));
        mutableControllers(controller).clear();
        assertThatCode(() -> controller.loadAgent("agent1")).doesNotThrowAnyException();
        assertThat(controller.getAgent("agent1")).isPresent();
    }

    private DynamicTest parity(String pythonTestName, Executable executable) {
        return DynamicTest.dynamicTest("Python parity: " + pythonTestName, () -> {
            resetGlobalState();
            executable.execute();
        });
    }

    private GlobalSessionController configuredController(String name) throws IOException {
        GlobalSessionController controller = GlobalSessionController.getGlobalSessionController();
        controller.setConfig(Map.of(
                "base_path", caseRoot(name).toString(),
                "data_container_type", CONTAINER_TYPE
        ));
        return controller;
    }

    private void resetGlobalState() {
        GlobalSessionController.resetForTesting();
        CallbackUtils.setCallbackFramework(new AsyncCallbackFramework());
        RunnerConfig.setRunnerConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG.copy());
        registerContainers();
    }

    private static void registerContainers() {
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
                return CompletableFuture.completedFuture(new MapContainer(Map.of()));
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

    private static SessionScope mainScope() {
        return SessionScopeFactory.createMain();
    }

    private static SessionScope directScope(String userId) {
        return SessionScopeFactory.createDirect(userId);
    }

    private Map<String, Object> mapData() {
        return Map.of("data", Map.of("key", "value"));
    }

    private Path caseRoot(String name) throws IOException {
        return Files.createDirectories(tempDir.resolve(name + "-" + System.nanoTime()));
    }

    private static void deactivateCurrent(SessionController controller, SessionScope scope) {
        controller.getScopeActiveSession(scope).ifPresent(session -> session.setActive(false));
        controller.getScopeMeta(scope).deactivateAllSessions();
    }

    private static Path orphanDir(GlobalSessionController controller, String agentId, String sessionId)
            throws IOException {
        Path orphan = SessionPaths.sessionsDir(controller.getBasePath(), agentId).resolve(sessionId);
        Files.createDirectories(orphan);
        Files.writeString(SessionPaths.stateFile(orphan), "{}");
        return orphan;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, SessionController> mutableControllers(GlobalSessionController controller)
            throws ReflectiveOperationException {
        Field field = GlobalSessionController.class.getDeclaredField("controllers");
        field.setAccessible(true);
        return (Map<String, SessionController>) field.get(controller);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ChainSession<?>> mutableSessionCache(SessionController controller)
            throws ReflectiveOperationException {
        Field field = SessionController.class.getDeclaredField("sessionCache");
        field.setAccessible(true);
        return (Map<String, ChainSession<?>>) field.get(controller);
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

    /**
     * Test data container used to mirror Python's patched {@code AgentSessionContainer}.
     *
     * <p>Mirrors Python's test {@code mock_container} fixture in
     * {@code tests/unit_tests/core/session/session_controller/test_global_controller.py}.</p>
     */
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

    /**
     * Test data container used to mirror Python's patched flush failure path.
     *
     * <p>Mirrors Python's patched {@code ctrl.flush} failure in
     * {@code tests/unit_tests/core/session/session_controller/test_global_controller.py}.</p>
     */
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
