/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.openjiuwen.agent_teams.TeamHarness.MountedRails;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link TeamHarness}.
 *
 * <p>Mirrors Python's {@code test_harness.py} for
 * {@code openjiuwen/agent_teams/harness.py}.</p>
 */
class TeamHarnessTest {

    @Test
    void buildMountsRailsInLoadBearingOrder() {
        FakeDeepAgent deepAgent = stubDeepAgent("WS", "SYS", null);
        FakeSpec spec = new FakeSpec(deepAgent);
        RecordingTeamToolRail teamTool = new RecordingTeamToolRail();
        Object teamPolicy = named("TeamPolicyRail");
        Object firstIter = named("FirstIterationGate");
        Object workspaceRail = named("TeamWorkspaceRail");
        Object approval = named("TeamToolApprovalRail");

        TeamHarness harness = TeamHarness.build(
                spec,
                TeamRole.LEADER,
                "leader",
                teamTool,
                teamPolicy,
                firstIter,
                workspaceRail,
                approval,
                null,
                false
        );

        assertThat(harness.innerAgent()).isSameAs(deepAgent);
        assertThat(spec.buildCalls).isEqualTo(1);
        assertThat(deepAgent.addedRails).containsExactly(teamTool, teamPolicy, firstIter, workspaceRail, approval);
    }

    @Test
    void buildEagerlyInitializesTeamToolRail() {
        FakeDeepAgent deepAgent = stubDeepAgent("WS", "SYS", null);
        FakeSpec spec = new FakeSpec(deepAgent);
        List<String> callLog = new ArrayList<>();
        Object teamPolicy = named("TeamPolicyRail");
        RecordingTeamToolRail teamTool = new RecordingTeamToolRail(callLog);
        deepAgent.addHook = rail -> {
            if (rail == teamTool) {
                callLog.add("addRail:teamTool");
            } else if (rail == teamPolicy) {
                callLog.add("addRail:teamPolicy");
            } else {
                callLog.add("addRail:other");
            }
        };

        TeamHarness.build(spec, TeamRole.LEADER, "leader", teamTool, teamPolicy);

        assertThat(callLog).containsExactly(
                "addRail:teamTool",
                "setSysOperation",
                "setWorkspace",
                "init",
                "addRail:teamPolicy"
        );
        assertThat(teamTool.sysOperation).isEqualTo("SYS");
        assertThat(teamTool.workspace).isEqualTo("WS");
        assertThat(teamTool.initAgent).isSameAs(deepAgent);
    }

    @Test
    void buildSkipsOptionalRailsWhenNone() {
        FakeDeepAgent deepAgent = stubDeepAgent(null, null, null);
        FakeSpec spec = new FakeSpec(deepAgent);
        Object teamTool = named("TeamToolRail");
        Object teamPolicy = named("TeamPolicyRail");

        TeamHarness.build(spec, TeamRole.LEADER, "leader", teamTool, teamPolicy);

        assertThat(deepAgent.addedRails).containsExactly(teamTool, teamPolicy);
    }

    @Test
    void buildMountsTeamPlanModeRailWhenProvided() {
        FakeDeepAgent deepAgent = stubDeepAgent(null, null, null);
        FakeSpec spec = new FakeSpec(deepAgent);
        Object teamTool = named("TeamToolRail");
        Object teamPolicy = named("TeamPolicyRail");
        Object teamPlanMode = named("TeamPlanModeRail");

        TeamHarness harness = TeamHarness.build(
                spec,
                TeamRole.LEADER,
                "leader",
                teamTool,
                teamPolicy,
                null,
                null,
                null,
                teamPlanMode,
                false
        );

        assertThat(deepAgent.addedRails).containsExactly(teamTool, teamPolicy, teamPlanMode);
        assertThat(harness.rails().getTeamPlanMode()).isSameAs(teamPlanMode);
    }

    @Test
    void runAgentCustomizerInvokesWithInnerAgent() {
        FakeDeepAgent deepAgent = stubDeepAgent(null, null, null);
        TeamHarness harness = makeHarness(deepAgent);
        List<Object> captured = new ArrayList<>();

        harness.runAgentCustomizer((agent, memberName, roleValue) -> {
            captured.add(agent);
            captured.add(memberName);
            captured.add(roleValue);
        });

        assertThat(captured).containsExactly(deepAgent, "leader", "leader");
    }

    @Test
    void runAgentCustomizerSwallowsExceptions() {
        TeamHarness harness = makeHarness(stubDeepAgent(null, null, null));

        assertDoesNotThrow(() -> harness.runAgentCustomizer((agent, memberName, roleValue) -> {
            throw new IllegalStateException("boom");
        }));
    }

    @Test
    void steerForwardsToInnerAgent() {
        FakeDeepAgent deepAgent = stubDeepAgent(null, null, null);
        TeamHarness harness = makeHarness(deepAgent);

        harness.steer("hello").toCompletableFuture().join();

        assertThat(deepAgent.steerCalls).containsExactly("hello");
    }

    @Test
    void followUpForwardsToInnerAgent() {
        FakeDeepAgent deepAgent = stubDeepAgent(null, null, null);
        TeamHarness harness = makeHarness(deepAgent);

        harness.followUp("update").toCompletableFuture().join();

        assertThat(deepAgent.followUpCalls).containsExactly("update");
    }

    @Test
    void abortForwardsToInnerAgent() {
        FakeDeepAgent deepAgent = stubDeepAgent(null, null, null);
        TeamHarness harness = makeHarness(deepAgent);

        harness.abort().toCompletableFuture().join();

        assertThat(deepAgent.abortCalls).isEqualTo(1);
    }

    @Test
    void registerAndUnregisterRailForward() {
        FakeDeepAgent deepAgent = stubDeepAgent(null, null, null);
        TeamHarness harness = makeHarness(deepAgent);
        Object rail = named("DynamicRail");

        harness.registerRail(rail).toCompletableFuture().join();
        harness.unregisterRail(rail).toCompletableFuture().join();

        assertThat(deepAgent.registeredRails).containsExactly(rail);
        assertThat(deepAgent.unregisteredRails).containsExactly(rail);
    }

    @Test
    void findRailsDelegatesToDeepAgent() {
        FakeDeepAgent deepAgent = stubDeepAgent(null, null, null);
        SpecialRail skillRail = new SpecialRail();
        deepAgent.findableRails.add(skillRail);
        TeamHarness harness = makeHarness(deepAgent);

        List<Object> found = harness.findRails(SpecialRail.class);

        assertThat(found).containsExactly(skillRail);
        assertThat(deepAgent.findRailQueries).containsExactly(SpecialRail.class);
    }

    @Test
    void findRailsReturnsEmptyWhenNoMatch() {
        TeamHarness harness = makeHarness(stubDeepAgent(null, null, null));

        assertThat(harness.findRails(SpecialRail.class)).isEmpty();
    }

    @Test
    void runStreamingDelegatesToRunner() {
        FakeDeepAgent deepAgent = stubDeepAgent(null, null, null);
        List<Object> captured = new ArrayList<>();
        TeamHarness harness = new TeamHarness(
                deepAgent,
                new MountedRails(named("tool"), named("policy")),
                TeamRole.LEADER,
                "leader",
                false,
                (agent, inputs, session) -> {
                    captured.add(agent);
                    captured.add(inputs.get("query"));
                    captured.add(session);
                    return List.<Object>of("chunk").iterator();
                }
        );

        List<Object> chunks = toList(harness.runStreaming(Map.of("query", "q"), "sess-1"));

        assertThat(captured).containsExactly(deepAgent, "q", "sess-1");
        assertThat(chunks).containsExactly("chunk");
    }

    @Test
    void hasPendingInterruptReturnsFalseWithoutSession() {
        TeamHarness harness = makeHarness(stubDeepAgent(null, null, null));

        assertThat(harness.hasPendingInterrupt()).isFalse();
    }

    @Test
    void hasPendingInterruptReturnsFalseWithoutState() {
        FakeSession session = new FakeSession(null);
        TeamHarness harness = makeHarness(stubDeepAgent(null, null, session));

        assertThat(harness.hasPendingInterrupt()).isFalse();
    }

    @Test
    void hasPendingInterruptReturnsTrueWhenStatePresent() {
        FakeSession session = new FakeSession(Map.of("interruptedTools", Map.of()));
        TeamHarness harness = makeHarness(stubDeepAgent(null, null, session));

        assertThat(harness.hasPendingInterrupt()).isTrue();
    }

    @Test
    void pendingInterruptSurvivesLoopSessionCleanup() {
        Map<String, Object> state = interruptState("tool-ask-1");
        FakeSession agentSession = new FakeSession(state);
        FakeTeamSession teamSession = new FakeTeamSession(agentSession);
        FakeDeepAgent deepAgent = stubDeepAgent(null, null, null);
        TeamHarness harness = new TeamHarness(
                deepAgent,
                new MountedRails(named("tool"), named("policy")),
                TeamRole.LEADER,
                "leader",
                false,
                (agent, inputs, session) -> Collections.emptyIterator()
        );

        harness.runStreaming(Map.of("query", "q"), "team-session", teamSession).hasNext();
        InteractiveInput interactive = new InteractiveInput();
        interactive.update("tool-ask-1", Map.of("answers", Map.of("framework", "React")));

        assertThat(harness.hasPendingInterrupt()).isTrue();
        assertThat(harness.isPendingInterruptResumeValid(interactive)).isTrue();
    }

    @Test
    void isPendingInterruptResumeValidRejectsNonInteractiveInput() {
        TeamHarness harness = makeHarness(stubDeepAgent(null, null, null));

        assertThat(harness.isPendingInterruptResumeValid("not interactive")).isFalse();
    }

    @Test
    void isPendingInterruptResumeValidReturnsFalseWithoutPendingIds() {
        FakeSession session = new FakeSession(Map.of("interruptedTools", Map.of()));
        TeamHarness harness = makeHarness(stubDeepAgent(null, null, session));
        InteractiveInput interactive = new InteractiveInput();
        interactive.update("call-1", Map.of("approved", true));

        assertThat(harness.isPendingInterruptResumeValid(interactive)).isFalse();
    }

    @Test
    void isPendingInterruptResumeValidAcceptsMatchingIds() {
        FakeSession session = new FakeSession(interruptState("call-1"));
        TeamHarness harness = makeHarness(stubDeepAgent(null, null, session));
        InteractiveInput interactive = new InteractiveInput();
        interactive.update("call-1", Map.of("approved", true));

        assertThat(harness.isPendingInterruptResumeValid(interactive)).isTrue();
    }

    @Test
    void isPendingInterruptResumeValidRejectsMismatchedIds() {
        FakeSession session = new FakeSession(interruptState("call-1"));
        TeamHarness harness = makeHarness(stubDeepAgent(null, null, session));
        InteractiveInput interactive = new InteractiveInput();
        interactive.update("call-2", Map.of("approved", true));

        assertThat(harness.isPendingInterruptResumeValid(interactive)).isFalse();
    }

    @Test
    void initCwdForRoundNoOpWithoutWorkspace() {
        TeamHarness harness = makeHarness(stubDeepAgent(null, null, null));

        assertDoesNotThrow(harness::initCwdForRound);
    }

    @Test
    void registerMemberToolsForwardsInnerAgent() {
        FakeDeepAgent deepAgent = stubDeepAgent(null, null, null);
        TeamHarness harness = makeHarness(deepAgent);
        FakeMemoryManager memoryManager = new FakeMemoryManager();

        harness.registerMemberTools(memoryManager);

        assertThat(memoryManager.registeredAgent).isSameAs(deepAgent);
    }

    @Test
    void injectMemberMemoryForwardsInnerAgent() {
        FakeDeepAgent deepAgent = stubDeepAgent(null, null, null);
        TeamHarness harness = makeHarness(deepAgent);
        FakeMemoryManager memoryManager = new FakeMemoryManager();

        harness.injectMemberMemory(memoryManager, "hello").toCompletableFuture().join();

        assertThat(memoryManager.injectedAgent).isSameAs(deepAgent);
        assertThat(memoryManager.injectedQuery).isEqualTo("hello");
    }

    private static TeamHarness makeHarness(FakeDeepAgent deepAgent) {
        return new TeamHarness(
                deepAgent,
                new MountedRails(named("TeamToolRail"), named("TeamPolicyRail")),
                TeamRole.LEADER,
                "leader"
        );
    }

    private static FakeDeepAgent stubDeepAgent(Object workspace, Object sysOperation, Object loopSession) {
        return new FakeDeepAgent(new FakeDeepConfig(workspace, sysOperation, "model"), loopSession);
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private static Object named(String name) {
        return new NamedObject(name);
    }

    private static Map<String, Object> interruptState(String requestId) {
        Map<String, Object> request = Map.of(requestId, new Object());
        Map<String, Object> entry = Map.of("interruptRequests", request);
        return Map.of("interruptedTools", Map.of("ask-user", entry));
    }

    private record NamedObject(String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private record FakeDeepConfig(Object workspace, Object sysOperation, Object model) {
    }

    private static final class FakeSpec {
        private final FakeDeepAgent deepAgent;
        private int buildCalls;

        FakeSpec(FakeDeepAgent deepAgent) {
            this.deepAgent = deepAgent;
        }

        public FakeDeepAgent build() {
            buildCalls++;
            return deepAgent;
        }
    }

    private static final class RecordingTeamToolRail {
        private final List<String> callLog;
        private Object sysOperation;
        private Object workspace;
        private Object initAgent;

        RecordingTeamToolRail() {
            this(new ArrayList<>());
        }

        RecordingTeamToolRail(List<String> callLog) {
            this.callLog = callLog;
        }

        public void setSysOperation(Object sysOperation) {
            callLog.add("setSysOperation");
            this.sysOperation = sysOperation;
        }

        public void setWorkspace(Object workspace) {
            callLog.add("setWorkspace");
            this.workspace = workspace;
        }

        public void init(Object agent) {
            callLog.add("init");
            this.initAgent = agent;
        }
    }

    private interface AddHook {
        void onAdd(Object rail);
    }

    private static final class FakeDeepAgent {
        private final FakeDeepConfig deepConfig;
        private final Object loopSession;
        private final Object card = "card";
        private final List<Object> addedRails = new ArrayList<>();
        private final List<Object> registeredRails = new ArrayList<>();
        private final List<Object> unregisteredRails = new ArrayList<>();
        private final List<Object> findableRails = new ArrayList<>();
        private final List<Class<?>> findRailQueries = new ArrayList<>();
        private final List<String> steerCalls = new ArrayList<>();
        private final List<String> followUpCalls = new ArrayList<>();
        private final List<String> switchModes = new ArrayList<>();
        private int abortCalls;
        private AddHook addHook;

        FakeDeepAgent(FakeDeepConfig deepConfig, Object loopSession) {
            this.deepConfig = deepConfig;
            this.loopSession = loopSession;
        }

        public FakeDeepConfig getDeepConfig() {
            return deepConfig;
        }

        public Object getLoopSession() {
            return loopSession;
        }

        public Object getCard() {
            return card;
        }

        public void addRail(Object rail) {
            if (addHook != null) {
                addHook.onAdd(rail);
            }
            addedRails.add(rail);
        }

        public CompletionStage<Void> steer(String content) {
            steerCalls.add(content);
            return CompletableFuture.completedFuture(null);
        }

        public CompletionStage<Void> followUp(String content) {
            followUpCalls.add(content);
            return CompletableFuture.completedFuture(null);
        }

        public CompletionStage<Void> abort() {
            abortCalls++;
            return CompletableFuture.completedFuture(null);
        }

        public CompletionStage<Void> registerRail(Object rail) {
            registeredRails.add(rail);
            return CompletableFuture.completedFuture(null);
        }

        public CompletionStage<Void> unregisterRail(Object rail) {
            unregisteredRails.add(rail);
            return CompletableFuture.completedFuture(null);
        }

        public List<Object> findRailsByType(Class<?>[] railTypes) {
            Class<?> type = railTypes[0];
            findRailQueries.add(type);
            return findableRails.stream().filter(type::isInstance).map(Object.class::cast).toList();
        }

        public Object loadState(Object session) {
            return session instanceof FakeSession fakeSession ? fakeSession.getState(TeamHarness.INTERRUPTION_KEY)
                    : null;
        }

        public CompletionStage<Void> switchMode(Object session, String mode) {
            switchModes.add(mode);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FakeSession {
        private final Map<String, Object> states = new LinkedHashMap<>();

        FakeSession(Object state) {
            if (state != null) {
                states.put(TeamHarness.INTERRUPTION_KEY, state);
            }
        }

        public CompletionStage<Void> preRun(Map<String, Object> inputs) {
            return CompletableFuture.completedFuture(null);
        }

        public Object getState(String key) {
            return states.get(key);
        }
    }

    private static final class FakeTeamSession {
        private final FakeSession agentSession;

        FakeTeamSession(FakeSession agentSession) {
            this.agentSession = agentSession;
        }

        public FakeSession createAgentSession(Object card, boolean shareStreamWriter) {
            return agentSession;
        }
    }

    private static final class SpecialRail {
    }

    private static final class FakeMemoryManager {
        private Object registeredAgent;
        private Object injectedAgent;
        private String injectedQuery;

        public void registerTools(Object agent) {
            registeredAgent = agent;
        }

        public CompletionStage<Void> loadAndInject(Object agent, String query) {
            injectedAgent = agent;
            injectedQuery = query;
            return CompletableFuture.completedFuture(null);
        }
    }
}
