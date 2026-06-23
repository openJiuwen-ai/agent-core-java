/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.constants.TeamConstants;
import com.openjiuwen.agent_teams.interaction.DeliverResult;
import com.openjiuwen.agent_teams.interaction.HumanAgentInbox;
import com.openjiuwen.agent_teams.interaction.InteractionRouter;
import com.openjiuwen.agent_teams.interaction.UnknownHumanAgentError;
import com.openjiuwen.agent_teams.interaction.UserInbox;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.prompts.TeamPromptSections;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.TeamBackend.CapabilityOverrides;
import com.openjiuwen.agent_teams.tools.TeamTools.TeamTool;
import com.openjiuwen.agent_teams.tools.TeamTools.UpdateTaskTool;
import com.openjiuwen.agent_teams.tools.locales.TeamToolLocales;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.harness.tools.ToolOutput;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Supplemental parity tests for HITT behavior.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.test_hitt} in
 * {@code tests/unit_tests/agent_teams/test_hitt.py}.</p>
 */
class HittPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/agent_teams/test_hitt.py";
    private static final String HUMAN = TeamConstants.HUMAN_AGENT_MEMBER_NAME;
    private static final String USER = TeamConstants.USER_PSEUDO_MEMBER_NAME;
    private static final String TEAM = "hitt_team";
    private static final String LEADER = "team_leader";

    @AfterEach
    void resetSessionContext() {
        AgentTeamsContext.resetSessionId(null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pythonTestNodes")
    void mirrorsPythonHittTests(String pythonNodeId) {
        runPythonNode(pythonNodeId);
    }

    private static Stream<Arguments> pythonTestNodes() {
        return Stream.of(
                arg("test_parse_mention_returns_target_and_body"),
                arg("test_parse_mention_none_when_no_prefix"),
                arg("test_parse_mention_none_when_empty"),
                arg("test_parse_mention_none_when_only_mention"),
                arg("test_parse_mention_allows_reserved_target"),
                arg("test_is_reserved_name_enforced"),
                arg("test_enable_hitt_with_declared_human_agent_passes_validation"),
                arg("test_enable_hitt_true_without_human_agent_predefined_passes"),
                arg("test_enable_hitt_false_with_human_agent_predefined_raises"),
                arg("test_enable_hitt_false_no_human_agent_predefined_passes"),
                arg("test_leader_member_name_cannot_be_reserved"),
                arg("test_predefined_member_cannot_use_reserved_name"),
                arg("test_build_team_with_predefined_human_agent_registers_member"),
                arg("test_build_team_without_hitt_skips_human_agent"),
                arg("test_build_team_arg_enable_hitt_true_with_spec_false_raises"),
                arg("test_build_team_arg_enable_hitt_false_overrides_spec_true"),
                arg("test_build_team_arg_enable_hitt_none_inherits_spec"),
                arg("test_backend_spawn_human_agent_blocked_when_hitt_disabled"),
                arg("test_human_agent_role_tool_set"),
                arg("test_leader_role_tools_exclude_human_agent_only"),
                arg("test_cancel_task_owned_by_human_agent_is_refused"),
                arg("test_reassign_task_owned_by_human_agent_is_refused"),
                arg("test_cancel_all_preserves_human_agent_claimed_task"),
                arg("test_direct_message_to_human_agent_stays_unread"),
                arg("test_direct_message_to_regular_member_is_unread"),
                arg("test_broadcast_to_human_agent_stays_unread"),
                arg("test_user_inbox_direct_writes_as_user"),
                arg("test_user_inbox_broadcast_writes_as_user"),
                arg("test_human_agent_inbox_raises_when_hitt_off"),
                arg("test_human_agent_inbox_sends_as_human_agent"),
                arg("test_multi_human_spec_validates"),
                arg("test_multi_human_spec_with_enable_hitt_passes_consistency"),
                arg("test_build_team_registers_every_declared_human_member"),
                arg("test_direct_message_to_every_human_member_stays_unread"),
                arg("test_broadcast_to_every_human_member_stays_unread"),
                arg("test_task_lock_per_human_member"),
                arg("test_cancel_all_preserves_all_human_members"),
                arg("test_human_agent_inbox_requires_sender_on_multi_team"),
                arg("test_human_agent_inbox_posts_under_chosen_sender"),
                arg("test_hitt_section_none_when_no_human_members"),
                arg("test_hitt_section_leader_mentions_lock_rules"),
                arg("test_hitt_section_human_agent_describes_constrained_tools"),
                arg("test_hitt_section_human_agent_send_message_is_user_driven_cn"),
                arg("test_hitt_section_human_agent_send_message_is_user_driven_en"),
                arg("test_hitt_section_leader_lists_every_human_member"),
                arg("test_hitt_section_human_agent_tells_self_apart"),
                arg("test_hitt_section_human_agent_strictly_forbids_autonomous_behavior_cn"),
                arg("test_hitt_section_human_agent_strictly_forbids_autonomous_behavior_en"),
                arg("test_hitt_section_teammate_default_is_anonymous_cn"),
                arg("test_hitt_section_teammate_default_is_anonymous_en"),
                arg("test_hitt_section_teammate_with_expose_flag_lists_roster_cn"),
                arg("test_hitt_section_teammate_with_expose_flag_lists_roster_en"),
                arg("test_hitt_section_expose_flag_does_not_affect_leader_or_human_agent"),
                arg("test_reserved_member_names_set_content"),
                arg("test_resolve_team_mode_default_when_no_predefined"),
                arg("test_resolve_team_mode_ignores_human_agent_in_predefined"),
                arg("test_resolve_team_mode_hybrid_when_non_human_member"),
                arg("test_resolve_team_mode_hybrid_with_mixed_roster"),
                arg("test_resolve_team_mode_explicit_predefined_overrides_derivation"),
                arg("test_hitt_enabled_reflects_capability_not_roster"),
                arg("test_hitt_enabled_false_when_capability_disabled")
        );
    }

    private static Arguments arg(String name) {
        return Arguments.of(SOURCE + "::" + name);
    }

    private static void runPythonNode(String nodeId) {
        switch (nodeId) {
            case SOURCE + "::test_parse_mention_returns_target_and_body" -> parseMentionReturnsTargetAndBody();
            case SOURCE + "::test_parse_mention_none_when_no_prefix" -> parseMentionNoneWhenNoPrefix();
            case SOURCE + "::test_parse_mention_none_when_empty" -> parseMentionNoneWhenEmpty();
            case SOURCE + "::test_parse_mention_none_when_only_mention" -> parseMentionNoneWhenOnlyMention();
            case SOURCE + "::test_parse_mention_allows_reserved_target" -> parseMentionAllowsReservedTarget();
            case SOURCE + "::test_is_reserved_name_enforced" -> isReservedNameEnforced();
            case SOURCE + "::test_enable_hitt_with_declared_human_agent_passes_validation" -> enableHittWithDeclaredHumanAgentPassesValidation();
            case SOURCE + "::test_enable_hitt_true_without_human_agent_predefined_passes" -> enableHittTrueWithoutHumanAgentPredefinedPasses();
            case SOURCE + "::test_enable_hitt_false_with_human_agent_predefined_raises" -> enableHittFalseWithHumanAgentPredefinedRaises();
            case SOURCE + "::test_enable_hitt_false_no_human_agent_predefined_passes" -> enableHittFalseNoHumanAgentPredefinedPasses();
            case SOURCE + "::test_leader_member_name_cannot_be_reserved" -> leaderMemberNameCannotBeReserved();
            case SOURCE + "::test_predefined_member_cannot_use_reserved_name" -> predefinedMemberCannotUseReservedName();
            case SOURCE + "::test_build_team_with_predefined_human_agent_registers_member" -> buildTeamWithPredefinedHumanAgentRegistersMember();
            case SOURCE + "::test_build_team_without_hitt_skips_human_agent" -> buildTeamWithoutHittSkipsHumanAgent();
            case SOURCE + "::test_build_team_arg_enable_hitt_true_with_spec_false_raises" -> buildTeamArgEnableHittTrueWithSpecFalseRaises();
            case SOURCE + "::test_build_team_arg_enable_hitt_false_overrides_spec_true" -> buildTeamArgEnableHittFalseOverridesSpecTrue();
            case SOURCE + "::test_build_team_arg_enable_hitt_none_inherits_spec" -> buildTeamArgEnableHittNoneInheritsSpec();
            case SOURCE + "::test_backend_spawn_human_agent_blocked_when_hitt_disabled" -> backendSpawnHumanAgentBlockedWhenHittDisabled();
            case SOURCE + "::test_human_agent_role_tool_set" -> humanAgentRoleToolSet();
            case SOURCE + "::test_leader_role_tools_exclude_human_agent_only" -> leaderRoleToolsExcludeHumanAgentOnly();
            case SOURCE + "::test_cancel_task_owned_by_human_agent_is_refused" -> cancelTaskOwnedByHumanAgentIsRefused();
            case SOURCE + "::test_reassign_task_owned_by_human_agent_is_refused" -> reassignTaskOwnedByHumanAgentIsRefused();
            case SOURCE + "::test_cancel_all_preserves_human_agent_claimed_task" -> cancelAllPreservesHumanAgentClaimedTask();
            case SOURCE + "::test_direct_message_to_human_agent_stays_unread" -> directMessageToHumanAgentStaysUnread();
            case SOURCE + "::test_direct_message_to_regular_member_is_unread" -> directMessageToRegularMemberIsUnread();
            case SOURCE + "::test_broadcast_to_human_agent_stays_unread" -> broadcastToHumanAgentStaysUnread();
            case SOURCE + "::test_user_inbox_direct_writes_as_user" -> userInboxDirectWritesAsUser();
            case SOURCE + "::test_user_inbox_broadcast_writes_as_user" -> userInboxBroadcastWritesAsUser();
            case SOURCE + "::test_human_agent_inbox_raises_when_hitt_off" -> humanAgentInboxRaisesWhenHittOff();
            case SOURCE + "::test_human_agent_inbox_sends_as_human_agent" -> humanAgentInboxSendsAsHumanAgent();
            case SOURCE + "::test_multi_human_spec_validates" -> multiHumanSpecValidates();
            case SOURCE + "::test_multi_human_spec_with_enable_hitt_passes_consistency" -> multiHumanSpecWithEnableHittPassesConsistency();
            case SOURCE + "::test_build_team_registers_every_declared_human_member" -> buildTeamRegistersEveryDeclaredHumanMember();
            case SOURCE + "::test_direct_message_to_every_human_member_stays_unread" -> directMessageToEveryHumanMemberStaysUnread();
            case SOURCE + "::test_broadcast_to_every_human_member_stays_unread" -> broadcastToEveryHumanMemberStaysUnread();
            case SOURCE + "::test_task_lock_per_human_member" -> taskLockPerHumanMember();
            case SOURCE + "::test_cancel_all_preserves_all_human_members" -> cancelAllPreservesAllHumanMembers();
            case SOURCE + "::test_human_agent_inbox_requires_sender_on_multi_team" -> humanAgentInboxRequiresSenderOnMultiTeam();
            case SOURCE + "::test_human_agent_inbox_posts_under_chosen_sender" -> humanAgentInboxPostsUnderChosenSender();
            case SOURCE + "::test_hitt_section_none_when_no_human_members" -> hittSectionNoneWhenNoHumanMembers();
            case SOURCE + "::test_hitt_section_leader_mentions_lock_rules" -> hittSectionLeaderMentionsLockRules();
            case SOURCE + "::test_hitt_section_human_agent_describes_constrained_tools" -> hittSectionHumanAgentDescribesConstrainedTools();
            case SOURCE + "::test_hitt_section_human_agent_send_message_is_user_driven_cn" -> hittSectionHumanAgentSendMessageIsUserDrivenCn();
            case SOURCE + "::test_hitt_section_human_agent_send_message_is_user_driven_en" -> hittSectionHumanAgentSendMessageIsUserDrivenEn();
            case SOURCE + "::test_hitt_section_leader_lists_every_human_member" -> hittSectionLeaderListsEveryHumanMember();
            case SOURCE + "::test_hitt_section_human_agent_tells_self_apart" -> hittSectionHumanAgentTellsSelfApart();
            case SOURCE + "::test_hitt_section_human_agent_strictly_forbids_autonomous_behavior_cn" -> hittSectionHumanAgentStrictlyForbidsAutonomousBehaviorCn();
            case SOURCE + "::test_hitt_section_human_agent_strictly_forbids_autonomous_behavior_en" -> hittSectionHumanAgentStrictlyForbidsAutonomousBehaviorEn();
            case SOURCE + "::test_hitt_section_teammate_default_is_anonymous_cn" -> hittSectionTeammateDefaultIsAnonymousCn();
            case SOURCE + "::test_hitt_section_teammate_default_is_anonymous_en" -> hittSectionTeammateDefaultIsAnonymousEn();
            case SOURCE + "::test_hitt_section_teammate_with_expose_flag_lists_roster_cn" -> hittSectionTeammateWithExposeFlagListsRosterCn();
            case SOURCE + "::test_hitt_section_teammate_with_expose_flag_lists_roster_en" -> hittSectionTeammateWithExposeFlagListsRosterEn();
            case SOURCE + "::test_hitt_section_expose_flag_does_not_affect_leader_or_human_agent" -> hittSectionExposeFlagDoesNotAffectLeaderOrHumanAgent();
            case SOURCE + "::test_reserved_member_names_set_content" -> reservedMemberNamesSetContent();
            case SOURCE + "::test_resolve_team_mode_default_when_no_predefined" -> resolveTeamModeDefaultWhenNoPredefined();
            case SOURCE + "::test_resolve_team_mode_ignores_human_agent_in_predefined" -> resolveTeamModeIgnoresHumanAgentInPredefined();
            case SOURCE + "::test_resolve_team_mode_hybrid_when_non_human_member" -> resolveTeamModeHybridWhenNonHumanMember();
            case SOURCE + "::test_resolve_team_mode_hybrid_with_mixed_roster" -> resolveTeamModeHybridWithMixedRoster();
            case SOURCE + "::test_resolve_team_mode_explicit_predefined_overrides_derivation" -> resolveTeamModeExplicitPredefinedOverridesDerivation();
            case SOURCE + "::test_hitt_enabled_reflects_capability_not_roster" -> hittEnabledReflectsCapabilityNotRoster();
            case SOURCE + "::test_hitt_enabled_false_when_capability_disabled" -> hittEnabledFalseWhenCapabilityDisabled();
            default -> throw new IllegalArgumentException("Unhandled Python node: " + nodeId);
        }
    }

    private static void parseMentionReturnsTargetAndBody() {
        assertThat(InteractionRouter.parseMention("@dev-1 please start task 123"))
                .contains(new InteractionRouter.Mention("dev-1", "please start task 123"));
    }

    private static void parseMentionNoneWhenNoPrefix() {
        assertThat(InteractionRouter.parseMention("just a regular message")).isEmpty();
    }

    private static void parseMentionNoneWhenEmpty() {
        assertThat(InteractionRouter.parseMention("")).isEmpty();
    }

    private static void parseMentionNoneWhenOnlyMention() {
        assertThat(InteractionRouter.parseMention("@dev-1")).isEmpty();
    }

    private static void parseMentionAllowsReservedTarget() {
        assertThat(InteractionRouter.parseMention("@human_agent you decide"))
                .contains(new InteractionRouter.Mention(HUMAN, "you decide"));
    }

    private static void isReservedNameEnforced() {
        assertThat(List.of("user", "team_leader", "human_agent"))
                .allMatch(InteractionRouter::isReservedName);
        assertThat(InteractionRouter.isReservedName("backend-dev-1")).isFalse();
    }

    private static void enableHittWithDeclaredHumanAgentPassesValidation() {
        TeamAgentSpec spec = schemaSpec(true, configMember(HUMAN, AgentConfigurator.TeamRole.HUMAN_AGENT));
        invokeSpecValidation(spec, "validateHittConsistency");
    }

    private static void enableHittTrueWithoutHumanAgentPredefinedPasses() {
        TeamAgentSpec spec = schemaSpec(true);
        invokeSpecValidation(spec, "validateHittConsistency");
    }

    private static void enableHittFalseWithHumanAgentPredefinedRaises() {
        TeamAgentSpec spec = schemaSpec(false, configMember(HUMAN, AgentConfigurator.TeamRole.HUMAN_AGENT));
        assertThatThrownBy(() -> invokeSpecValidation(spec, "validateHittConsistency"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("enable_hitt=False");
    }

    private static void enableHittFalseNoHumanAgentPredefinedPasses() {
        TeamAgentSpec spec = schemaSpec(false);
        invokeSpecValidation(spec, "validateHittConsistency");
    }

    private static void leaderMemberNameCannotBeReserved() {
        TeamAgentSpec spec = schemaSpec(false);
        LeaderSpec leader = new LeaderSpec();
        leader.setMemberName(HUMAN);
        spec.setLeader(leader);

        assertThatThrownBy(() -> invokeSpecValidation(spec, "validateReservedNames"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }

    private static void predefinedMemberCannotUseReservedName() {
        TeamAgentSpec spec = schemaSpec(false, configMember(USER, AgentConfigurator.TeamRole.TEAMMATE));

        assertThatThrownBy(() -> invokeSpecValidation(spec, "validateReservedNames"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved name");
    }

    private static void buildTeamWithPredefinedHumanAgentRegistersMember() {
        Fixture fixture = builtHittTeam();

        TeamMember member = fixture.database().getMember(HUMAN, TEAM).toCompletableFuture().join().orElseThrow();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.UNSTARTED.value());
        assertThat(member.getExecutionStatus()).isEqualTo(ExecutionStatus.IDLE.value());
        assertThat(fixture.backend().hittEnabled()).isTrue();
    }

    private static void buildTeamWithoutHittSkipsHumanAgent() {
        Fixture fixture = fixture(false, List.of());
        fixture.backend().buildTeam("Plain Team", "test", "Leader", "Leader persona").toCompletableFuture().join();

        assertThat(fixture.database().getMember(HUMAN, TEAM).toCompletableFuture().join()).isEmpty();
        assertThat(fixture.backend().hittEnabled()).isFalse();
    }

    private static void buildTeamArgEnableHittTrueWithSpecFalseRaises() {
        Fixture fixture = fixture(false, List.of());

        assertThatThrownBy(() -> fixture.backend()
                .buildTeam("x", "y", "Leader", "z", new CapabilityOverrides(true, null))
                .toCompletableFuture()
                .join())
                .isInstanceOf(CompletionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("capability ceiling");
    }

    private static void buildTeamArgEnableHittFalseOverridesSpecTrue() {
        Fixture fixture = fixture(true, List.of(schemaMember(HUMAN, TeamRole.HUMAN_AGENT)));
        fixture.backend()
                .buildTeam("HITT Team", "test", "Leader", "Leader persona", new CapabilityOverrides(false, null))
                .toCompletableFuture()
                .join();

        assertThat(fixture.database().getMember(HUMAN, TEAM).toCompletableFuture().join()).isEmpty();
        assertThat(fixture.backend().hittEnabled()).isFalse();
    }

    private static void buildTeamArgEnableHittNoneInheritsSpec() {
        Fixture fixture = builtHittTeam();

        assertThat(fixture.backend().hittEnabled()).isTrue();
    }

    private static void backendSpawnHumanAgentBlockedWhenHittDisabled() {
        Fixture fixture = fixture(false, List.of());

        var result = fixture.backend().spawnHumanAgent("alice", "Alice", "human", null).toCompletableFuture().join();

        assertThat(result.isOk()).isFalse();
        assertThat(result.getReason()).contains("HITT capability is disabled");
    }

    private static void humanAgentRoleToolSet() {
        Fixture fixture = fixture(false, List.of());

        List<String> names = toolNames(TeamTools.createTeamTools(
                "human_agent", fixture.backend(), MemberMode.BUILD_MODE.value(), "temporary", null, null, null, "en"));

        assertThat(names).containsExactly("member_complete_task", "send_message", "view_task");
        assertThat(names).doesNotContain("claim_task", "update_task", "spawn_member");
    }

    private static void leaderRoleToolsExcludeHumanAgentOnly() {
        Fixture fixture = fixture(false, List.of());

        List<String> names = toolNames(TeamTools.createTeamTools(
                "leader", fixture.backend(), MemberMode.BUILD_MODE.value(), "temporary", null, null, null, "en"));

        assertThat(names).contains("build_team", "update_task", "send_message");
        assertThat(names).doesNotContain("member_complete_task");
    }

    private static void cancelTaskOwnedByHumanAgentIsRefused() {
        Fixture fixture = builtHittTeam();
        createAndAssign(fixture.backend(), "t-1", HUMAN);
        UpdateTaskTool tool = new UpdateTaskTool(fixture.backend(), TeamToolLocales.makeTranslator("cn"));

        ToolOutput output = tool.invoke(Map.of("task_id", "t-1", "status", "cancelled")).toCompletableFuture().join();

        assertThat(output.isSuccess()).isFalse();
        assertThat(output.getError()).isNotBlank();
        TeamTask task = fixture.backend().getTaskManager().get("t-1").toCompletableFuture().join().orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.CLAIMED.value());
        assertThat(task.getAssignee()).isEqualTo(HUMAN);
    }

    private static void reassignTaskOwnedByHumanAgentIsRefused() {
        Fixture fixture = builtHittTeam();
        createAndAssign(fixture.backend(), "t-2", HUMAN);
        UpdateTaskTool tool = new UpdateTaskTool(fixture.backend(), TeamToolLocales.makeTranslator("cn"));

        ToolOutput output = tool.invoke(Map.of("task_id", "t-2", "assignee", "other-member"))
                .toCompletableFuture()
                .join();

        assertThat(output.isSuccess()).isFalse();
        assertThat(fixture.backend().getTaskManager().get("t-2").toCompletableFuture().join().orElseThrow()
                .getAssignee()).isEqualTo(HUMAN);
    }

    private static void cancelAllPreservesHumanAgentClaimedTask() {
        Fixture fixture = builtHittTeam();
        createAndAssign(fixture.backend(), "t-human", HUMAN);
        fixture.backend().getTaskManager().add("open", "c", "t-open", null).toCompletableFuture().join();
        UpdateTaskTool tool = new UpdateTaskTool(fixture.backend(), TeamToolLocales.makeTranslator("cn"));

        ToolOutput output = tool.invoke(Map.of("task_id", "*", "status", "cancelled")).toCompletableFuture().join();

        assertThat(output.isSuccess()).isTrue();
        assertThat(task(fixture.backend(), "t-human").getStatus()).isEqualTo(TaskStatus.CLAIMED.value());
        assertThat(task(fixture.backend(), "t-open").getStatus()).isEqualTo(TaskStatus.CANCELLED.value());
    }

    private static void directMessageToHumanAgentStaysUnread() {
        Fixture fixture = builtHittTeam();

        String msgId = fixture.backend().getMessageManager().sendMessage("please review", HUMAN)
                .toCompletableFuture()
                .join();

        assertThat(msgId).isNotNull();
        List<TeamMessage> messages = fixture.backend().getMessageManager().getMessages(HUMAN)
                .toCompletableFuture()
                .join();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getIsRead()).isFalse();
    }

    private static void directMessageToRegularMemberIsUnread() {
        Fixture fixture = fixture(false, List.of());
        fixture.backend()
                .buildTeam("plain", "t", "Leader", "p", new CapabilityOverrides(false, null))
                .toCompletableFuture()
                .join();
        fixture.backend().spawnMember(
                "dev-1", "Dev", new AgentConfigurator.AgentCard("dev", "Dev", "dev"), "Dev",
                null, MemberStatus.UNSTARTED, ExecutionStatus.IDLE, MemberMode.BUILD_MODE, null, TeamRole.TEAMMATE)
                .toCompletableFuture()
                .join();

        fixture.backend().getMessageManager().sendMessage("hi", "dev-1").toCompletableFuture().join();

        List<TeamMessage> messages = fixture.backend().getMessageManager().getMessages("dev-1")
                .toCompletableFuture()
                .join();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getIsRead()).isFalse();
    }

    private static void broadcastToHumanAgentStaysUnread() {
        Fixture fixture = builtHittTeam();

        String msgId = fixture.backend().getMessageManager().broadcastMessage("global announcement")
                .toCompletableFuture()
                .join();

        assertThat(msgId).isNotNull();
        List<TeamMessage> unread = fixture.backend().getMessageManager().getBroadcastMessages(HUMAN, true)
                .toCompletableFuture()
                .join();
        assertThat(unread).hasSize(1);
        assertThat(unread.get(0).getMessageId()).isEqualTo(msgId);
    }

    private static void userInboxDirectWritesAsUser() {
        RecordingMessageBus bus = new RecordingMessageBus();
        UserInbox inbox = new UserInbox(bus);

        DeliverResult result = inbox.direct("alice", "look at this").toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(bus.directMessages).containsExactly(new MessageRecord("look at this", "alice", USER));
    }

    private static void userInboxBroadcastWritesAsUser() {
        RecordingMessageBus bus = new RecordingMessageBus();
        UserInbox inbox = new UserInbox(bus);

        DeliverResult result = inbox.broadcast("everyone read this").toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(bus.broadcasts).containsExactly(new MessageRecord("everyone read this", null, USER));
    }

    private static void humanAgentInboxRaisesWhenHittOff() {
        HumanAgentInbox inbox = new HumanAgentInbox(new InboxTeam(List.of(), List.of()), new RecordingMessageBus());

        assertThatThrownBy(() -> inbox.send("hello").toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(com.openjiuwen.agent_teams.interaction.HumanAgentNotEnabledError.class);
    }

    private static void humanAgentInboxSendsAsHumanAgent() {
        RecordingMessageBus bus = new RecordingMessageBus();
        HumanAgentInbox inbox = new HumanAgentInbox(new InboxTeam(List.of(HUMAN), List.of(HUMAN, "team_leader")), bus);

        DeliverResult result = inbox.send("ok", "team_leader").toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(bus.directMessages).containsExactly(new MessageRecord("ok", "team_leader", HUMAN));
    }

    private static void multiHumanSpecValidates() {
        TeamAgentSpec spec = schemaSpec(true,
                configMember("human_designer", AgentConfigurator.TeamRole.HUMAN_AGENT),
                configMember("human_pm", AgentConfigurator.TeamRole.HUMAN_AGENT));
        invokeSpecValidation(spec, "validateReservedNames");
    }

    private static void multiHumanSpecWithEnableHittPassesConsistency() {
        TeamAgentSpec spec = schemaSpec(true,
                configMember("human_designer", AgentConfigurator.TeamRole.HUMAN_AGENT),
                configMember("human_pm", AgentConfigurator.TeamRole.HUMAN_AGENT));
        invokeSpecValidation(spec, "validateHittConsistency");
    }

    private static void buildTeamRegistersEveryDeclaredHumanMember() {
        Fixture fixture = builtMultiHumanTeam();

        assertThat(fixture.backend().hittEnabled()).isTrue();
        assertThat(fixture.backend().isHumanAgent("human_designer").toCompletableFuture().join()).isTrue();
        assertThat(fixture.backend().isHumanAgent("human_pm").toCompletableFuture().join()).isTrue();
        assertThat(fixture.backend().isHumanAgent(LEADER).toCompletableFuture().join()).isFalse();
        assertThat(member(fixture.database(), "human_designer").getStatus()).isEqualTo(MemberStatus.UNSTARTED.value());
        assertThat(member(fixture.database(), "human_pm").getStatus()).isEqualTo(MemberStatus.UNSTARTED.value());
    }

    private static void directMessageToEveryHumanMemberStaysUnread() {
        Fixture fixture = builtMultiHumanTeam();
        TeamMessageManager messages = fixture.backend().getMessageManager();

        for (String name : List.of("human_designer", "human_pm")) {
            assertThat(messages.sendMessage("hi " + name, name).toCompletableFuture().join()).isNotNull();
            assertThat(messages.getMessages(name).toCompletableFuture().join())
                    .filteredOn(message -> name.equals(message.getToMemberName()))
                    .allMatch(message -> !Boolean.TRUE.equals(message.getIsRead()));
        }
    }

    private static void broadcastToEveryHumanMemberStaysUnread() {
        Fixture fixture = builtMultiHumanTeam();
        TeamMessageManager messages = fixture.backend().getMessageManager();

        String msgId = messages.broadcastMessage("hello team").toCompletableFuture().join();

        for (String name : List.of("human_designer", "human_pm")) {
            List<TeamMessage> unread = messages.getBroadcastMessages(name, true).toCompletableFuture().join();
            assertThat(unread).hasSize(1);
            assertThat(unread.get(0).getMessageId()).isEqualTo(msgId);
        }
    }

    private static void taskLockPerHumanMember() {
        Fixture fixture = builtMultiHumanTeam();
        createAndAssign(fixture.backend(), "t-designer", "human_designer");
        createAndAssign(fixture.backend(), "t-pm", "human_pm");
        UpdateTaskTool tool = new UpdateTaskTool(fixture.backend(), TeamToolLocales.makeTranslator("en"));

        ToolOutput designer = tool.invoke(Map.of("task_id", "t-designer", "status", "cancelled"))
                .toCompletableFuture()
                .join();
        ToolOutput pm = tool.invoke(Map.of("task_id", "t-pm", "assignee", LEADER))
                .toCompletableFuture()
                .join();

        assertThat(designer.isSuccess()).isFalse();
        assertThat(pm.isSuccess()).isFalse();
        assertThat(task(fixture.backend(), "t-designer").getAssignee()).isEqualTo("human_designer");
        assertThat(task(fixture.backend(), "t-pm").getAssignee()).isEqualTo("human_pm");
    }

    private static void cancelAllPreservesAllHumanMembers() {
        Fixture fixture = builtMultiHumanTeam();
        createAndAssign(fixture.backend(), "t-designer", "human_designer");
        createAndAssign(fixture.backend(), "t-pm", "human_pm");
        fixture.backend().getTaskManager().add("open", "c", "t-open", null).toCompletableFuture().join();
        UpdateTaskTool tool = new UpdateTaskTool(fixture.backend(), TeamToolLocales.makeTranslator("en"));

        ToolOutput output = tool.invoke(Map.of("task_id", "*", "status", "cancelled")).toCompletableFuture().join();

        assertThat(output.isSuccess()).isTrue();
        assertThat(task(fixture.backend(), "t-designer").getStatus()).isEqualTo(TaskStatus.CLAIMED.value());
        assertThat(task(fixture.backend(), "t-pm").getStatus()).isEqualTo(TaskStatus.CLAIMED.value());
        assertThat(task(fixture.backend(), "t-open").getStatus()).isEqualTo(TaskStatus.CANCELLED.value());
    }

    private static void humanAgentInboxRequiresSenderOnMultiTeam() {
        HumanAgentInbox inbox = new HumanAgentInbox(
                new InboxTeam(List.of("human_designer", "human_pm"), List.of(LEADER)),
                new RecordingMessageBus());

        assertThatThrownBy(() -> inbox.send("spoofing", LEADER, "ghost").toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(UnknownHumanAgentError.class);
    }

    private static void humanAgentInboxPostsUnderChosenSender() {
        RecordingMessageBus bus = new RecordingMessageBus();
        HumanAgentInbox inbox = new HumanAgentInbox(
                new InboxTeam(List.of("human_designer", "human_pm"), List.of(LEADER)),
                bus);

        inbox.send("ok", LEADER, "human_pm").toCompletableFuture().join();

        assertThat(bus.directMessages).containsExactly(new MessageRecord("ok", LEADER, "human_pm"));
    }

    private static void hittSectionNoneWhenNoHumanMembers() {
        assertThat(TeamPromptSections.buildTeamHittSection(
                AgentConfigurator.TeamRole.LEADER, List.of(), "cn", null, false)).isEmpty();
    }

    private static void hittSectionLeaderMentionsLockRules() {
        String body = hittSection(AgentConfigurator.TeamRole.LEADER, List.of(HUMAN), "cn", null, false);

        assertThat(body).contains(HUMAN, "send_message", "update_task");
    }

    private static void hittSectionHumanAgentDescribesConstrainedTools() {
        String body = hittSection(AgentConfigurator.TeamRole.HUMAN_AGENT, List.of(HUMAN), "en", HUMAN, false);

        assertThat(body).contains("send_message", "claim_task");
        assertThat(body.toLowerCase()).contains("do not");
    }

    private static void hittSectionHumanAgentSendMessageIsUserDrivenCn() {
        String body = hittSection(AgentConfigurator.TeamRole.HUMAN_AGENT, List.of(HUMAN), "cn", HUMAN, false);

        assertThat(body).contains("send_message");
        assertThat(body).doesNotContain("no `send_message`");
    }

    private static void hittSectionHumanAgentSendMessageIsUserDrivenEn() {
        String body = hittSection(AgentConfigurator.TeamRole.HUMAN_AGENT, List.of(HUMAN), "en", HUMAN, false);

        assertThat(body).contains("do have `send_message`");
        assertThat(body.toLowerCase()).contains("controller-driven");
        assertThat(body).doesNotContain("no `send_message`");
    }

    private static void hittSectionLeaderListsEveryHumanMember() {
        String body = hittSection(
                AgentConfigurator.TeamRole.LEADER, List.of("human_designer", "human_pm"), "cn", null, false);

        assertThat(body).contains("human_designer", "human_pm");
    }

    private static void hittSectionHumanAgentTellsSelfApart() {
        String body = hittSection(
                AgentConfigurator.TeamRole.HUMAN_AGENT, List.of("human_designer", "human_pm"), "cn", "human_pm", false);

        assertThat(body).contains("human_pm");
    }

    private static void hittSectionHumanAgentStrictlyForbidsAutonomousBehaviorCn() {
        String body = hittSection(AgentConfigurator.TeamRole.HUMAN_AGENT, List.of(HUMAN), "cn", HUMAN, false);

        assertThat(body).contains("send_message", "member_complete_task");
    }

    private static void hittSectionHumanAgentStrictlyForbidsAutonomousBehaviorEn() {
        String body = hittSection(AgentConfigurator.TeamRole.HUMAN_AGENT, List.of(HUMAN), "en", HUMAN, false);

        assertThat(body).contains("[For-Controller", "[Task Assigned For Controller]", "strictly forbidden",
                "send_message", "member_complete_task");
    }

    private static void hittSectionTeammateDefaultIsAnonymousCn() {
        String body = hittSection(
                AgentConfigurator.TeamRole.TEAMMATE, List.of("human_pm", "human_designer"), "cn", null, false);

        assertThat(body).contains("send_message");
        assertThat(body).doesNotContain("human_pm", "human_designer");
    }

    private static void hittSectionTeammateDefaultIsAnonymousEn() {
        String body = hittSection(
                AgentConfigurator.TeamRole.TEAMMATE, List.of("human_pm", "human_designer"), "en", null, false);

        assertThat(body).contains("send_message");
        assertThat(body).doesNotContain("human_pm", "human_designer");
        assertThat(body.toLowerCase()).doesNotContain("real humans");
    }

    private static void hittSectionTeammateWithExposeFlagListsRosterCn() {
        String body = hittSection(
                AgentConfigurator.TeamRole.TEAMMATE, List.of("human_pm", "human_designer"), "cn", null, true);

        assertThat(body).contains("human_pm", "human_designer", "send_message");
    }

    private static void hittSectionTeammateWithExposeFlagListsRosterEn() {
        String body = hittSection(
                AgentConfigurator.TeamRole.TEAMMATE, List.of("human_pm", "human_designer"), "en", null, true);

        assertThat(body).contains("human_pm", "human_designer", "send_message");
        assertThat(body.toLowerCase()).contains("real humans");
    }

    private static void hittSectionExposeFlagDoesNotAffectLeaderOrHumanAgent() {
        String leaderOff = hittSection(AgentConfigurator.TeamRole.LEADER, List.of("human_pm"), "cn", null, false);
        String leaderOn = hittSection(AgentConfigurator.TeamRole.LEADER, List.of("human_pm"), "cn", null, true);
        String humanOff = hittSection(
                AgentConfigurator.TeamRole.HUMAN_AGENT, List.of("human_pm"), "cn", "human_pm", false);
        String humanOn = hittSection(
                AgentConfigurator.TeamRole.HUMAN_AGENT, List.of("human_pm"), "cn", "human_pm", true);

        assertThat(leaderOff).isEqualTo(leaderOn);
        assertThat(humanOff).isEqualTo(humanOn);
    }

    private static void reservedMemberNamesSetContent() {
        assertThat(TeamConstants.RESERVED_MEMBER_NAMES).contains(HUMAN, USER, LEADER);
    }

    private static void resolveTeamModeDefaultWhenNoPredefined() {
        assertThat(AgentConfigurator.resolveTeamMode(configSpec())).isEqualTo("default");
    }

    private static void resolveTeamModeIgnoresHumanAgentInPredefined() {
        AgentConfigurator.TeamAgentSpec spec = configSpec(
                new AgentConfigurator.TeamMemberSpec(HUMAN, AgentConfigurator.TeamRole.HUMAN_AGENT, "x"));

        assertThat(AgentConfigurator.resolveTeamMode(spec)).isEqualTo("default");
    }

    private static void resolveTeamModeHybridWhenNonHumanMember() {
        AgentConfigurator.TeamAgentSpec spec = configSpec(
                new AgentConfigurator.TeamMemberSpec("dev_1", AgentConfigurator.TeamRole.TEAMMATE, "x"));

        assertThat(AgentConfigurator.resolveTeamMode(spec)).isEqualTo("hybrid");
    }

    private static void resolveTeamModeHybridWithMixedRoster() {
        AgentConfigurator.TeamAgentSpec spec = configSpec(
                new AgentConfigurator.TeamMemberSpec(HUMAN, AgentConfigurator.TeamRole.HUMAN_AGENT, "x"),
                new AgentConfigurator.TeamMemberSpec("dev_1", AgentConfigurator.TeamRole.TEAMMATE, "x"));

        assertThat(AgentConfigurator.resolveTeamMode(spec)).isEqualTo("hybrid");
    }

    private static void resolveTeamModeExplicitPredefinedOverridesDerivation() {
        AgentConfigurator.TeamAgentSpec spec = configSpec(
                new AgentConfigurator.TeamMemberSpec("dev_1", AgentConfigurator.TeamRole.TEAMMATE, "x"));
        spec.setTeamMode("predefined");

        assertThat(AgentConfigurator._resolveTeamMode(spec)).isEqualTo("predefined");
    }

    private static void hittEnabledReflectsCapabilityNotRoster() {
        Fixture fixture = fixture(true, List.of());

        assertThat(fixture.backend().hittEnabled()).isTrue();
        assertThat(fixture.backend().humanAgentNames().toCompletableFuture().join()).isEmpty();
    }

    private static void hittEnabledFalseWhenCapabilityDisabled() {
        Fixture fixture = fixture(false, List.of());

        assertThat(fixture.backend().hittEnabled()).isFalse();
    }

    private static TeamAgentSpec schemaSpec(
            boolean enableHitt,
            AgentConfigurator.TeamMemberSpec... predefinedMembers) {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName(TEAM);
        spec.setAgents(Map.of("leader", new AgentConfigurator.DeepAgentSpec()));
        spec.setEnableHitt(enableHitt);
        spec.setPredefinedMembers(List.of(predefinedMembers));
        return spec;
    }

    private static AgentConfigurator.TeamMemberSpec configMember(
            String name,
            AgentConfigurator.TeamRole role) {
        AgentConfigurator.TeamMemberSpec member = new AgentConfigurator.TeamMemberSpec(name, role, name + " persona");
        member.setDisplayName(title(name));
        return member;
    }

    private static AgentConfigurator.TeamAgentSpec configSpec(
            AgentConfigurator.TeamMemberSpec... predefinedMembers) {
        AgentConfigurator.TeamAgentSpec spec = new AgentConfigurator.TeamAgentSpec();
        spec.setAgents(Map.of("leader", new AgentConfigurator.DeepAgentSpec()));
        spec.setTeamName(TEAM);
        spec.setPredefinedMembers(List.of(predefinedMembers));
        return spec;
    }

    private static void invokeSpecValidation(TeamAgentSpec spec, String methodName) {
        try {
            Method method = TeamAgentSpec.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(spec);
        } catch (ReflectiveOperationException exc) {
            Throwable cause = exc.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError("Unable to invoke TeamAgentSpec." + methodName, exc);
        }
    }

    private static TeamMemberSpec schemaMember(String name, TeamRole role) {
        return new TeamMemberSpec(name, title(name), role, name + " persona");
    }

    private static Fixture fixture(boolean enableHitt, List<TeamMemberSpec> predefinedMembers) {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamBackend backend = new TeamBackend(
                TEAM,
                LEADER,
                true,
                database,
                messager,
                MemberMode.BUILD_MODE,
                predefinedMembers,
                null,
                null,
                enableHitt,
                false,
                List.of(),
                null,
                null,
                null,
                "team plan",
                LEADER);
        return new Fixture(database, backend);
    }

    private static Fixture builtHittTeam() {
        Fixture fixture = fixture(true, List.of(schemaMember(HUMAN, TeamRole.HUMAN_AGENT)));
        fixture.backend().buildTeam("HITT Team", "test", "Leader", "Leader persona").toCompletableFuture().join();
        return fixture;
    }

    private static Fixture builtMultiHumanTeam() {
        Fixture fixture = fixture(true, List.of(
                schemaMember("human_designer", TeamRole.HUMAN_AGENT),
                schemaMember("human_pm", TeamRole.HUMAN_AGENT)));
        fixture.backend().buildTeam("Multi", "t", "Leader", "p").toCompletableFuture().join();
        return fixture;
    }

    private static void createAndAssign(TeamBackend backend, String taskId, String assignee) {
        var created = backend.getTaskManager().add("t", "c", taskId, null).toCompletableFuture().join();
        assertThat(created.ok()).isTrue();
        var assigned = backend.getTaskManager().assign(taskId, assignee).toCompletableFuture().join();
        assertThat(assigned.ok()).isTrue();
    }

    private static TeamTask task(TeamBackend backend, String taskId) {
        return backend.getTaskManager().get(taskId).toCompletableFuture().join().orElseThrow();
    }

    private static TeamMember member(InMemoryTeamDatabase database, String memberName) {
        return database.getMember(memberName, TEAM).toCompletableFuture().join().orElseThrow();
    }

    private static List<String> toolNames(List<TeamTool> tools) {
        return tools.stream().map(tool -> tool.card().name()).sorted().toList();
    }

    private static String hittSection(
            AgentConfigurator.TeamRole role,
            List<String> names,
            String language,
            String selfMemberName,
            boolean exposeHumanAgentsToTeammates) {
        Optional<PromptSection> section = TeamPromptSections.buildTeamHittSection(
                role,
                names,
                language,
                selfMemberName,
                exposeHumanAgentsToTeammates);
        return section.orElseThrow().render(language);
    }

    private static String title(String name) {
        String value = name.replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record Fixture(InMemoryTeamDatabase database, TeamBackend backend) {
    }

    private record MessageRecord(String content, String toMemberName, String fromMemberName) {
    }

    private static final class InboxTeam implements HumanAgentInbox.TeamBackendView {
        private final List<String> humans;
        private final Set<String> members;

        private InboxTeam(List<String> humans, List<String> members) {
            this.humans = new ArrayList<>(humans);
            this.members = new LinkedHashSet<>(members);
        }

        @Override
        public CompletionStage<List<String>> humanAgentNames() {
            return CompletableFuture.completedFuture(humans);
        }

        @Override
        public CompletionStage<Object> getMember(String name) {
            return CompletableFuture.completedFuture(members.contains(name) ? name : null);
        }
    }

    private static final class RecordingMessageBus
            implements UserInbox.MessageManagerView, HumanAgentInbox.MessageManagerView {
        private final List<MessageRecord> broadcasts = new ArrayList<>();
        private final List<MessageRecord> directMessages = new ArrayList<>();

        @Override
        public CompletionStage<String> broadcastMessage(String content, String fromMemberName) {
            broadcasts.add(new MessageRecord(content, null, fromMemberName));
            return CompletableFuture.completedFuture("broadcast-" + broadcasts.size());
        }

        @Override
        public CompletionStage<String> sendMessage(String content, String toMemberName, String fromMemberName) {
            directMessages.add(new MessageRecord(content, toMemberName, fromMemberName));
            return CompletableFuture.completedFuture("direct-" + directMessages.size());
        }
    }

    /**
     * Recording messager collaborator for HITT backend tests.
     *
     * <p>Mirrors Python's mocked {@code Messager} fixture in
     * {@code tests/unit_tests/agent_teams/test_hitt.py}.</p>
     */
    private static final class RecordingMessager implements Messager {
        private final List<EventMessage> publishedMessages = new ArrayList<>();

        @Override
        public CompletionStage<Void> start() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> publish(String topicId, EventMessage message) {
            publishedMessages.add(message);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> subscribe(String topicId, MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unsubscribe(String topicId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> send(String agentId, EventMessage message) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> registerDirectMessageHandler(MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterDirectMessageHandler() {
            return CompletableFuture.completedFuture(null);
        }
    }
}
