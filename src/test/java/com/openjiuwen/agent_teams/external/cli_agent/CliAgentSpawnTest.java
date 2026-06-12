/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external.cli_agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import com.openjiuwen.agent_teams.agent.MemberRuntime;
import com.openjiuwen.agent_teams.external.ExternalCliRuntime;
import com.openjiuwen.agent_teams.external.ReinvokeCliRuntime;
import com.openjiuwen.agent_teams.external.TeamJoinDescriptor;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.session.stream.OutputSchema;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link CliAgentSpawn}.
 *
 * <p>Mirrors Python's {@code spawn.py} helpers in
 * {@code openjiuwen/agent_teams/external/cli_agent/spawn.py}.</p>
 */
class CliAgentSpawnTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void descriptorFromContextBuildsJoinEnvAndAvoidsDirectAddrCollision() throws Exception {
        AgentTeamsContext.SessionIdToken token = AgentTeamsContext.setSessionId("session-1");
        try {
            TeamRuntimeContext ctx = context("codex");
            MessagerTransportConfig transport = new MessagerTransportConfig();
            transport.setBackend("pyzmq");
            transport.setTeamName("team-a");
            transport.setNodeId("dev");
            transport.setDirectAddr("tcp://127.0.0.1:17000");
            transport.setPubsubPublishAddr("tcp://pub");
            transport.setPubsubSubscribeAddr("tcp://sub");
            ctx.setMessagerConfig(transport);
            ctx.setDbConfig(Map.of("connection_string", "sqlite:///team.db"));

            TeamJoinDescriptor descriptor = CliAgentSpawn.descriptorFromContext(ctx);
            Map<String, Object> json = OBJECT_MAPPER.readValue(descriptor.toJson(), new TypeReference<>() {
            });

            assertThat(json).containsEntry("session_id", "session-1");
            assertThat(json).containsEntry("team_name", "team-a");
            assertThat(json).containsEntry("member_name", "dev");
            assertThat(json).containsEntry("role", "teammate");
            assertThat(json).containsEntry("language", "en");
            assertThat(stringMap(json.get("db_config"))).containsEntry("connection_string", "sqlite:///team.db");
            assertThat(stringMap(json.get("transport_config")))
                    .containsEntry("direct_addr", "tcp://127.0.0.1:*")
                    .containsEntry("pubsub_publish_addr", "tcp://pub");
        assertThat(descriptor.toEnv()).containsKey(CliAgentSpawn.TEAM_JOIN_ENV);
        } finally {
            AgentTeamsContext.resetSessionId(token);
        }
    }

    @Test
    void buildCliRuntimeStreamingDrivesATurnThroughFakeProcess() {
        TeamRuntimeContext ctx = context("generic");
        CliAgentSpawn.BuildOptions options = new CliAgentSpawn.BuildOptions(
                null,
                fakeJavaCommand(FakeStreamingCli.class),
                false,
                "openjiuwen-team",
                List.of("openjiuwen-team-mcp"),
                null,
                Map.of()
        );

        MemberRuntime memberRuntime = CliAgentSpawn.buildCliRuntime(ctx, options).toCompletableFuture().join();

        assertThat(memberRuntime).isInstanceOf(ExternalCliRuntime.class);
        ExternalCliRuntime runtime = (ExternalCliRuntime) memberRuntime;
        try {
            Iterator<Object> stream = runtime.runStreaming(Map.of("query", "hello"), "sess-1");
            List<String> contents = new ArrayList<>();
            while (stream.hasNext()) {
                contents.add(contentOf(stream.next()));
            }
            assertThat(contents).contains("echo: hello");
        } finally {
            runtime.aclose().toCompletableFuture().join();
        }
    }

    @Test
    void buildCliRuntimeReturnsReinvokeRuntimeForOneShotAdapter() throws Exception {
        TeamRuntimeContext ctx = context("codex");
        CliAgentSpawn.BuildOptions options = new CliAgentSpawn.BuildOptions(
                null,
                List.of("codex-test"),
                true,
                "openjiuwen-team",
                List.of("openjiuwen-team-mcp", "--stdio"),
                "be precise",
                Map.of(CliAgentSpawn.TEAM_JOIN_ENV, "stale")
        );

        CompletionStage<MemberRuntime> runtime = CliAgentSpawn.buildCliRuntime(ctx, options);
        MemberRuntime memberRuntime = runtime.toCompletableFuture().join();

        assertThat(memberRuntime).isInstanceOf(ReinvokeCliRuntime.class);
        @SuppressWarnings("unchecked")
        Map<String, String> env = (Map<String, String>) field(memberRuntime, "env");
        @SuppressWarnings("unchecked")
        List<String> launchExtraArgs = (List<String>) field(memberRuntime, "launchExtraArgs");

        assertThat(env.get(CliAgentSpawn.TEAM_JOIN_ENV)).isNotEqualTo("stale");
        assertThat(launchExtraArgs).contains(
                "-c",
                "mcp_servers.openjiuwen_team.command=\"openjiuwen-team-mcp\"",
                "mcp_servers.openjiuwen_team.args=[\"--stdio\"]",
                "developer_instructions=\"be precise\""
        );
    }

    @Test
    void missingMemberNameOrCliAgentRaisesConfigError() {
        TeamRuntimeContext noMember = context("codex");
        noMember.setMemberName(null);
        assertThrows(BaseError.class, () -> CliAgentSpawn.descriptorFromContext(noMember));

        TeamRuntimeContext noCli = context(null);
        assertThrows(BaseError.class, () -> CliAgentSpawn.buildCliRuntime(noCli).toCompletableFuture().join());
    }

    private static TeamRuntimeContext context(String cliAgent) {
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(TeamRole.TEAMMATE);
        ctx.setMemberName("dev");
        ctx.setTeamSpec(new TeamSpec("team-a", "Team A", "leader"));
        ctx.getTeamSpec().setLanguage("en");
        ctx.setCliAgent(cliAgent);
        return ctx;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static List<String> fakeJavaCommand(Class<?> mainClass) {
        String executable = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        return List.of(executable, "-cp", System.getProperty("java.class.path"), mainClass.getName());
    }

    @SuppressWarnings("unchecked")
    private static String contentOf(Object chunk) {
        Map<String, Object> payload = (Map<String, Object>) ((OutputSchema) chunk).getPayload();
        return (String) payload.get("content");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stringMap(Object value) {
        return (Map<String, Object>) value;
    }

    public static final class FakeStreamingCli {
        private FakeStreamingCli() {
        }

        public static void main(String[] args) throws Exception {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null) {
                return;
            }
            System.out.println("echo: " + line.trim());
            System.out.println("<<END_OF_TURN>>");
            System.out.flush();
        }
    }
}
