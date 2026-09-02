
package com.openjiuwen.agentteams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agentteams.agent.StreamController;
import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.factory.TeamFactory;
import com.openjiuwen.agentteams.interaction.HumanAgentNotEnabledError;
import com.openjiuwen.agentteams.interaction.Router;
import com.openjiuwen.agentteams.interaction.UnknownHumanAgentError;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.ModelPoolEntry;
import com.openjiuwen.agentteams.schema.team.TeamLifecycle;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.spawn.SpawnContext;
import com.openjiuwen.core.memory.team.TeamMemoryConfig;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptEntry;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TeamAgentCompatibilityTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void resetSpawnSession() {
        // resumePersistentTeam pins a new session id into SpawnContext's
        // InheritableThreadLocal and never restores the previous value.
        // Without this reset, the leaked id poisons later test classes whose
        // TeamBackend constructs from SpawnContext.getSessionId() and then
        // publishes on a session-prefixed topic the subscriber never matches.
        SpawnContext.setSessionId("");
    }

    @Test
    void specShouldInjectDefaultLeaderWhenMissing() {
        TeamAgentSpec spec = TeamAgentSpec.builder().name("research-team")
                .members(List.of(TeamMemberSpec.builder().name("analyst").role(TeamRole.MEMBER).build())).build();

        spec.build();

        assertThat(spec.getMembers()).hasSize(2);
        assertThat(spec.getMembers().get(0).getName()).isEqualTo(TeamConstants.DEFAULT_LEADER_MEMBER_NAME);
        assertThat(spec.getMembers().get(0).getRole()).isEqualTo(TeamRole.LEADER);
    }

    @Test
    void specShouldRejectReservedUserPseudoMemberName() {
        TeamAgentSpec spec = TeamAgentSpec.builder().name("bad-team")
                .members(List.of(TeamMemberSpec.builder().name(TeamConstants.USER_PSEUDO_MEMBER_NAME).build())).build();

        assertThatThrownBy(spec::validate).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reserved team member name");
    }

    @Test
    void routerShouldParseMentionAndReportReservedNames() {
        assertThat(Router.parseMention("@dev-1 please start task 123")).get()
                .extracting(route -> route.target(), route -> route.body())
                .containsExactly("dev-1", "please start task 123");
        assertThat(Router.parseMention("@dev-1")).isEmpty();
        assertThat(Router.parseMention("just a regular message")).isEmpty();
        assertThat(Router.isReservedName(TeamConstants.USER_PSEUDO_MEMBER_NAME)).isTrue();
        assertThat(Router.isReservedName(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)).isTrue();
        assertThat(Router.isReservedName("backend-dev-1")).isFalse();
    }

    @Test
    void teamFactoryShouldRouteMentionToMemberAndRestoreSnapshotState() {
        TeamAgentSpec spec = TeamAgentSpec.builder().name("support-team").description("Support coordination team")
                .members(List.of(TeamMemberSpec.builder().name("dispatcher").role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("resolver").role(TeamRole.MEMBER).build()))
                .modelPool(List
                        .of(ModelPoolEntry.builder().modelId("gpt-4").provider("openai").modelName("gpt-4").build()))
                .build();

        TeamAgent agent = TeamFactory.createAgentTeam(spec);
        Map<String, Object> result = agent.dispatchTask("@resolver Route a refund request.");

        assertThat(result).containsEntry("team_id", "support-team");
        assertThat(result).containsEntry("leader", "dispatcher");
        assertThat(result).containsEntry("member_count", 2);
        assertThat(result).containsEntry("route", "direct");
        assertThat(result).containsEntry("target", "resolver");
        assertThat(result).containsEntry("delivered_content", "Route a refund request.");
        assertThat(result.get("message_id")).isNotNull();
        assertThat(agent.getContext().getLifecycle()).isEqualTo(TeamLifecycle.RUNNING);
        assertThat(agent.getLeaderInbox()).isEmpty();
        assertThat(agent.getMessageManager().getMessages("resolver", false)).singleElement()
                .extracting(message -> message.getFromMemberName(), message -> message.getContent())
                .containsExactly(TeamConstants.USER_PSEUDO_MEMBER_NAME, "Route a refund request.");

        Map<String, Object> snapshot = agent.snapshot();
        TeamAgent recovered = TeamFactory.recoverAgentTeam(snapshot);
        assertThat(recovered.getSpec().getName()).isEqualTo("support-team");
        assertThat(recovered.getContext().getTeamId()).isEqualTo("support-team");
        assertThat(recovered.getMessageManager().getMessages("resolver", false)).singleElement()
                .extracting(message -> message.getFromMemberName(), message -> message.getContent())
                .containsExactly(TeamConstants.USER_PSEUDO_MEMBER_NAME, "Route a refund request.");
    }

    @Test
    void teamAgentShouldFallbackToLeaderInboxForPlainText() {
        TeamAgentSpec spec = TeamAgentSpec.builder().name("leader-team")
                .members(List.of(TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                        .role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("dev-1").role(TeamRole.MEMBER).build()))
                .build();

        TeamAgent agent = TeamFactory.createAgentTeam(spec);
        Map<String, Object> result = agent.dispatchTask("Please plan the release.");

        assertThat(result).containsEntry("route", "leader");
        assertThat(result).containsEntry("target", TeamConstants.DEFAULT_LEADER_MEMBER_NAME);
        assertThat(agent.getLeaderInbox()).containsExactly("Please plan the release.");
        assertThat(agent.getMessageManager().listAllMessages()).isEmpty();
    }

    @Test
    void teamAgentShouldSupportUserBroadcastAndHumanAgentMessages() {
        TeamAgentSpec spec = TeamAgentSpec.builder().name("hitt-team").humanAgentEnabled(true)
                .members(List.of(
                        TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER)
                                .build(),
                        TeamMemberSpec.builder().name("human_designer").role(TeamRole.HUMAN_AGENT).build(),
                        TeamMemberSpec.builder().name("human_pm").role(TeamRole.HUMAN_AGENT).build(),
                        TeamMemberSpec.builder().name("dev-1").role(TeamRole.MEMBER).build()))
                .build();

        TeamAgent agent = TeamFactory.createAgentTeam(spec);

        String broadcastId = agent.broadcast("everyone read this");
        String directHumanId = agent.humanAgentSay("on it", TeamConstants.DEFAULT_LEADER_MEMBER_NAME, "human_pm");
        String defaultHumanId = agent.humanAgentSay("looking", "dev-1", null);

        assertThat(broadcastId).isNotBlank();
        assertThat(directHumanId).isNotBlank();
        assertThat(defaultHumanId).isNotBlank();
        assertThat(agent.getMessageManager().getBroadcastMessages(false)).singleElement()
                .extracting(message -> message.getFromMemberName(), message -> message.getContent())
                .containsExactly(TeamConstants.USER_PSEUDO_MEMBER_NAME, "everyone read this");
        assertThat(agent.getMessageManager().getMessages(TeamConstants.DEFAULT_LEADER_MEMBER_NAME, false))
                .singleElement().extracting(message -> message.getFromMemberName(), message -> message.getContent())
                .containsExactly("human_pm", "on it");
        assertThat(agent.getMessageManager().getMessages("dev-1", false)).singleElement()
                .extracting(message -> message.getFromMemberName(), message -> message.getContent())
                .containsExactly("human_designer", "looking");
    }

    @Test
    void humanAgentInboxShouldRejectMissingOrUnknownHumanMembers() {
        TeamAgentSpec noHumanSpec = TeamAgentSpec.builder().name("no-human-team").members(List.of(
                TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build()))
                .build();
        TeamAgent noHumanAgent = TeamFactory.createAgentTeam(noHumanSpec);

        assertThatThrownBy(() -> noHumanAgent.humanAgentSay("hi", TeamConstants.DEFAULT_LEADER_MEMBER_NAME, null))
                .isInstanceOf(HumanAgentNotEnabledError.class)
                .hasMessageContaining("No human-agent member is registered");

        TeamAgentSpec multiHumanSpec = TeamAgentSpec.builder().name("multi-human-team").humanAgentEnabled(true)
                .members(List.of(
                        TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER)
                                .build(),
                        TeamMemberSpec.builder().name("human_designer").role(TeamRole.HUMAN_AGENT).build(),
                        TeamMemberSpec.builder().name("human_pm").role(TeamRole.HUMAN_AGENT).build()))
                .build();
        TeamAgent multiHumanAgent = TeamFactory.createAgentTeam(multiHumanSpec);

        assertThatThrownBy(
                () -> multiHumanAgent.humanAgentSay("spoof", TeamConstants.DEFAULT_LEADER_MEMBER_NAME, "ghost"))
                .isInstanceOf(UnknownHumanAgentError.class).hasMessageContaining("registered human-agent member");
    }

    @Test
    void teamAgentShouldUpdateModelPoolAndResumeWithNewSessionId() {
        TeamAgentSpec spec = TeamAgentSpec.builder().name("research-team")
                .members(List.of(TeamMemberSpec.builder().name("lead").role(TeamRole.LEADER).build())).build();

        TeamAgent agent = TeamFactory.createAgentTeam(spec);
        agent.updateModelPool(List.of(ModelPoolEntry.builder().modelId("claude").provider("anthropic")
                .modelName("claude-sonnet").weight(2).build()));
        TeamFactory.resumePersistentTeam(agent, "team-session-001");

        assertThat(agent.getSpec().getModelPool()).hasSize(1);
        assertThat(agent.getContext().getTeamId()).isEqualTo("research-team");
        assertThat(agent.getContext().getSessionId()).isEqualTo("team-session-001");
        assertThat(agent.getContext().getMetadata()).containsEntry("model_pool_size", 1);
        assertThat(agent.getContext().getMetadata()).containsEntry("session_id", "team-session-001");
    }

    @Test
    void teamFactoryShouldRecoverSnapshotSessionIdAndRuntimeTraces() {
        TeamAgentSpec spec = TeamAgentSpec.builder().name("snapshot-team")
                .members(List.of(TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                        .role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("worker-1").role(TeamRole.MEMBER).build()))
                .build();

        TeamAgent agent = TeamFactory.createAgentTeam(spec);
        agent.dispatchTask("Please plan the recovery slice.");
        agent.resumeForNewSession("team-session-002");

        Map<String, Object> snapshot = agent.snapshot();
        TeamAgent recovered = TeamFactory.recoverAgentTeam(snapshot);

        assertThat(recovered.getContext().getSessionId()).isEqualTo("team-session-002");
        assertThat(recovered.getContext().getMetadata()).containsEntry("session_id", "team-session-002");
        assertThat(recovered.getLeaderInbox()).containsExactly("Please plan the recovery slice.");
    }

    @Test
    void teamAgentShouldWireTeamMemoryIntoLeaderRuntime() throws Exception {
        Path workspace = tempDir.resolve("team-workspace");
        Files.createDirectories(workspace.resolve("memory"));
        Files.writeString(workspace.resolve("memory").resolve("MEMORY.md"), "leader remembers escalation policy");
        Path sharedDir = tempDir.resolve("team-memory");

        TeamAgentSpec spec = TeamAgentSpec.builder().name("memory-team").lifecycle("persistent").language("en")
                .members(List.of(TeamMemberSpec.builder().name("lead").role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("worker").role(TeamRole.MEMBER).build()))
                .memory(TeamMemoryConfig.builder().enabled(true).sharedMemory(true).scenario("general")
                        .teamMemoryDir(sharedDir.toString()).build())
                .build();

        TeamAgent agent = new TeamAgent().configure(spec,
                com.openjiuwen.agentteams.schema.team.TeamRuntimeContext.builder().teamId("memory-team")
                        .metadata(new java.util.LinkedHashMap<>(Map.of("workspace_path", workspace.toString())))
                        .build());
        Map<String, Object> result = agent.dispatchTask("policy");

        assertThat(result).containsEntry("team_id", "memory-team");
        assertThat(agent.getMemoryManager()).isNotNull();
        assertThat(agent.getMemoryManager().getOwnedToolNames()).contains("memory_search", "memory_get", "read_memory",
                "write_memory", "edit_memory");
        assertThat(agent.getDeepAgent().getAgent().getSystemPromptBuilder().hasSection("team_memory")).isTrue();
        assertThat(agent.getDeepAgent().getAgent().getSystemPromptBuilder().getSection("team_memory").render("en"))
                .contains("leader remembers escalation policy");

        agent.close();
        assertThat(agent.getMemoryManager().getOwnedToolNames()).isEmpty();
        assertThat(agent.getDeepAgent().getAgent().getSystemPromptBuilder().hasSection("team_memory")).isFalse();
    }

    @Test
    void teamAgentShouldQueueInputAndInterruptResumeDuringInFlightRound() {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder().name("stream-window-team")
                .members(List.of(TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                        .role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("worker-1").role(TeamRole.MEMBER).build()))
                .build());
        agent.getAgentSession()
                .updateState(Map.of(ToolInterruptionState.INTERRUPTION_KEY,
                        ToolInterruptionState.builder()
                                .interruptedTools(List.of(ToolInterruptEntry.builder()
                                        .request(InterruptRequest.builder().interruptId("call-1").build()).build()))
                                .build()));
        agent.setInFlightRound(true);

        agent.deliverInput("normal mailbox message");
        InteractiveInput resume = new InteractiveInput();
        resume.update("call-1", Map.of("approved", true));
        agent.resumeInterrupt(resume);

        assertThat(agent.getStreamController().getPendingInputs()).containsExactly("normal mailbox message");
        assertThat(agent.getStreamController().getPendingInterruptResumes()).containsExactly(resume);
        assertThat(agent.getContext().getMetadata()).containsEntry("pending_input_count", 1);
        assertThat(agent.getContext().getMetadata()).containsEntry("pending_interrupt_resume_count", 1);
        assertThat(agent.getLastResumedInterruptInput()).isNull();
    }

    @Test
    void streamShouldStartRoundThroughCoordinationQueueAndCloseAfterChunk() throws Exception {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder().name("stream-coordination-team")
                .members(List.of(TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                        .role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("worker-1").role(TeamRole.MEMBER).build()))
                .build());
        List<Map<String, Object>> streamedInputs = new ArrayList<>();
        StreamController controller = new StreamController(agent::getDeepAgent, agent::resolveLocalMemberName,
                status -> agent.getTeamBackend().updateMemberStatus(agent.resolveLocalMemberName(), status),
                ignored -> {
                }, agent::getAgentSession, () -> {
                }, (deepAgent, inputs, sessionId) -> {
                    streamedInputs.add(new java.util.LinkedHashMap<>(inputs));
                    return List.<Object>of(new OutputSchema("answer", 0, Map.of("output", inputs.get("query"))))
                            .iterator();
                });
        java.lang.reflect.Field field = TeamAgent.class.getDeclaredField("streamController");
        field.setAccessible(true);
        field.set(agent, controller);

        Iterator<Object> iterator = agent.stream(Map.of("query", "stream via queue"), "stream-session");

        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isInstanceOf(OutputSchema.class)
                .extracting(chunk -> ((OutputSchema) chunk).getPayload()).asString().contains("stream via queue");
        assertThat(iterator.hasNext()).isFalse();
        assertThat(streamedInputs).singleElement().extracting(inputs -> inputs.get("query"))
                .isEqualTo("stream via queue");
        assertThat(agent.getContext().getMetadata()).containsEntry("last_route", "stream_round");
        assertThat(agent.getContext().getMetadata()).doesNotContainKey("streaming_coordination");
        assertThat(agent.getStreamController().getPendingInputs()).isEmpty();
    }

    @Test
    void shutdownSelfShouldCancelStreamControllerAndCloseStream() {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder().name("shutdown-stream-team")
                .members(List.of(TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                        .role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("worker-1").role(TeamRole.MEMBER).build()))
                .build());

        agent.shutdownSelf();

        assertThat(agent.getContext().getLifecycle()).isEqualTo(TeamLifecycle.COMPLETED);
        assertThat(agent.getStreamController().getStreamQueue()).contains(StreamController.STREAM_END);
    }
}
