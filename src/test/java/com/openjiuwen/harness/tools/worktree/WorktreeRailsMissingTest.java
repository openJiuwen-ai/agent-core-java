/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Missing parity coverage for worktree rails.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/harness/tools/worktree/test_rails.py}.</p>
 */
class WorktreeRailsMissingTest {

    private static final String SOURCE = "tests/unit_tests/harness/tools/worktree/test_rails.py";

    @TempDir
    Path tempDir;

    @TestFactory
    Collection<DynamicTest> pythonWorktreeRailCases() {
        return List.of(
                caseOf("test_worktree_rail_defaults_before_init", this::worktreeRailDefaultsBeforeInit),
                caseOf("test_worktree_rail_init_registers_two_tools_on_agent",
                        this::worktreeRailInitRegistersTwoToolsOnAgent),
                caseOf("test_worktree_rail_init_forwards_event_handler",
                        this::worktreeRailInitForwardsEventHandler),
                caseOf("test_worktree_rail_uninit_removes_tools_from_both_managers",
                        this::worktreeRailUninitRemovesToolsFromBothManagers),
                caseOf("test_worktree_rail_init_tool_cards_carry_agent_id",
                        this::worktreeRailInitToolCardsCarryAgentId),
                caseOf("test_lifecycle_rail_subclasses_inherit_from_lifecycle_base",
                        this::lifecycleRailSubclassesInheritFromLifecycleBase),
                caseOf("test_lifecycle_rail_hooks_are_noop_by_default",
                        this::lifecycleRailHooksAreNoopByDefault),
                caseOf("test_create_event_dataclass_still_usable", this::createEventDataclassStillUsable),
                caseOf("test_before_invoke_restores_session_from_dict_state",
                        this::beforeInvokeRestoresSessionFromDictState),
                caseOf("test_before_invoke_tolerates_legacy_object_payload",
                        this::beforeInvokeToleratesLegacyObjectPayload),
                caseOf("test_before_invoke_resets_contextvar_when_state_missing",
                        this::beforeInvokeResetsContextvarWhenStateMissing),
                caseOf("test_before_invoke_noop_when_session_absent", this::beforeInvokeNoopWhenSessionAbsent),
                caseOf("test_after_invoke_persists_current_session_as_dict",
                        this::afterInvokePersistsCurrentSessionAsDict),
                caseOf("test_after_invoke_persists_none_to_clear_state",
                        this::afterInvokePersistsNoneToClearState),
                caseOf("test_after_invoke_noop_when_session_absent", this::afterInvokeNoopWhenSessionAbsent),
                caseOf("test_invoke_roundtrip_survives_a_simulated_resume",
                        this::invokeRoundtripSurvivesSimulatedResume),
                caseOf("test_before_invoke_redirects_cwd_to_worktree_path",
                        this::beforeInvokeRedirectsCwdToWorktreePath),
                caseOf("test_before_invoke_leaves_cwd_alone_when_no_stored_session",
                        this::beforeInvokeLeavesCwdAloneWhenNoStoredSession)
        );
    }

    private DynamicTest caseOf(String pythonNode, ThrowingRunnable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, () -> {
            resetState();
            try {
                executable.run();
            } finally {
                resetState();
            }
        });
    }

    private void worktreeRailDefaultsBeforeInit() {
        WorktreeRails.WorktreeRail rail = new WorktreeRails.WorktreeRail();

        assertThat(rail.getPriority()).isEqualTo(100);
        assertThat(rail.getManager()).isNull();
    }

    private void worktreeRailInitRegistersTwoToolsOnAgent() {
        WorktreeRails.WorktreeRail rail = new WorktreeRails.WorktreeRail();
        DeepAgent agent = agent("test_agent");

        rail.init(agent);

        assertThat(rail.getManager()).isNotNull();
        assertThat(rail.getTools()).extracting(tool -> tool.getCard().getName())
                .containsExactly("enter_worktree", "exit_worktree");
        assertThat(agent.getAbilityManager().get("enter_worktree")).isPresent();
        assertThat(agent.getAbilityManager().get("exit_worktree")).isPresent();
    }

    private void worktreeRailInitForwardsEventHandler() throws Exception {
        WorktreeEventHandler handler = event -> CompletableFuture.completedFuture(null);
        WorktreeRails.WorktreeLifecycleRail lifecycleRail = new WorktreeRails.WorktreeLifecycleRail();
        WorktreeRails.WorktreeRail rail = new WorktreeRails.WorktreeRail(null, handler, List.of(lifecycleRail));

        rail.init(agent("handler-agent"));

        Field handlerField = WorktreeManager.class.getDeclaredField("eventHandler");
        handlerField.setAccessible(true);
        Field railsField = WorktreeManager.class.getDeclaredField("rails");
        railsField.setAccessible(true);
        List<?> managerRails = (List<?>) railsField.get(rail.getManager());
        assertThat(handlerField.get(rail.getManager())).isSameAs(handler);
        assertThat(managerRails).hasSize(1);
        assertThat(managerRails.getFirst()).isSameAs(lifecycleRail);
    }

    private void worktreeRailUninitRemovesToolsFromBothManagers() {
        WorktreeRails.WorktreeRail rail = new WorktreeRails.WorktreeRail();
        DeepAgent agent = agent("uninit-agent");
        rail.init(agent);
        List<String> toolIds = rail.getTools().stream().map(tool -> tool.getCard().getId()).toList();

        rail.uninit(agent);

        assertThat(agent.getAbilityManager().get("enter_worktree")).isEmpty();
        assertThat(agent.getAbilityManager().get("exit_worktree")).isEmpty();
        assertThat(toolIds).allSatisfy(id -> assertThat(Runner.resourceMgr().getTool(id)).isNull());
        assertThat(rail.getManager()).isNull();
        assertThat(rail.getTools()).isEmpty();
    }

    private void worktreeRailInitToolCardsCarryAgentId() {
        WorktreeRails.WorktreeRail rail = new WorktreeRails.WorktreeRail();

        rail.init(agent("agent-abc"));

        assertThat(rail.getTools()).allSatisfy(tool -> assertThat(tool.getCard().getId()).endsWith("agent-abc"));
    }

    private void lifecycleRailSubclassesInheritFromLifecycleBase() {
        assertThat(WorktreeRails.WorktreeLifecycleRail.class)
                .isAssignableFrom(WorktreeRails.AutoSetupRail.class);
        assertThat(WorktreeRails.WorktreeLifecycleRail.class)
                .isAssignableFrom(WorktreeRails.DiffSummaryRail.class);
        assertThat(WorktreeRails.WorktreeLifecycleRail.class.isAssignableFrom(WorktreeRails.WorktreeRail.class))
                .isFalse();
    }

    private void lifecycleRailHooksAreNoopByDefault() {
        WorktreeRails.WorktreeLifecycleRail rail = new WorktreeRails.WorktreeLifecycleRail();
        CallbackContext ctx = new CallbackContext(agent("ctx-agent"), Map.of());
        WorktreeSession session = makeSession("hook");

        assertThat(rail.beforeWorktreeCreate(ctx, "slug", "/repo")).isNull();
        assertDoesNotThrow(() -> rail.afterWorktreeCreate(ctx, session));
        assertThat(rail.beforeWorktreeExit(ctx, session, "keep")).isNull();
        assertDoesNotThrow(() -> rail.afterWorktreeExit(ctx, session, "keep"));
        assertThat(rail.onWorktreeFileWrite(ctx, session, "/repo/file.txt")).isTrue();
        assertThat(rail.beforeWorktreeCommit(ctx, session, "msg", List.of("a.txt"))).isNull();
        assertDoesNotThrow(() -> rail.afterWorktreeCommit(ctx, session, "abc123"));
        assertThat(rail.onWorktreeSync(ctx, session, "push", List.of("a.txt"))).containsExactly("a.txt");
    }

    private void createEventDataclassStillUsable() {
        WorktreeCreatedEvent event = new WorktreeCreatedEvent("demo", "/tmp/demo", null, null, false);

        assertThat(event.getWorktreeName()).isEqualTo("demo");
    }

    private void beforeInvokeRestoresSessionFromDictState() {
        WorktreeRails.WorktreeRail rail = new WorktreeRails.WorktreeRail();
        MemorySession session = new MemorySession(Map.of(WorktreeRails.SESSION_STATE_KEY, stateMap(makeSession("alpha"))));

        rail.beforeInvoke(ctx(session));

        assertThat(WorktreeSessionContext.getCurrentSession()).isNotNull();
        assertThat(WorktreeSessionContext.getCurrentSession().getWorktreeName()).isEqualTo("alpha");
    }

    private void beforeInvokeToleratesLegacyObjectPayload() {
        WorktreeRails.WorktreeRail rail = new WorktreeRails.WorktreeRail();
        WorktreeSession persisted = makeSession("legacy");
        MemorySession session = new MemorySession(Map.of(WorktreeRails.SESSION_STATE_KEY, persisted));

        rail.beforeInvoke(ctx(session));

        assertThat(WorktreeSessionContext.getCurrentSession()).isSameAs(persisted);
    }

    private void beforeInvokeResetsContextvarWhenStateMissing() {
        WorktreeRails.WorktreeRail rail = new WorktreeRails.WorktreeRail();
        WorktreeSessionContext.setCurrentSession(makeSession("stale"));
        WorktreeSessionContext.setDefaultWorktreeName("stale-default");
        MemorySession session = new MemorySession();

        rail.beforeInvoke(ctx(session));

        assertThat(WorktreeSessionContext.getCurrentSession()).isNull();
        assertThat(WorktreeSessionContext.getDefaultWorktreeName()).isNull();
    }

    private void beforeInvokeNoopWhenSessionAbsent() {
        WorktreeRails.WorktreeRail rail = new WorktreeRails.WorktreeRail();

        assertDoesNotThrow(() -> rail.beforeInvoke(new CallbackContext(agent("no-session"), Map.of())));

        assertThat(WorktreeSessionContext.getCurrentSession()).isNull();
    }

    @SuppressWarnings("unchecked")
    private void afterInvokePersistsCurrentSessionAsDict() {
        WorktreeRails.WorktreeRail rail = new WorktreeRails.WorktreeRail();
        WorktreeSessionContext.setCurrentSession(makeSession("beta"));
        WorktreeSessionContext.setDefaultWorktreeName("default-beta");
        MemorySession session = new MemorySession();

        rail.afterInvoke(ctx(session));

        Object stored = session.getState(WorktreeRails.SESSION_STATE_KEY);
        assertThat(stored).isInstanceOf(Map.class);
        assertThat((Map<String, Object>) stored).containsEntry("worktree_name", "beta");
        assertThat(session.getState(WorktreeRails.DEFAULT_WORKTREE_NAME_KEY)).isEqualTo("default-beta");
    }

    private void afterInvokePersistsNoneToClearState() {
        WorktreeRails.WorktreeRail rail = new WorktreeRails.WorktreeRail();
        WorktreeSessionContext.setDefaultWorktreeName("default-old");
        MemorySession session = new MemorySession(Map.of(WorktreeRails.SESSION_STATE_KEY, stateMap(makeSession("old"))));

        rail.afterInvoke(ctx(session));

        assertThat(session.getState(WorktreeRails.SESSION_STATE_KEY)).isNull();
        assertThat(session.getState(WorktreeRails.DEFAULT_WORKTREE_NAME_KEY)).isEqualTo("default-old");
    }

    private void afterInvokeNoopWhenSessionAbsent() {
        WorktreeRails.WorktreeRail rail = new WorktreeRails.WorktreeRail();
        WorktreeSessionContext.setCurrentSession(makeSession("any"));

        assertDoesNotThrow(() -> rail.afterInvoke(new CallbackContext(agent("no-session-after"), Map.of())));
    }

    private void invokeRoundtripSurvivesSimulatedResume() {
        WorktreeRails.WorktreeRail rail = new WorktreeRails.WorktreeRail();
        MemorySession session = new MemorySession();

        WorktreeSessionContext.setCurrentSession(makeSession("resumable"));
        WorktreeSessionContext.setDefaultWorktreeName("resumable-default");
        rail.afterInvoke(ctx(session));

        WorktreeSessionContext.setCurrentSession(null);
        WorktreeSessionContext.setDefaultWorktreeName(null);

        rail.beforeInvoke(ctx(session));

        assertThat(WorktreeSessionContext.getCurrentSession()).isNotNull();
        assertThat(WorktreeSessionContext.getCurrentSession().getWorktreeName()).isEqualTo("resumable");
        assertThat(WorktreeSessionContext.getDefaultWorktreeName()).isEqualTo("resumable-default");
    }

    private void beforeInvokeRedirectsCwdToWorktreePath() throws Exception {
        WorktreeRails.WorktreeRail rail = new WorktreeRails.WorktreeRail();
        Cwd.initCwd(tempDir.toString());
        Path worktreeDir = Files.createDirectories(tempDir.resolve(".worktrees").resolve("redirect"));
        WorktreeSession stored = new WorktreeSession(tempDir.toString(), worktreeDir.toString(), "redirect");
        MemorySession session = new MemorySession(Map.of(WorktreeRails.SESSION_STATE_KEY, stateMap(stored)));

        rail.beforeInvoke(ctx(session));

        String expected = worktreeDir.toAbsolutePath().normalize().toString();
        assertThat(Cwd.getCwd()).isEqualTo(expected);
        assertThat(Cwd.getOriginalCwd()).isEqualTo(expected);
    }

    private void beforeInvokeLeavesCwdAloneWhenNoStoredSession() {
        WorktreeRails.WorktreeRail rail = new WorktreeRails.WorktreeRail();
        Cwd.initCwd(tempDir.toString());
        String untouched = Cwd.getCwd();

        rail.beforeInvoke(ctx(new MemorySession()));

        assertThat(Cwd.getCwd()).isEqualTo(untouched);
    }

    private static CallbackContext ctx(AgentSessionApi session) {
        return new CallbackContext(agent("session-agent"), Map.of("session", session));
    }

    private static DeepAgent agent(String id) {
        return new DeepAgent(new AgentCard(id, id, "test agent"));
    }

    private static WorktreeSession makeSession(String name) {
        return new WorktreeSession("/repo", "/workspace/.worktrees/" + name, name);
    }

    private static Map<String, Object> stateMap(WorktreeSession session) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("original_cwd", session.getOriginalCwd());
        state.put("worktree_path", session.getWorktreePath());
        state.put("worktree_name", session.getWorktreeName());
        return state;
    }

    private static void resetState() {
        WorktreeSessionContext.setCurrentSession(null);
        WorktreeSessionContext.setDefaultWorktreeName(null);
        Cwd.clear();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class MemorySession implements AgentSessionApi {
        private final Map<String, Object> state = new LinkedHashMap<>();

        private MemorySession() {
        }

        private MemorySession(Map<String, Object> initial) {
            if (initial != null) {
                state.putAll(initial);
            }
        }

        @Override
        public String getSessionId() {
            return "session";
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public Iterator<Object> streamIterator() {
            return List.of().iterator();
        }
    }
}
