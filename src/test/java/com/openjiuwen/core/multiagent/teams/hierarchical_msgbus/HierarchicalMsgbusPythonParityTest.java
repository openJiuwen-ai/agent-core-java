/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_msgbus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.TeamConfig;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.team_runtime.CommunicableAgent;
import com.openjiuwen.core.multiagent.team_runtime.TeamRuntime;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.AddAbilityResult;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for the hierarchical message-bus team module.
 *
 * <p>Mirrors Python's supplemental test module
 * {@code tests/unit_tests/multi_agent/builtin_teams/hierarchical_msgbus/test_hierarchical_msgbus.py}.</p>
 */
class HierarchicalMsgbusPythonParityTest {

    private static final String PYTEST_PREFIX = "tests/unit_tests/multi_agent/builtin_teams/hierarchical_msgbus/"
            + "test_hierarchical_msgbus.py::";
    private static final ObjectMapper JSON = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> mirrorsPythonPytestNodes() {
        List<TestCase> testCases = List.of(
                testCase("TestHierarchicalTeamConfig::test_requires_supervisor_agent",
                        HierarchicalMsgbusPythonParityTest::configRequiresSupervisorBeforeTeamUse),
                testCase("TestHierarchicalTeamConfig::test_stores_supervisor_card",
                        HierarchicalMsgbusPythonParityTest::configStoresSupervisorCard),
                testCase("TestHierarchicalTeamConfig::test_supervisor_card_id_accessible",
                        HierarchicalMsgbusPythonParityTest::configSupervisorCardIdAccessible),
                testCase("TestHierarchicalTeamConfig::test_inherits_team_config",
                        HierarchicalMsgbusPythonParityTest::configInheritsTeamConfig),
                testCase("TestHierarchicalTeamConfig::test_team_config_default_max_agents",
                        HierarchicalMsgbusPythonParityTest::configTeamConfigDefaultMaxAgents),
                testCase("TestHierarchicalTeamConfig::test_two_configs_are_independent",
                        HierarchicalMsgbusPythonParityTest::configTwoConfigsAreIndependent),

                testCase("TestHierarchicalTeamInit::test_card_stored",
                        HierarchicalMsgbusPythonParityTest::teamInitCardStored),
                testCase("TestHierarchicalTeamInit::test_config_stored",
                        HierarchicalMsgbusPythonParityTest::teamInitConfigStored),
                testCase("TestHierarchicalTeamInit::test_runtime_created",
                        HierarchicalMsgbusPythonParityTest::teamInitRuntimeCreated),
                testCase("TestHierarchicalTeamInit::test_runtime_has_no_agents_initially",
                        HierarchicalMsgbusPythonParityTest::teamInitRuntimeHasNoAgentsInitially),
                testCase("TestHierarchicalTeamInit::test_is_base_team",
                        HierarchicalMsgbusPythonParityTest::teamInitIsBaseTeam),

                testCase("TestHierarchicalTeamAddAgent::test_registers_agent_in_runtime",
                        HierarchicalMsgbusPythonParityTest::teamAddAgentRegistersAgentInRuntime),
                testCase("TestHierarchicalTeamAddAgent::test_returns_self_for_chaining",
                        HierarchicalMsgbusPythonParityTest::teamAddAgentReturnsSelfForChaining),
                testCase("TestHierarchicalTeamAddAgent::test_agent_count_increments",
                        HierarchicalMsgbusPythonParityTest::teamAddAgentCountIncrements),
                testCase("TestHierarchicalTeamAddAgent::test_supervisor_card_registered",
                        HierarchicalMsgbusPythonParityTest::teamAddAgentSupervisorCardRegistered),
                testCase("TestHierarchicalTeamAddAgent::test_duplicate_agent_does_not_increase_count",
                        HierarchicalMsgbusPythonParityTest::teamAddAgentDuplicateDoesNotIncreaseCount),
                testCase("TestHierarchicalTeamAddAgent::test_get_agent_card_after_registration",
                        HierarchicalMsgbusPythonParityTest::teamAddAgentGetAgentCardAfterRegistration),
                testCase("TestHierarchicalTeamAddAgent::test_list_agents_contains_registered_id",
                        HierarchicalMsgbusPythonParityTest::teamAddAgentListAgentsContainsRegisteredId),
                testCase("TestHierarchicalTeamAddAgent::test_supervisor_registration_emits_info_log",
                        HierarchicalMsgbusPythonParityTest::teamAddAgentSupervisorRegistrationAppliesTimeout),

                testCase("TestHierarchicalTeamAssertReady::test_raises_when_supervisor_not_registered",
                        HierarchicalMsgbusPythonParityTest::teamAssertReadyRaisesWhenSupervisorNotRegistered),
                testCase("TestHierarchicalTeamAssertReady::test_passes_when_supervisor_registered",
                        HierarchicalMsgbusPythonParityTest::teamAssertReadyPassesWhenSupervisorRegistered),
                testCase("TestHierarchicalTeamAssertReady::test_raises_when_only_non_supervisor_registered",
                        HierarchicalMsgbusPythonParityTest::teamAssertReadyRaisesWhenOnlyNonSupervisorRegistered),

                testCase("TestHierarchicalTeamInvoke::test_raises_when_supervisor_not_registered",
                        HierarchicalMsgbusPythonParityTest::teamInvokeRaisesWhenSupervisorNotRegistered),
                testCase("TestHierarchicalTeamInvoke::test_returns_result_from_runtime_send",
                        HierarchicalMsgbusPythonParityTest::teamInvokeReturnsResultFromRuntimeSend),
                testCase("TestHierarchicalTeamInvoke::test_send_called_with_supervisor_as_recipient",
                        HierarchicalMsgbusPythonParityTest::teamInvokeSendCalledWithSupervisorAsRecipient),
                testCase("TestHierarchicalTeamInvoke::test_send_called_with_session_id",
                        HierarchicalMsgbusPythonParityTest::teamInvokeSendCalledWithSessionId),

                testCase("TestHierarchicalTeamStream::test_raises_when_supervisor_not_registered",
                        HierarchicalMsgbusPythonParityTest::teamStreamRaisesWhenSupervisorNotRegistered),
                testCase("TestHierarchicalTeamStream::test_yields_all_chunks_from_stream_context",
                        HierarchicalMsgbusPythonParityTest::teamStreamYieldsFinalChunkFromRuntimeSend),
                testCase("TestHierarchicalTeamStream::test_stream_empty_when_context_yields_nothing",
                        HierarchicalMsgbusPythonParityTest::teamStreamEmptyWhenRuntimeSendReturnsNull),

                testCase("TestP2PAbilityManagerInit::test_inherits_ability_manager",
                        HierarchicalMsgbusPythonParityTest::p2pInitInheritsAbilityManager),
                testCase("TestP2PAbilityManagerInit::test_semaphore_reflects_max_parallel",
                        HierarchicalMsgbusPythonParityTest::p2pInitSemaphoreReflectsMaxParallel),
                testCase("TestP2PAbilityManagerInit::test_max_parallel_clamped_to_one_when_zero",
                        HierarchicalMsgbusPythonParityTest::p2pInitMaxParallelClampedToOneWhenZero),
                testCase("TestP2PAbilityManagerInit::test_max_parallel_clamped_to_one_when_negative",
                        HierarchicalMsgbusPythonParityTest::p2pInitMaxParallelClampedToOneWhenNegative),
                testCase("TestP2PAbilityManagerInit::test_semaphore_lazily_created_and_cached",
                        HierarchicalMsgbusPythonParityTest::p2pInitSemaphoreLazilyCreatedAndCached),

                testCase("TestP2PAbilityManagerAdd::test_add_stores_agent_card",
                        HierarchicalMsgbusPythonParityTest::p2pAddStoresAgentCard),
                testCase("TestP2PAbilityManagerAdd::test_add_multiple_cards",
                        HierarchicalMsgbusPythonParityTest::p2pAddMultipleCards),
                testCase("TestP2PAbilityManagerAdd::test_add_returns_add_ability_result",
                        HierarchicalMsgbusPythonParityTest::p2pAddReturnsAddAbilityResult),
                testCase("TestP2PAbilityManagerAdd::test_add_duplicate_returns_not_added",
                        HierarchicalMsgbusPythonParityTest::p2pAddDuplicateReturnsNotAdded),

                testCase("TestP2PAbilityManagerExecuteNonAgent::test_empty_tool_calls_returns_empty_list",
                        HierarchicalMsgbusPythonParityTest::p2pExecuteEmptyToolCallsReturnsEmptyList),
                testCase("TestP2PAbilityManagerExecuteNonAgent::test_non_agent_call_delegates_to_super",
                        HierarchicalMsgbusPythonParityTest::p2pExecuteNonAgentCallDelegatesToBase),
                testCase("TestP2PAbilityManagerExecuteNonAgent::test_non_agent_single_tool_call_passes_through",
                        HierarchicalMsgbusPythonParityTest::p2pExecuteNonAgentSingleToolCallPassesThrough),

                testCase("TestP2PAbilityManagerExecuteAgentCall::test_agent_call_invokes_supervisor_send",
                        HierarchicalMsgbusPythonParityTest::p2pExecuteAgentCallInvokesSupervisorSend),
                testCase("TestP2PAbilityManagerExecuteAgentCall::test_agent_call_recipient_matches_agent_id",
                        HierarchicalMsgbusPythonParityTest::p2pExecuteAgentCallRecipientMatchesAgentId),
                testCase("TestP2PAbilityManagerExecuteAgentCall::test_agent_call_passes_session_id",
                        HierarchicalMsgbusPythonParityTest::p2pExecuteAgentCallPassesSessionId),
                testCase("TestP2PAbilityManagerExecuteAgentCall::test_agent_call_returns_result_and_tool_message",
                        HierarchicalMsgbusPythonParityTest::p2pExecuteAgentCallReturnsResultAndToolMessage),
                testCase("TestP2PAbilityManagerExecuteAgentCall::test_tool_message_has_correct_tool_call_id",
                        HierarchicalMsgbusPythonParityTest::p2pExecuteToolMessageHasCorrectToolCallId),
                testCase("TestP2PAbilityManagerExecuteAgentCall::test_p2p_failure_returns_error_tool_message",
                        HierarchicalMsgbusPythonParityTest::p2pExecuteFailureReturnsErrorToolMessage),
                testCase("TestP2PAbilityManagerExecuteAgentCall::test_p2p_error_tool_message_has_original_call_id",
                        HierarchicalMsgbusPythonParityTest::p2pExecuteErrorToolMessageHasOriginalCallId),

                testCase("TestP2PAbilityManagerParallelDispatch::test_all_parallel_agent_calls_dispatched",
                        HierarchicalMsgbusPythonParityTest::p2pParallelAllAgentCallsDispatched),
                testCase("TestP2PAbilityManagerParallelDispatch::test_semaphore_limits_peak_concurrency",
                        HierarchicalMsgbusPythonParityTest::p2pParallelSemaphoreLimitsPeakConcurrency),
                testCase("TestP2PAbilityManagerParallelDispatch::test_result_order_preserved_for_parallel_calls",
                        HierarchicalMsgbusPythonParityTest::p2pParallelResultOrderPreserved),
                testCase("TestP2PAbilityManagerParallelDispatch::test_mixed_agent_and_tool_calls_both_executed",
                        HierarchicalMsgbusPythonParityTest::p2pParallelMixedAgentAndToolCallsBothExecuted),

                testCase("TestSupervisorAgentInit::test_ability_manager_is_p2p",
                        HierarchicalMsgbusPythonParityTest::supervisorInitAbilityManagerIsP2p),
                testCase("TestSupervisorAgentInit::test_is_communicable_agent",
                        HierarchicalMsgbusPythonParityTest::supervisorInitIsCommunicableAgent),
                testCase("TestSupervisorAgentInit::test_is_react_agent",
                        HierarchicalMsgbusPythonParityTest::supervisorInitIsReactAgent),
                testCase("TestSupervisorAgentInit::test_register_sub_agent_card_adds_to_ability_manager",
                        HierarchicalMsgbusPythonParityTest::supervisorInitRegisterSubAgentCardAddsToAbilityManager),
                testCase("TestSupervisorAgentInit::test_register_multiple_sub_agents",
                        HierarchicalMsgbusPythonParityTest::supervisorInitRegisterMultipleSubAgents),
                testCase("TestSupervisorAgentInit::test_register_sub_agent_emits_debug_log",
                        HierarchicalMsgbusPythonParityTest::supervisorInitRegisterSubAgentEmitsDebugLogEquivalent),

                testCase("TestSupervisorAgentConfigure::test_configure_react_config_returns_self",
                        HierarchicalMsgbusPythonParityTest::supervisorConfigureReactConfigReturnsSelf),
                testCase("TestSupervisorAgentConfigure::test_configure_non_react_is_noop_returns_self",
                        HierarchicalMsgbusPythonParityTest::supervisorConfigureNonReactIsNoopReturnsSelf),
                testCase("TestSupervisorAgentConfigure::test_configure_none_is_noop_returns_self",
                        HierarchicalMsgbusPythonParityTest::supervisorConfigureNoneIsNoopReturnsSelf),

                testCase("TestSupervisorAgentCreate::test_create_returns_card_and_callable_provider",
                        HierarchicalMsgbusPythonParityTest::supervisorCreateReturnsCardAndCallableProvider),
                testCase("TestSupervisorAgentCreate::test_create_empty_agents_raises",
                        HierarchicalMsgbusPythonParityTest::supervisorCreateEmptyAgentsRaises),
                testCase("TestSupervisorAgentCreate::test_create_non_agent_card_in_list_raises",
                        HierarchicalMsgbusPythonParityTest::supervisorCreateNonAgentCardInListRaises),
                testCase("TestSupervisorAgentCreate::test_provider_returns_supervisor_agent_instance",
                        HierarchicalMsgbusPythonParityTest::supervisorCreateProviderReturnsSupervisorAgentInstance),
                testCase("TestSupervisorAgentCreate::test_provider_registers_all_sub_agents",
                        HierarchicalMsgbusPythonParityTest::supervisorCreateProviderRegistersAllSubAgents),
                testCase("TestSupervisorAgentCreate::test_create_agent_card_id_matches_supplied_card",
                        HierarchicalMsgbusPythonParityTest::supervisorCreateAgentCardIdMatchesSuppliedCard),
                testCase("TestSupervisorAgentCreate::test_create_with_custom_max_iterations",
                        HierarchicalMsgbusPythonParityTest::supervisorCreateWithCustomMaxIterations),
                testCase("TestSupervisorAgentCreate::test_create_with_custom_max_parallel_sub_agents",
                        HierarchicalMsgbusPythonParityTest::supervisorCreateWithCustomMaxParallelSubAgents)
        );
        assertThat(testCases).hasSize(69);
        return testCases.stream().map(testCase -> dynamicTest(PYTEST_PREFIX + testCase.nodeId(), testCase.executable()));
    }

    private static void configRequiresSupervisorBeforeTeamUse() {
        HierarchicalTeamConfig config = new HierarchicalTeamConfig();
        HierarchicalTeam team = new HierarchicalTeam(teamCard("team"), config);

        assertThatThrownBy(team::assertReady)
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("No supervisor configured");
    }

    private static void configStoresSupervisorCard() {
        AgentCard card = supervisorCard("sv1");

        HierarchicalTeamConfig config = new HierarchicalTeamConfig(card);

        assertThat(config.getSupervisorAgent()).isSameAs(card);
        assertThat(config.getSupervisorAgent().getName()).isEqualTo("sv1");
    }

    private static void configSupervisorCardIdAccessible() {
        assertThat(makeConfig("my_supervisor").getSupervisorAgent().getId()).isEqualTo("my_supervisor");
    }

    private static void configInheritsTeamConfig() {
        assertThat(makeConfig("supervisor")).isInstanceOf(TeamConfig.class);
    }

    private static void configTeamConfigDefaultMaxAgents() {
        assertThat(makeConfig("supervisor").getMaxAgents()).isEqualTo(10);
    }

    private static void configTwoConfigsAreIndependent() {
        HierarchicalTeamConfig first = makeConfig("sv_a");
        HierarchicalTeamConfig second = makeConfig("sv_b");

        assertThat(first.getSupervisorAgent().getId()).isNotEqualTo(second.getSupervisorAgent().getId());
        assertThat(first.getSupervisorAgent()).isNotSameAs(second.getSupervisorAgent());
    }

    private static void teamInitCardStored() {
        TeamCard card = teamCard("my_team");

        assertThat(new HierarchicalTeam(card, makeConfig("supervisor")).getCard()).isSameAs(card);
    }

    private static void teamInitConfigStored() {
        HierarchicalTeamConfig config = makeConfig("sv_cfg");

        assertThat(new HierarchicalTeam(teamCard("team"), config).getConfig()).isSameAs(config);
    }

    private static void teamInitRuntimeCreated() {
        assertThat(makeTeam("supervisor").getRuntime()).isInstanceOf(TeamRuntime.class);
    }

    private static void teamInitRuntimeHasNoAgentsInitially() {
        assertThat(makeTeam("supervisor").getAgentCount()).isZero();
    }

    private static void teamInitIsBaseTeam() {
        assertThat(makeTeam("supervisor")).isInstanceOf(BaseTeam.class);
    }

    private static void teamAddAgentRegistersAgentInRuntime() {
        HierarchicalTeam team = makeTeam("supervisor");

        team.addAgent(subCard("a1"), ignored -> new Object());

        assertThat(team.getRuntime().hasAgent("a1")).isTrue();
    }

    private static void teamAddAgentReturnsSelfForChaining() {
        HierarchicalTeam team = makeTeam("supervisor");

        assertThat(team.addAgent(subCard("a2"), ignored -> new Object())).isSameAs(team);
    }

    private static void teamAddAgentCountIncrements() {
        HierarchicalTeam team = makeTeam("supervisor");

        assertThat(team.getAgentCount()).isZero();
        team.addAgent(subCard("a1"), ignored -> new Object());
        assertThat(team.getAgentCount()).isEqualTo(1);
        team.addAgent(subCard("a2"), ignored -> new Object());
        assertThat(team.getAgentCount()).isEqualTo(2);
    }

    private static void teamAddAgentSupervisorCardRegistered() {
        HierarchicalTeam team = makeTeam("sv_add");

        team.addAgent(supervisorCard("sv_add"), ignored -> new Object());

        assertThat(team.getRuntime().hasAgent("sv_add")).isTrue();
    }

    private static void teamAddAgentDuplicateDoesNotIncreaseCount() {
        HierarchicalTeam team = makeTeam("supervisor");

        team.addAgent(subCard("dup"), ignored -> new Object());
        team.addAgent(subCard("dup"), ignored -> new Object());

        assertThat(team.getAgentCount()).isEqualTo(1);
    }

    private static void teamAddAgentGetAgentCardAfterRegistration() {
        HierarchicalTeam team = makeTeam("supervisor");
        AgentCard card = subCard("lookup_me");

        team.addAgent(card, ignored -> new Object());

        assertThat(team.getAgentCard("lookup_me")).isSameAs(card);
    }

    private static void teamAddAgentListAgentsContainsRegisteredId() {
        HierarchicalTeam team = makeTeam("supervisor");

        team.addAgent(subCard("listed"), ignored -> new Object());

        assertThat(team.listAgents()).contains("listed");
    }

    private static void teamAddAgentSupervisorRegistrationAppliesTimeout() {
        HierarchicalTeamConfig config = new HierarchicalTeamConfig(supervisorCard("sv_logged"), 2.5);
        HierarchicalTeam team = new HierarchicalTeam(teamCard("team"), config);

        team.addAgent(supervisorCard("sv_logged"), ignored -> new Object());

        assertThat(team.getRuntime().getP2pTimeout()).isEqualTo(2.5);
    }

    private static void teamAssertReadyRaisesWhenSupervisorNotRegistered() {
        assertThatThrownBy(makeTeam("sv_missing")::assertReady).isInstanceOf(BaseError.class);
    }

    private static void teamAssertReadyPassesWhenSupervisorRegistered() {
        HierarchicalTeam team = makeTeam("sv_ok");
        team.addAgent(supervisorCard("sv_ok"), ignored -> new Object());

        team.assertReady();
    }

    private static void teamAssertReadyRaisesWhenOnlyNonSupervisorRegistered() {
        HierarchicalTeam team = makeTeam("sv_real");
        team.addAgent(subCard("not_supervisor"), ignored -> new Object());

        assertThatThrownBy(team::assertReady).isInstanceOf(BaseError.class);
    }

    private static void teamInvokeRaisesWhenSupervisorNotRegistered() {
        assertThatThrownBy(() -> makeTeam("sv_absent").invoke(Map.of("query", "hi")).toCompletableFuture().join())
                .isInstanceOf(BaseError.class);
    }

    private static void teamInvokeReturnsResultFromRuntimeSend() {
        RecordingRuntime runtime = new RecordingRuntime();
        Map<String, Object> expected = Map.of("output", "done");
        runtime.response.set(expected);
        HierarchicalTeam team = makeTeam("sv", runtime);
        team.addAgent(supervisorCard("sv"), ignored -> new Object());

        assertThat(team.invoke(Map.of("query", "hello")).toCompletableFuture().join()).isSameAs(expected);
    }

    private static void teamInvokeSendCalledWithSupervisorAsRecipient() {
        RecordingRuntime runtime = new RecordingRuntime();
        HierarchicalTeam team = makeTeam("sv_recv", runtime);
        team.addAgent(supervisorCard("sv_recv"), ignored -> new Object());

        team.invoke(Map.of("q", "test")).toCompletableFuture().join();

        assertThat(runtime.lastRecipient.get()).isEqualTo("sv_recv");
    }

    private static void teamInvokeSendCalledWithSessionId() {
        RecordingRuntime runtime = new RecordingRuntime();
        HierarchicalTeam team = makeTeam("sv_sid", runtime);
        team.addAgent(supervisorCard("sv_sid"), ignored -> new Object());
        TestSession session = new TestSession("my-session-42");

        team.invoke(Map.of("q", "test"), session).toCompletableFuture().join();

        assertThat(runtime.lastSessionId.get()).isEqualTo("my-session-42");
    }

    private static void teamStreamRaisesWhenSupervisorNotRegistered() {
        assertThatThrownBy(() -> makeTeam("sv_absent").stream(Map.of("q", "hi")).toList())
                .isInstanceOf(BaseError.class);
    }

    private static void teamStreamYieldsFinalChunkFromRuntimeSend() {
        RecordingRuntime runtime = new RecordingRuntime();
        runtime.response.set("stream-result");
        HierarchicalTeam team = makeTeam("sv", runtime);
        team.addAgent(supervisorCard("sv"), ignored -> new Object());

        List<Object> chunks = team.stream(Map.of("q", "test")).toList();

        assertThat(chunks).isNotEmpty();
        assertThat(runtime.lastRecipient.get()).isEqualTo("sv");
    }

    private static void teamStreamEmptyWhenRuntimeSendReturnsNull() {
        RecordingRuntime runtime = new RecordingRuntime();
        runtime.response.set(null);
        HierarchicalTeam team = makeTeam("sv", runtime);
        team.addAgent(supervisorCard("sv"), ignored -> new Object());

        List<Object> chunks = team.stream(Map.of("q", "test")).toList();

        assertThat(chunks).isEmpty();
    }

    private static void p2pInitInheritsAbilityManager() {
        assertThat(new P2PAbilityManager(new RecordingSupervisor())).isInstanceOf(AbilityManager.class);
    }

    private static void p2pInitSemaphoreReflectsMaxParallel() {
        assertThat(new P2PAbilityManager(new RecordingSupervisor(), 7).getMaxParallelSubAgents()).isEqualTo(7);
    }

    private static void p2pInitMaxParallelClampedToOneWhenZero() {
        assertThat(new P2PAbilityManager(new RecordingSupervisor(), 0).getMaxParallelSubAgents()).isEqualTo(1);
    }

    private static void p2pInitMaxParallelClampedToOneWhenNegative() {
        assertThat(new P2PAbilityManager(new RecordingSupervisor(), -5).getMaxParallelSubAgents()).isEqualTo(1);
    }

    private static void p2pInitSemaphoreLazilyCreatedAndCached() {
        P2PAbilityManager manager = new P2PAbilityManager(new RecordingSupervisor(), 3);

        assertThat(manager.getSemaphore()).isSameAs(manager.getSemaphore());
        assertThat(manager.getMaxParallelSubAgents()).isEqualTo(3);
    }

    private static void p2pAddStoresAgentCard() {
        P2PAbilityManager manager = new P2PAbilityManager(new RecordingSupervisor());
        AgentCard card = subCard("ax");

        manager.add(card);

        assertThat(manager.getAgents()).containsEntry("ax", card);
    }

    private static void p2pAddMultipleCards() {
        P2PAbilityManager manager = new P2PAbilityManager(new RecordingSupervisor());

        manager.add(subCard("a1"));
        manager.add(subCard("a2"));

        assertThat(manager.getAgents()).containsKeys("a1", "a2");
    }

    private static void p2pAddReturnsAddAbilityResult() {
        P2PAbilityManager manager = new P2PAbilityManager(new RecordingSupervisor());

        AddAbilityResult result = manager.add(subCard("ret"));

        assertThat(result).isInstanceOf(AddAbilityResult.class);
        assertThat(result.isAdded()).isTrue();
    }

    private static void p2pAddDuplicateReturnsNotAdded() {
        P2PAbilityManager manager = new P2PAbilityManager(new RecordingSupervisor());

        manager.add(subCard("dup"));
        AddAbilityResult result = manager.add(subCard("dup"));

        assertThat(result.isAdded()).isFalse();
        assertThat(result.getReason()).isEqualTo("duplicate_agent");
    }

    private static void p2pExecuteEmptyToolCallsReturnsEmptyList() {
        P2PAbilityManager manager = new P2PAbilityManager(new RecordingSupervisor());

        assertThat(manager.execute(null, List.of(), new TestSession("s1"))).isEmpty();
    }

    private static void p2pExecuteNonAgentCallDelegatesToBase() {
        Tool echo = echoArgumentsTool("unknown_tool");
        withRegisteredTool(echo, () -> {
            P2PAbilityManager manager = new P2PAbilityManager(new RecordingSupervisor());

            List<AbilityManager.ExecutionResult> results = manager.execute(
                    null,
                    toolCall("unknown_tool", Map.of("x", 1), "tc1"),
                    new TestSession("s1")
            );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).result()).isEqualTo(Map.of("x", 1));
        });
    }

    private static void p2pExecuteNonAgentSingleToolCallPassesThrough() {
        P2PAbilityManager manager = new P2PAbilityManager(new RecordingSupervisor());

        List<AbilityManager.ExecutionResult> results = manager.execute(
                null,
                toolCall("plain_tool", Map.of(), "pt1"),
                new TestSession("s1")
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).toolMessage().getToolCallId()).isEqualTo("pt1");
    }

    private static void p2pExecuteAgentCallInvokesSupervisorSend() {
        RecordingSupervisor supervisor = new RecordingSupervisor();
        P2PAbilityManager manager = new P2PAbilityManager(supervisor);
        manager.add(subCard("sub_a"));

        manager.execute(null, toolCall("sub_a", Map.of("x", 1), "tc1"), new TestSession("s1"));

        assertThat(supervisor.sendCount.get()).isEqualTo(1);
    }

    private static void p2pExecuteAgentCallRecipientMatchesAgentId() {
        RecordingSupervisor supervisor = new RecordingSupervisor();
        P2PAbilityManager manager = new P2PAbilityManager(supervisor);
        manager.add(subCard("agent_b"));

        manager.execute(null, toolCall("agent_b", Map.of(), "tc1"), new TestSession("s1"));

        assertThat(supervisor.lastRecipient.get()).isEqualTo("agent_b");
    }

    private static void p2pExecuteAgentCallPassesSessionId() {
        RecordingSupervisor supervisor = new RecordingSupervisor();
        P2PAbilityManager manager = new P2PAbilityManager(supervisor);
        manager.add(subCard("agent_c"));

        manager.execute(null, toolCall("agent_c", Map.of(), "tc1"), new TestSession("sess-42"));

        assertThat(supervisor.lastSessionId.get()).isEqualTo("sess-42");
    }

    private static void p2pExecuteAgentCallReturnsResultAndToolMessage() {
        RecordingSupervisor supervisor = new RecordingSupervisor();
        supervisor.response.set(Map.of("answer", 42));
        P2PAbilityManager manager = new P2PAbilityManager(supervisor);
        manager.add(subCard("ag"));

        List<AbilityManager.ExecutionResult> results =
                manager.execute(null, toolCall("ag", Map.of(), "tc1"), new TestSession("s1"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).result()).isEqualTo(Map.of("answer", 42));
        assertThat(results.get(0).toolMessage()).isNotNull();
    }

    private static void p2pExecuteToolMessageHasCorrectToolCallId() {
        RecordingSupervisor supervisor = new RecordingSupervisor();
        P2PAbilityManager manager = new P2PAbilityManager(supervisor);
        manager.add(subCard("ag2"));

        List<AbilityManager.ExecutionResult> results =
                manager.execute(null, toolCall("ag2", Map.of(), "call-xyz"), new TestSession("s1"));

        assertThat(results.get(0).toolMessage().getToolCallId()).isEqualTo("call-xyz");
    }

    private static void p2pExecuteFailureReturnsErrorToolMessage() {
        RecordingSupervisor supervisor = new RecordingSupervisor();
        supervisor.fail.set(true);
        P2PAbilityManager manager = new P2PAbilityManager(supervisor);
        manager.add(subCard("fail_ag"));

        List<AbilityManager.ExecutionResult> results =
                manager.execute(null, toolCall("fail_ag", Map.of(), "tf1"), new TestSession("s1"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).result()).isNull();
        assertThat(String.valueOf(results.get(0).toolMessage().getContent()))
                .contains("P2P parallel dispatch failed");
    }

    private static void p2pExecuteErrorToolMessageHasOriginalCallId() {
        RecordingSupervisor supervisor = new RecordingSupervisor();
        supervisor.fail.set(true);
        P2PAbilityManager manager = new P2PAbilityManager(supervisor);
        manager.add(subCard("fail2"));

        List<AbilityManager.ExecutionResult> results =
                manager.execute(null, toolCall("fail2", Map.of(), "err-id"), new TestSession("s1"));

        assertThat(results.get(0).toolMessage().getToolCallId()).isEqualTo("err-id");
    }

    private static void p2pParallelAllAgentCallsDispatched() {
        RecordingSupervisor supervisor = new RecordingSupervisor();
        P2PAbilityManager manager = new P2PAbilityManager(supervisor, 5);
        manager.add(subCard("s1"));
        manager.add(subCard("s2"));

        List<AbilityManager.ExecutionResult> results = manager.execute(
                null,
                List.of(toolCall("s1", Map.of(), "t1"), toolCall("s2", Map.of(), "t2")),
                new TestSession("sp")
        );

        assertThat(results).hasSize(2);
        assertThat(supervisor.recipients).containsExactlyInAnyOrder("s1", "s2");
    }

    private static void p2pParallelSemaphoreLimitsPeakConcurrency() {
        RecordingSupervisor supervisor = new RecordingSupervisor();
        supervisor.delayMillis.set(30);
        P2PAbilityManager manager = new P2PAbilityManager(supervisor, 2);
        for (int i = 0; i < 5; i++) {
            manager.add(subCard("ag" + i));
        }

        List<ToolCall> calls = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            calls.add(toolCall("ag" + i, Map.of(), "tc" + i));
        }
        manager.execute(null, calls, new TestSession("ss"));

        assertThat(supervisor.peakActive.get()).isLessThanOrEqualTo(2);
    }

    private static void p2pParallelResultOrderPreserved() {
        RecordingSupervisor supervisor = new RecordingSupervisor();
        supervisor.responseByRecipient.put("first", "r1");
        supervisor.responseByRecipient.put("second", "r2");
        P2PAbilityManager manager = new P2PAbilityManager(supervisor, 2);
        manager.add(subCard("first"));
        manager.add(subCard("second"));

        List<AbilityManager.ExecutionResult> results = manager.execute(
                null,
                List.of(toolCall("first", Map.of(), "c1"), toolCall("second", Map.of(), "c2")),
                new TestSession("sp")
        );

        assertThat(results).extracting(AbilityManager.ExecutionResult::result).containsExactly("r1", "r2");
    }

    private static void p2pParallelMixedAgentAndToolCallsBothExecuted() {
        Tool echo = echoArgumentsTool("reg_tool");
        withRegisteredTool(echo, () -> {
            RecordingSupervisor supervisor = new RecordingSupervisor();
            P2PAbilityManager manager = new P2PAbilityManager(supervisor);
            manager.add(subCard("sub_m"));

            List<AbilityManager.ExecutionResult> results = manager.execute(
                    null,
                    List.of(toolCall("sub_m", Map.of(), "ta"), toolCall("reg_tool", Map.of("regular", true), "tr")),
                    new TestSession("sm")
            );

            assertThat(results).hasSize(2);
            assertThat(results.get(0).result()).isEqualTo("sent:sub_m");
            assertThat(results.get(1).result()).isEqualTo(Map.of("regular", true));
        });
    }

    private static void supervisorInitAbilityManagerIsP2p() {
        assertThat(new SupervisorAgent(supervisorCard("sv_i")).getP2PAbilityManager())
                .isInstanceOf(P2PAbilityManager.class);
    }

    private static void supervisorInitIsCommunicableAgent() {
        assertThat(new SupervisorAgent(supervisorCard("sv_comm"))).isInstanceOf(CommunicableAgent.class);
    }

    private static void supervisorInitIsReactAgent() {
        assertThat(new SupervisorAgent(supervisorCard("sv_react"))).isInstanceOf(ReActAgent.class);
    }

    private static void supervisorInitRegisterSubAgentCardAddsToAbilityManager() {
        SupervisorAgent supervisor = new SupervisorAgent(supervisorCard("sv_r"));

        supervisor.registerSubAgentCard(subCard("sub1"));

        assertThat(supervisor.getP2PAbilityManager().getAgents()).containsKey("sub1");
    }

    private static void supervisorInitRegisterMultipleSubAgents() {
        SupervisorAgent supervisor = new SupervisorAgent(supervisorCard("sv_multi"));

        supervisor.registerSubAgentCard(subCard("s1"));
        supervisor.registerSubAgentCard(subCard("s2"));

        assertThat(supervisor.getP2PAbilityManager().getAgents()).containsKeys("s1", "s2");
    }

    private static void supervisorInitRegisterSubAgentEmitsDebugLogEquivalent() {
        SupervisorAgent supervisor = new SupervisorAgent(supervisorCard("sv_log"));

        supervisor.registerSubAgentCard(subCard("logged_sub"));

        assertThat(supervisor.getP2PAbilityManager().getAgents()).containsKey("logged_sub");
    }

    private static void supervisorConfigureReactConfigReturnsSelf() {
        SupervisorAgent supervisor = new SupervisorAgent(supervisorCard("sv_c"));

        assertThat(supervisor.configure(new ReActAgentConfig())).isSameAs(supervisor);
    }

    private static void supervisorConfigureNonReactIsNoopReturnsSelf() {
        SupervisorAgent supervisor = new SupervisorAgent(supervisorCard("sv_n"));

        assertThat(supervisor.configure(new Object())).isSameAs(supervisor);
    }

    private static void supervisorConfigureNoneIsNoopReturnsSelf() {
        SupervisorAgent supervisor = new SupervisorAgent(supervisorCard("sv_none"));

        assertThat(supervisor.configure(null)).isSameAs(supervisor);
    }

    private static void supervisorCreateReturnsCardAndCallableProvider() {
        SupervisorAgent.CreatedSupervisor created = createSupervisor(List.of(subCard("a1")), supervisorCard("sv_create"));

        assertThat(created.agentCard().getId()).isEqualTo("sv_create");
        assertThat(created.provider()).isNotNull();
    }

    private static void supervisorCreateEmptyAgentsRaises() {
        assertThatThrownBy(() -> createSupervisor(List.of(), supervisorCard("sv_e"))).isInstanceOf(BaseError.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void supervisorCreateNonAgentCardInListRaises() {
        List rawAgents = List.of("not_a_card");

        assertThatThrownBy(() -> SupervisorAgent.create(
                rawAgents,
                modelClientConfig(),
                modelRequestConfig(),
                supervisorCard("sv_bad"),
                "sys"
        )).isInstanceOf(BaseError.class);
    }

    private static void supervisorCreateProviderReturnsSupervisorAgentInstance() {
        SupervisorAgent.CreatedSupervisor created = createSupervisor(List.of(subCard("x1")), supervisorCard("sv_prov"));

        assertThat(created.provider().get()).isInstanceOf(SupervisorAgent.class);
    }

    private static void supervisorCreateProviderRegistersAllSubAgents() {
        SupervisorAgent.CreatedSupervisor created = createSupervisor(
                List.of(subCard("x1"), subCard("x2")),
                supervisorCard("sv_prov2")
        );

        SupervisorAgent instance = created.provider().get();

        assertThat(instance.getP2PAbilityManager().getAgents()).containsKeys("x1", "x2");
    }

    private static void supervisorCreateAgentCardIdMatchesSuppliedCard() {
        AgentCard supervisorCard = supervisorCard("exact_id");
        SupervisorAgent.CreatedSupervisor created = createSupervisor(List.of(subCard("sub")), supervisorCard);

        assertThat(created.agentCard()).isSameAs(supervisorCard);
    }

    private static void supervisorCreateWithCustomMaxIterations() {
        SupervisorAgent.CreatedSupervisor created = SupervisorAgent.create(
                List.of(subCard("sub")),
                modelClientConfig(),
                modelRequestConfig(),
                supervisorCard("sv_iter"),
                "sys",
                3,
                10
        );

        assertThat(created.agentCard().getId()).isEqualTo("sv_iter");
        assertThat(created.provider()).isNotNull();
    }

    private static void supervisorCreateWithCustomMaxParallelSubAgents() {
        SupervisorAgent.CreatedSupervisor created = SupervisorAgent.create(
                List.of(subCard("sub")),
                modelClientConfig(),
                modelRequestConfig(),
                supervisorCard("sv_par"),
                "sys",
                5,
                4
        );

        assertThat(created.provider().get().getP2PAbilityManager().getMaxParallelSubAgents()).isEqualTo(4);
    }

    private static TestCase testCase(String nodeId, Executable executable) {
        return new TestCase(nodeId, executable);
    }

    private static HierarchicalTeam makeTeam(String supervisorId) {
        return new HierarchicalTeam(teamCard("h_team"), makeConfig(supervisorId));
    }

    private static HierarchicalTeam makeTeam(String supervisorId, TeamRuntime runtime) {
        return new HierarchicalTeam(teamCard("h_team"), makeConfig(supervisorId), runtime);
    }

    private static HierarchicalTeamConfig makeConfig(String supervisorId) {
        return new HierarchicalTeamConfig(supervisorCard(supervisorId));
    }

    private static TeamCard teamCard(String teamId) {
        return new TeamCard(teamId, teamId, "hierarchical team");
    }

    private static AgentCard supervisorCard(String supervisorId) {
        return new AgentCard(supervisorId, supervisorId, "supervisor agent");
    }

    private static AgentCard subCard(String agentId) {
        return new AgentCard(agentId, agentId, "sub-agent " + agentId);
    }

    private static Tool echoArgumentsTool(String name) {
        return new Tool(ToolCard.builder()
                .id(name)
                .name(name)
                .description(name)
                .inputParams(Map.of("type", "object"))
                .build()) {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return inputs == null ? Map.of() : inputs;
            }
        };
    }

    private static void withRegisteredTool(Tool tool, Runnable action) {
        String toolId = tool.getCard().getId();
        Runner.resourceMgr().removeTool(toolId);
        Runner.resourceMgr().addTool(tool);
        try {
            action.run();
        } finally {
            Runner.resourceMgr().removeTool(toolId);
        }
    }

    private static ToolCall toolCall(String name, Map<String, Object> arguments, String callId) {
        ToolCall call = new ToolCall();
        call.setId(callId);
        call.setType("function");
        call.setName(name);
        try {
            call.setArguments(JSON.writeValueAsString(arguments));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
        return call;
    }

    private static ModelClientConfig modelClientConfig() {
        ModelClientConfig config = new ModelClientConfig();
        config.setClientProvider("openai");
        config.setApiKey("test-key");
        config.setApiBase("https://api.example.com");
        return config;
    }

    private static ModelRequestConfig modelRequestConfig() {
        ModelRequestConfig config = new ModelRequestConfig();
        config.setModelName("gpt-4");
        return config;
    }

    private static SupervisorAgent.CreatedSupervisor createSupervisor(List<AgentCard> agents, AgentCard supervisorCard) {
        return SupervisorAgent.create(
                agents,
                modelClientConfig(),
                modelRequestConfig(),
                supervisorCard,
                "You are a supervisor."
        );
    }

    private record TestCase(String nodeId, Executable executable) {
    }

    private static final class RecordingRuntime extends TeamRuntime {
        private final AtomicReference<Object> response = new AtomicReference<>("ok");
        private final AtomicReference<Object> lastMessage = new AtomicReference<>();
        private final AtomicReference<String> lastRecipient = new AtomicReference<>();
        private final AtomicReference<String> lastSender = new AtomicReference<>();
        private final AtomicReference<String> lastSessionId = new AtomicReference<>();
        private final AtomicReference<Double> lastTimeout = new AtomicReference<>();

        @Override
        public CompletableFuture<Object> send(
                Object message,
                String recipient,
                String sender,
                String sessionId,
                Double timeout
        ) {
            lastMessage.set(message);
            lastRecipient.set(recipient);
            lastSender.set(sender);
            lastSessionId.set(sessionId);
            lastTimeout.set(timeout);
            return CompletableFuture.completedFuture(response.get());
        }
    }

    private static final class RecordingSupervisor implements CommunicableAgent {
        private final AtomicReference<Object> response = new AtomicReference<>();
        private final Map<String, Object> responseByRecipient = new java.util.concurrent.ConcurrentHashMap<>();
        private final AtomicReference<Object> lastMessage = new AtomicReference<>();
        private final AtomicReference<String> lastRecipient = new AtomicReference<>();
        private final AtomicReference<String> lastSessionId = new AtomicReference<>();
        private final AtomicReference<Double> lastTimeout = new AtomicReference<>();
        private final AtomicInteger sendCount = new AtomicInteger();
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger peakActive = new AtomicInteger();
        private final AtomicInteger delayMillis = new AtomicInteger();
        private final AtomicReference<Boolean> fail = new AtomicReference<>(false);
        private final List<String> recipients = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<Object> send(Object message, String recipient, String sessionId, Double timeout) {
            sendCount.incrementAndGet();
            lastMessage.set(message);
            lastRecipient.set(recipient);
            lastSessionId.set(sessionId);
            lastTimeout.set(timeout);
            recipients.add(recipient);
            int current = active.incrementAndGet();
            peakActive.accumulateAndGet(current, Math::max);
            try {
                if (delayMillis.get() > 0) {
                    TimeUnit.MILLISECONDS.sleep(delayMillis.get());
                }
                if (Boolean.TRUE.equals(fail.get())) {
                    return CompletableFuture.failedFuture(new IllegalStateException("send failed"));
                }
                Object value = responseByRecipient.getOrDefault(recipient, response.get());
                if (value == null) {
                    value = "sent:" + recipient;
                }
                return CompletableFuture.completedFuture(value);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return CompletableFuture.failedFuture(exception);
            } finally {
                active.decrementAndGet();
            }
        }
    }

    private static final class TestSession implements AgentSessionApi {
        private final String sessionId;
        private final List<Object> writes = new ArrayList<>();

        private TestSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> data) {
        }

        @Override
        public void writeStream(Object data) {
            writes.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return writes.iterator();
        }
    }
}
