package com.openjiuwen.agent_teams.worktree;

import com.openjiuwen.agent_teams.agent.TeamMember;
import com.openjiuwen.agent_teams.agent.TeamRail;
import com.openjiuwen.agent_teams.constants.TeamConstants;
import com.openjiuwen.agent_teams.interaction.HumanAgentInbox;
import com.openjiuwen.agent_teams.interaction.HumanAgentNotEnabledError;
import com.openjiuwen.agent_teams.interaction.MentionParser;
import com.openjiuwen.agent_teams.interaction.UnknownHumanAgentError;
import com.openjiuwen.agent_teams.interaction.UserInbox;
import com.openjiuwen.agent_teams.messager.InProcessMessager;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.message.MessageRecord;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.task.TaskDetail;
import com.openjiuwen.agent_teams.schema.task.TaskStatus;
import com.openjiuwen.agent_teams.tools.AgentTeamsToolRegistry;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.agent_teams.tools.TeamBackendRegistry;
import com.openjiuwen.agent_teams.tools.TeamToolOutput;
import com.openjiuwen.agent_teams.tools.UpdateTaskTool;
import com.openjiuwen.agent_teams.workspace.ConflictStrategy;
import com.openjiuwen.agent_teams.workspace.TeamWorkspaceConfig;
import com.openjiuwen.agent_teams.workspace.TeamWorkspaceManager;
import com.openjiuwen.agent_teams.workspace.WorkspaceFileLock;
import com.openjiuwen.agent_teams.workspace.WorkspaceMode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.test_hitt}
 * and {@code tests.unit_tests.agent_teams.worktree.test_manager}.
 */
class AgentTeamsWorktreeWorkspaceAndHittTest {

    @AfterEach
    void cleanup() {
        InProcessMessager.cleanupBus();
        TeamBackendRegistry.clear();
        WorktreeSessionHolder.setCurrentSession(null);
    }

    @Test
    void worktreeConfigDefaultsMatchPythonIntent() {
        WorktreeConfig config = new WorktreeConfig();
        assertFalse(config.isEnabled());
        assertEquals(30, config.getCleanupAfterDays());
        assertTrue(config.isAutoCleanupOnShutdown());
        assertEquals(WorktreeLifecyclePolicy.AUTO, config.getLifecyclePolicy());
    }

    @Test
    void worktreeManagerEnterSetsAndRemoveClearsCurrentSession() {
        MessagerTransportConfig transportConfig = new MessagerTransportConfig();
        transportConfig.setNodeId("leader");
        InProcessMessager messager = new InProcessMessager(transportConfig);
        WorktreeConfig config = new WorktreeConfig();
        config.setEnabled(true);
        config.setBaseDir("build/test-worktrees");

        WorktreeManager manager = new WorktreeManager(config, messager, "workspace-root");
        WorktreeSession session = manager.enter("slug-a", "member-a", "team-a");

        assertEquals(session, WorktreeSessionHolder.getCurrentSession());
        assertEquals("slug-a", session.getSlug());
        assertTrue(manager.removeCurrent(true));
        assertNull(WorktreeSessionHolder.getCurrentSession());
    }

    @Test
    void requireCurrentSessionUsesPythonAlignedErrorMessage() {
        WorktreeSessionHolder.setCurrentSession(null);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                WorktreeSessionHolder::requireCurrentSession
        );

        assertEquals("Not in a worktree session", error.getMessage());
    }

    @Test
    void workspaceModelsAndManagerProvideMinimalLockingAndMountSemantics() throws Exception {
        TeamWorkspaceConfig config = new TeamWorkspaceConfig();
        assertFalse(config.isEnabled());
        assertEquals(ConflictStrategy.LOCK, config.getConflictStrategy());
        assertEquals(
                List.of("artifacts/code", "artifacts/docs", "artifacts/reports", "trajectories"),
                config.getArtifactDirs()
        );

        Path shared = Files.createTempDirectory("shared-workspace");
        Path agentRoot = Files.createTempDirectory("agent-workspace");
        TeamWorkspaceManager manager = new TeamWorkspaceManager(
                config,
                shared.toString(),
                "team-alpha",
                WorkspaceMode.LOCAL
        );

        manager.mountIntoWorkspace(agentRoot.toString());
        assertTrue(Files.exists(agentRoot.resolve(".team").resolve("team-alpha")));

        WorkspaceFileLock lock = new WorkspaceFileLock("src/main.py", "m1", "Alice", Instant.now().toString());
        assertTrue(manager.acquireLock(lock));
        assertNotNull(manager.getLock("src/main.py"));
        assertFalse(manager.acquireLock(new WorkspaceFileLock("src/main.py", "m2", "Bob", Instant.now().toString())));
        assertTrue(manager.releaseLock("src/main.py", "m1"));
    }

    @Test
    void enableHittInjectsHumanAgentAndReservedNamesAreRejected() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setEnableHitt(true);
        spec.injectHumanAgentIfEnabled();
        assertTrue(spec.getPredefinedMembers().stream().anyMatch(member ->
                TeamConstants.HUMAN_AGENT_MEMBER_NAME.equals(member.getMemberName())
                        && member.getRoleType() == TeamRole.HUMAN_AGENT));

        TeamAgentSpec reservedLeader = new TeamAgentSpec();
        LeaderSpec leaderSpec = new LeaderSpec();
        leaderSpec.setMemberName(TeamConstants.HUMAN_AGENT_MEMBER_NAME);
        reservedLeader.setLeader(leaderSpec);
        assertThrows(IllegalArgumentException.class, reservedLeader::build);
    }

    @Test
    void parseMentionReturnsTargetAndBody() {
        MentionParser.Mention mention = MentionParser.parseMention("@dev-1 please start task 123");

        assertNotNull(mention);
        assertEquals("dev-1", mention.target());
        assertEquals("please start task 123", mention.body());
    }

    @Test
    void parseMentionReturnsNullWhenNoPrefix() {
        assertNull(MentionParser.parseMention("just a regular message"));
    }

    @Test
    void parseMentionReturnsNullWhenEmpty() {
        assertNull(MentionParser.parseMention(""));
    }

    @Test
    void parseMentionReturnsNullWhenOnlyMention() {
        assertNull(MentionParser.parseMention("@dev-1"));
    }

    @Test
    void parseMentionAllowsReservedTarget() {
        MentionParser.Mention mention = MentionParser.parseMention("@human_agent you decide");

        assertNotNull(mention);
        assertEquals(TeamConstants.HUMAN_AGENT_MEMBER_NAME, mention.target());
        assertEquals("you decide", mention.body());
    }

    @Test
    void isReservedNameEnforced() {
        assertTrue(MentionParser.isReservedName(TeamConstants.USER_PSEUDO_MEMBER_NAME));
        assertTrue(MentionParser.isReservedName(TeamConstants.DEFAULT_LEADER_MEMBER_NAME));
        assertTrue(MentionParser.isReservedName(TeamConstants.HUMAN_AGENT_MEMBER_NAME));
        assertFalse(MentionParser.isReservedName("backend-dev-1"));
    }

    @Test
    void enableHittInjectsHumanAgentMember() {
        TeamAgentSpec spec = minimalSpec();
        spec.setEnableHitt(true);

        spec.injectHumanAgentIfEnabled();

        assertTrue(spec.getPredefinedMembers().stream().anyMatch(member ->
                TeamConstants.HUMAN_AGENT_MEMBER_NAME.equals(member.getMemberName())
                        && member.getRoleType() == TeamRole.HUMAN_AGENT));
    }

    @Test
    void enableHittIsIdempotentOnExistingHumanAgent() {
        TeamMemberSpec preexisting = humanMemberSpec(
                TeamConstants.HUMAN_AGENT_MEMBER_NAME,
                "Custom Human",
                "Custom persona"
        );
        TeamAgentSpec spec = minimalSpec();
        spec.setEnableHitt(true);
        spec.setPredefinedMembers(List.of(preexisting));

        spec.injectHumanAgentIfEnabled();

        List<TeamMemberSpec> humanSlots = spec.getPredefinedMembers().stream()
                .filter(member -> TeamConstants.HUMAN_AGENT_MEMBER_NAME.equals(member.getMemberName()))
                .toList();
        assertEquals(1, humanSlots.size());
        assertEquals("Custom persona", humanSlots.get(0).getPersona());
    }

    @Test
    void enableHittFalseSkipsInjection() {
        TeamAgentSpec spec = minimalSpec();
        spec.setEnableHitt(false);

        spec.injectHumanAgentIfEnabled();

        assertTrue(spec.getPredefinedMembers().stream()
                .noneMatch(member -> TeamConstants.HUMAN_AGENT_MEMBER_NAME.equals(member.getMemberName())));
    }

    @Test
    void leaderMemberNameCannotBeReserved() {
        TeamAgentSpec spec = minimalSpec();
        LeaderSpec leader = new LeaderSpec();
        leader.setMemberName(TeamConstants.HUMAN_AGENT_MEMBER_NAME);
        spec.setLeader(leader);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, spec::build);
        assertTrue(error.getMessage().contains("reserved"));
    }

    @Test
    void predefinedMemberCannotUseReservedName() {
        TeamMemberSpec member = new TeamMemberSpec();
        member.setMemberName(TeamConstants.USER_PSEUDO_MEMBER_NAME);
        member.setDisplayName("x");
        member.setPersona("x");
        TeamAgentSpec spec = minimalSpec();
        spec.setPredefinedMembers(List.of(member));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, spec::build);
        assertTrue(error.getMessage().contains("reserved"));
    }

    @Test
    void buildTeamEnableHittRegistersHumanAgent() {
        TeamBackend backend = builtBackend("build-enable-hitt", true, List.of());

        TeamMember member = backend.getMember(TeamConstants.HUMAN_AGENT_MEMBER_NAME);
        assertNotNull(member);
        assertEquals(MemberStatus.READY, member.getStatus());
        assertEquals(ExecutionStatus.IDLE, member.getExecutionStatus());
        assertTrue(backend.hittEnabled());
        assertTrue(backend.isHumanAgent(TeamConstants.HUMAN_AGENT_MEMBER_NAME));
    }

    @Test
    void buildTeamWithoutHittSkipsHumanAgent() {
        TeamBackend backend = builtBackend("build-no-hitt", false, List.of());

        assertNull(backend.getMember(TeamConstants.HUMAN_AGENT_MEMBER_NAME));
        assertFalse(backend.hittEnabled());
        assertFalse(backend.isHumanAgent(TeamConstants.HUMAN_AGENT_MEMBER_NAME));
    }

    @Test
    void humanAgentRoleOnlyGetsSendMessage() {
        TeamBackend backend = newBackend("human-tools");

        List<String> names = toolNames(AgentTeamsToolRegistry.createTeamTools(backend, TeamRole.HUMAN_AGENT, "build_mode"));

        assertIterableEquals(List.of("send_message"), names);
    }

    @Test
    void leaderRoleToolsExcludeHumanAgentOnlyRestrictions() {
        TeamBackend backend = newBackend("leader-tools");

        Set<String> names = AgentTeamsToolRegistry.createTeamTools(backend, TeamRole.LEADER, "build_mode")
                .stream()
                .map(tool -> tool.getCard().getName())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertTrue(names.containsAll(List.of("build_team", "update_task", "send_message")));
        assertFalse(names.contains("claim_task"));
    }

    @Test
    void cancelTaskOwnedByHumanAgentIsRefused() throws Exception {
        TeamBackend backend = builtBackend("cancel-human-task", true, List.of());
        createAndAssign(backend, "t-1", TeamConstants.HUMAN_AGENT_MEMBER_NAME);
        UpdateTaskTool tool = new UpdateTaskTool(backend);

        TeamToolOutput out = (TeamToolOutput) tool.invoke(Map.of("task_id", "t-1", "status", "cancelled"));

        assertFalse(out.isSuccess());
        assertTrue(out.getError().toLowerCase().contains("human"));
        TaskDetail task = backend.getTask("t-1");
        assertEquals(TaskStatus.CLAIMED, task.getStatus());
        assertEquals(TeamConstants.HUMAN_AGENT_MEMBER_NAME, task.getAssignee());
    }

    @Test
    void reassignTaskOwnedByHumanAgentIsRefused() throws Exception {
        TeamBackend backend = builtBackend("reassign-human-task", true, List.of());
        createAndAssign(backend, "t-2", TeamConstants.HUMAN_AGENT_MEMBER_NAME);
        UpdateTaskTool tool = new UpdateTaskTool(backend);

        TeamToolOutput out = (TeamToolOutput) tool.invoke(Map.of("task_id", "t-2", "assignee", "other-member"));

        assertFalse(out.isSuccess());
        assertTrue(out.getError().toLowerCase().contains("human"));
        TaskDetail task = backend.getTask("t-2");
        assertEquals(TeamConstants.HUMAN_AGENT_MEMBER_NAME, task.getAssignee());
    }

    @Test
    void cancelAllPreservesHumanAgentClaimedTask() throws Exception {
        TeamBackend backend = builtBackend("cancel-all-human", true, List.of());
        createAndAssign(backend, "t-human", TeamConstants.HUMAN_AGENT_MEMBER_NAME);
        backend.createTask("open", "c", "t-open", List.of());
        UpdateTaskTool tool = new UpdateTaskTool(backend);

        TeamToolOutput out = (TeamToolOutput) tool.invoke(Map.of("task_id", "*", "status", "cancelled"));

        assertTrue(out.isSuccess());
        assertEquals(1, ((Map<?, ?>) out.getData()).get("cancelled_count"));
        assertEquals(TaskStatus.CLAIMED, backend.getTask("t-human").getStatus());
        assertEquals(TaskStatus.CANCELLED, backend.getTask("t-open").getStatus());
    }

    @Test
    void directMessageToHumanAgentIsAutoRead() {
        TeamBackend backend = builtBackend("auto-read-human", true, List.of());

        String messageId = backend.sendMessage("please review", TeamConstants.HUMAN_AGENT_MEMBER_NAME, "team_leader");

        assertNotNull(messageId);
        List<MessageRecord> messages = backend.getMessages(TeamConstants.HUMAN_AGENT_MEMBER_NAME, false, null);
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).isRead());
    }

    @Test
    void directMessageToRegularMemberIsUnread() {
        TeamBackend backend = builtBackend("regular-message-unread", false, List.of());
        backend.spawnMember(
                "dev-1",
                "Dev",
                null,
                "developer",
                null,
                MemberStatus.READY,
                ExecutionStatus.IDLE
        );

        String messageId = backend.sendMessage("hi", "dev-1", "team_leader");

        assertNotNull(messageId);
        List<MessageRecord> messages = backend.getMessages("dev-1", false, null);
        assertEquals(1, messages.size());
        assertFalse(messages.get(0).isRead());
    }

    @Test
    void broadcastAutoAdvancesHumanAgentReadWatermark() {
        TeamBackend backend = builtBackend("broadcast-auto-read", true, List.of());

        String messageId = backend.broadcastMessage("global announcement", "team_leader");

        assertNotNull(messageId);
        assertTrue(backend.getBroadcastMessages(TeamConstants.HUMAN_AGENT_MEMBER_NAME, true, null).isEmpty());
    }

    @Test
    void userInboxDirectWritesAsUser() {
        RecordingBackend backend = new RecordingBackend("user-direct", List.of(), "alice");
        UserInbox inbox = new UserInbox(backend, body -> Map.of("leader_body", body));

        Object result = inbox.direct("alice", "look at this");

        assertEquals("direct-1", result);
        assertEquals("alice", backend.lastDirectTarget);
        assertEquals("look at this", backend.lastDirectContent);
        assertEquals(TeamConstants.USER_PSEUDO_MEMBER_NAME, backend.lastDirectSender);
    }

    @Test
    void userInboxBroadcastWritesAsUser() {
        RecordingBackend backend = new RecordingBackend("user-broadcast", List.of(), "alice");
        UserInbox inbox = new UserInbox(backend, body -> Map.of("leader_body", body));

        Object result = inbox.broadcast("everyone read this");

        assertInstanceOf(Map.class, result);
        assertEquals("everyone read this", backend.lastBroadcastContent);
        assertEquals(TeamConstants.USER_PSEUDO_MEMBER_NAME, backend.lastBroadcastSender);
    }

    @Test
    void humanAgentInboxRaisesWhenHittOff() {
        RecordingBackend backend = new RecordingBackend("human-off", List.of(), "team_leader");
        HumanAgentInbox inbox = new HumanAgentInbox(backend);

        assertThrows(HumanAgentNotEnabledError.class, () -> inbox.send("hi"));
    }

    @Test
    void humanAgentInboxSendsAsHumanAgent() {
        RecordingBackend backend = new RecordingBackend(
                "human-default-send",
                List.of(TeamConstants.HUMAN_AGENT_MEMBER_NAME),
                "team_leader"
        );
        HumanAgentInbox inbox = new HumanAgentInbox(backend);

        Object result = inbox.send("on it");

        assertInstanceOf(Map.class, result);
        assertEquals("on it", backend.lastBroadcastContent);
        assertEquals(TeamConstants.HUMAN_AGENT_MEMBER_NAME, backend.lastBroadcastSender);
    }

    @Test
    void multiHumanSpecValidates() {
        TeamAgentSpec spec = multiHumanSpec();

        assertDoesNotThrow(() -> invokeReservedNameValidation(spec));
    }

    @Test
    void enableHittDoesNotReinjectWhenDeclared() {
        TeamAgentSpec spec = multiHumanSpec();
        spec.setEnableHitt(true);
        Set<String> before = memberNames(spec.getPredefinedMembers());

        spec.injectHumanAgentIfEnabled();

        Set<String> after = memberNames(spec.getPredefinedMembers());
        assertEquals(before, after);
        assertFalse(after.contains(TeamConstants.HUMAN_AGENT_MEMBER_NAME));
    }

    @Test
    void buildTeamRegistersEveryDeclaredHumanMember() {
        TeamBackend backend = multiHumanBackend("multi-registers");

        assertTrue(backend.hittEnabled());
        assertTrue(backend.isHumanAgent("human_designer"));
        assertTrue(backend.isHumanAgent("human_pm"));
        assertFalse(backend.isHumanAgent("team_leader"));
        for (String name : List.of("human_designer", "human_pm")) {
            TeamMember member = backend.getMember(name);
            assertNotNull(member);
            assertEquals(MemberStatus.READY, member.getStatus());
            assertEquals(ExecutionStatus.IDLE, member.getExecutionStatus());
        }
    }

    @Test
    void directMessageAutoReadForEveryHumanMember() {
        TeamBackend backend = multiHumanBackend("multi-direct-auto-read");

        for (String name : List.of("human_designer", "human_pm")) {
            String messageId = backend.sendMessage("hi " + name, name, "team_leader");
            assertNotNull(messageId);
            assertTrue(backend.getMessages(name, false, null).stream()
                    .anyMatch(message -> name.equals(message.getToMemberName()) && message.isRead()));
        }
    }

    @Test
    void broadcastAutoMarksReadForEveryHumanMember() {
        TeamBackend backend = multiHumanBackend("multi-broadcast-auto-read");

        String messageId = backend.broadcastMessage("hello team", "team_leader");

        assertNotNull(messageId);
        for (String name : List.of("human_designer", "human_pm")) {
            assertTrue(backend.getBroadcastMessages(name, true, null).isEmpty());
        }
    }

    @Test
    void taskLockPerHumanMember() throws Exception {
        TeamBackend backend = multiHumanBackend("multi-task-lock");
        createAndAssign(backend, "t-designer", "human_designer");
        createAndAssign(backend, "t-pm", "human_pm");
        UpdateTaskTool tool = new UpdateTaskTool(backend);

        TeamToolOutput designer = (TeamToolOutput) tool.invoke(Map.of("task_id", "t-designer", "status", "cancelled"));
        TeamToolOutput pm = (TeamToolOutput) tool.invoke(Map.of("task_id", "t-pm", "assignee", "team_leader"));

        assertFalse(designer.isSuccess());
        assertFalse(pm.isSuccess());
        assertEquals("human_designer", backend.getTask("t-designer").getAssignee());
        assertEquals("human_pm", backend.getTask("t-pm").getAssignee());
    }

    @Test
    void cancelAllPreservesAllHumanMembers() throws Exception {
        TeamBackend backend = multiHumanBackend("multi-cancel-all");
        createAndAssign(backend, "t-designer", "human_designer");
        createAndAssign(backend, "t-pm", "human_pm");
        backend.createTask("open", "c", "t-open", List.of());
        UpdateTaskTool tool = new UpdateTaskTool(backend);

        TeamToolOutput out = (TeamToolOutput) tool.invoke(Map.of("task_id", "*", "status", "cancelled"));

        assertTrue(out.isSuccess());
        assertEquals(TaskStatus.CLAIMED, backend.getTask("t-designer").getStatus());
        assertEquals(TaskStatus.CLAIMED, backend.getTask("t-pm").getStatus());
        assertEquals(TaskStatus.CANCELLED, backend.getTask("t-open").getStatus());
    }

    @Test
    void humanAgentInboxRequiresSenderOnMultiTeam() {
        RecordingBackend backend = new RecordingBackend(
                "human-unknown-sender",
                List.of("human_designer", "human_pm"),
                "team_leader"
        );
        HumanAgentInbox inbox = new HumanAgentInbox(backend);

        assertThrows(UnknownHumanAgentError.class, () -> inbox.sendAs("ghost", "spoofing"));
    }

    @Test
    void humanAgentInboxPostsUnderChosenSender() {
        RecordingBackend backend = new RecordingBackend(
                "human-explicit-sender",
                List.of("human_designer", "human_pm"),
                "team_leader"
        );
        HumanAgentInbox inbox = new HumanAgentInbox(backend);

        Object result = inbox.sendAs("human_pm", "ok");

        assertInstanceOf(Map.class, result);
        assertEquals("human_pm", backend.lastBroadcastSender);
        assertEquals("ok", backend.lastBroadcastContent);
    }

    @Test
    void hittSectionNoneWhenNoHumanMembers() {
        assertNull(TeamRail.buildTeamHittSection(TeamRole.LEADER, List.of(), "en", null));
    }

    @Test
    void hittSectionLeaderMentionsLockRules() {
        PromptSection section = TeamRail.buildTeamHittSection(
                TeamRole.LEADER,
                List.of(TeamConstants.HUMAN_AGENT_MEMBER_NAME),
                "en",
                null
        );

        assertNotNull(section);
        String body = section.render("en");
        assertTrue(body.contains(TeamConstants.HUMAN_AGENT_MEMBER_NAME));
        assertTrue(body.contains("send_message"));
        assertTrue(body.contains("must not") || body.contains("cannot"));
        assertTrue(body.contains("claimed"));
    }

    @Test
    void hittSectionHumanAgentDescribesConstrainedTools() {
        PromptSection section = TeamRail.buildTeamHittSection(
                TeamRole.HUMAN_AGENT,
                List.of(TeamConstants.HUMAN_AGENT_MEMBER_NAME),
                "en",
                TeamConstants.HUMAN_AGENT_MEMBER_NAME
        );

        assertNotNull(section);
        String body = section.render("en");
        assertTrue(body.contains("Your member_name is `" + TeamConstants.HUMAN_AGENT_MEMBER_NAME + "`"));
        assertTrue(body.contains("send_message"));
        assertTrue(body.contains("claim_task"));
    }

    @Test
    void hittSectionLeaderListsEveryHumanMember() {
        PromptSection section = TeamRail.buildTeamHittSection(
                TeamRole.LEADER,
                List.of("human_designer", "human_pm"),
                "en",
                null
        );

        assertNotNull(section);
        String body = section.render("en");
        assertTrue(body.contains("human_designer"));
        assertTrue(body.contains("human_pm"));
    }

    @Test
    void hittSectionHumanAgentTellsSelfApart() {
        PromptSection section = TeamRail.buildTeamHittSection(
                TeamRole.HUMAN_AGENT,
                List.of("human_designer", "human_pm"),
                "en",
                "human_pm"
        );

        assertNotNull(section);
        String body = section.render("en");
        assertTrue(body.contains("Your member_name is `human_pm`"));
        assertTrue(body.contains("human_designer"));
        assertTrue(body.contains("human_pm"));
    }

    @Test
    void reservedMemberNamesSetContent() {
        assertTrue(TeamConstants.RESERVED_MEMBER_NAMES.contains(TeamConstants.HUMAN_AGENT_MEMBER_NAME));
        assertTrue(TeamConstants.RESERVED_MEMBER_NAMES.contains(TeamConstants.USER_PSEUDO_MEMBER_NAME));
        assertTrue(TeamConstants.RESERVED_MEMBER_NAMES.contains(TeamConstants.DEFAULT_LEADER_MEMBER_NAME));
    }

    private static TeamAgentSpec minimalSpec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("hitt_team");
        return spec;
    }

    private static TeamAgentSpec multiHumanSpec() {
        TeamAgentSpec spec = minimalSpec();
        spec.setTeamName("multi_hitt_team");
        spec.setPredefinedMembers(List.of(
                humanMemberSpec("human_designer", "Designer", "Visual designer"),
                humanMemberSpec("human_pm", "Product Manager", "PM")
        ));
        return spec;
    }

    private static TeamMemberSpec humanMemberSpec(String name, String displayName, String persona) {
        TeamMemberSpec spec = new TeamMemberSpec();
        spec.setMemberName(name);
        spec.setDisplayName(displayName);
        spec.setRoleType(TeamRole.HUMAN_AGENT);
        spec.setPersona(persona);
        return spec;
    }

    private static TeamBackend newBackend(String suffix) {
        return new TeamBackend("hitt-" + suffix, "team_leader", true, null, List.of());
    }

    private static TeamBackend builtBackend(String suffix, boolean enableHitt, List<TeamMemberSpec> predefinedMembers) {
        TeamBackend backend = new TeamBackend("hitt-" + suffix, "team_leader", true, null, predefinedMembers);
        backend.buildTeam("HITT Team", "test", "Leader", "Leader persona", enableHitt);
        return backend;
    }

    private static TeamBackend multiHumanBackend(String suffix) {
        return builtBackend("multi-" + suffix, false, List.of(
                humanMemberSpec("human_designer", "Designer", "Visual designer"),
                humanMemberSpec("human_pm", "PM", "Product")
        ));
    }

    private static void createAndAssign(TeamBackend backend, String taskId, String assignee) {
        backend.createTask("t", "c", taskId, List.of());
        assertTrue(backend.assignTask(taskId, assignee));
    }

    private static List<String> toolNames(List<Tool> tools) {
        return tools.stream()
                .map(tool -> tool.getCard().getName())
                .sorted()
                .toList();
    }

    private static Set<String> memberNames(List<TeamMemberSpec> members) {
        return members.stream()
                .map(TeamMemberSpec::getMemberName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static void invokeReservedNameValidation(TeamAgentSpec spec) throws Exception {
        Method method = TeamAgentSpec.class.getDeclaredMethod("validateReservedMemberNames");
        method.setAccessible(true);
        method.invoke(spec);
    }

    private static final class RecordingBackend extends TeamBackend {
        private final List<String> humanNames;
        private final Set<String> knownMembers;
        private String lastDirectTarget;
        private String lastDirectContent;
        private String lastDirectSender;
        private String lastBroadcastContent;
        private String lastBroadcastSender;

        private RecordingBackend(String teamName, List<String> humanNames, String... memberNames) {
            super(teamName, "team_leader", true, null, List.of());
            this.humanNames = new ArrayList<>(humanNames);
            this.knownMembers = new LinkedHashSet<>(Arrays.asList(memberNames));
            this.knownMembers.addAll(humanNames);
        }

        @Override
        public boolean hasMember(String memberName) {
            return knownMembers.contains(memberName);
        }

        @Override
        public String sendMessage(String content, String toMemberName, String fromMemberName) {
            this.lastDirectContent = content;
            this.lastDirectTarget = toMemberName;
            this.lastDirectSender = fromMemberName;
            return "direct-1";
        }

        @Override
        public Map<String, Object> broadcastMessageToMembers(String content, String fromMemberName) {
            this.lastBroadcastContent = content;
            this.lastBroadcastSender = fromMemberName;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message_id", "broadcast-1");
            result.put("triggered_members", List.of());
            return result;
        }

        @Override
        public List<String> humanAgentNames() {
            return new ArrayList<>(humanNames);
        }

        @Override
        public boolean isHumanAgent(String memberName) {
            return humanNames.contains(memberName);
        }

        @Override
        public boolean hittEnabled() {
            return !humanNames.isEmpty();
        }
    }
}
