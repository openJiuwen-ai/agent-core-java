/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.TeamHarness;
import com.openjiuwen.agent_teams.agent.MemberRuntime;
import com.openjiuwen.agent_teams.external.ExternalCliRuntime;
import com.openjiuwen.agent_teams.external.ReinvokeCliRuntime;
import com.openjiuwen.agent_teams.external.cli_agent.CliAgentAdapter;
import com.openjiuwen.agent_teams.external.cli_agent.Injector;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.session.stream.OutputSchema;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Supplemental parity tests for the external CLI adapter, runtime, and injector surface.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.external.test_cli_agent} in
 * {@code tests/unit_tests/agent_teams/external/test_cli_agent.py}.</p>
 */
class CliAgentPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/agent_teams/external/test_cli_agent.py";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> pythonCliAgentCases() {
        return pythonNodeIds().map(nodeId -> dynamicTest(nodeId, () -> runPythonCase(nodeId)));
    }

    private static Stream<String> pythonNodeIds() {
        return Stream.of(
                SOURCE + "::test_build_adapter_claude_stream_json",
                SOURCE + "::test_claude_completion_on_result_json",
                SOURCE + "::test_generic_adapter_marker_completion",
                SOURCE + "::test_build_adapter_command_override",
                SOURCE + "::test_build_adapter_unknown_raises",
                SOURCE + "::test_available_adapters_includes_known_clis",
                SOURCE + "::test_claude_mcp_launch_args_use_mcp_config_flag",
                SOURCE + "::test_codex_mcp_launch_args_use_config_override",
                SOURCE + "::test_one_shot_adapters_have_no_mcp_injection",
                SOURCE + "::test_runtime_run_streaming_writes_input_and_consumes_until_complete",
                SOURCE + "::test_runtime_steer_and_follow_up_inject",
                SOURCE + "::test_runtime_abort_stops_turn",
                SOURCE + "::test_streaming_premature_eof_crash_raises_with_stderr",
                SOURCE + "::test_streaming_eof_without_process_does_not_raise",
                SOURCE + "::test_reinvoke_inactivity_timeout_terminates_silent_process",
                SOURCE + "::test_reinvoke_inactivity_timeout_does_not_kill_active_process",
                SOURCE + "::test_reinvoke_abort_terminates_current_subprocess",
                SOURCE + "::test_runtime_conforms_to_member_runtime_protocol",
                SOURCE + "::test_team_harness_exposes_member_runtime_surface",
                SOURCE + "::test_stdin_pipe_injector_writes_newline_framed",
                SOURCE + "::test_hermes_build_turn_command_positional_with_continue",
                SOURCE + "::test_openclaw_build_turn_command_message_and_session",
                SOURCE + "::test_reinvoke_runtime_buffers_followups",
                SOURCE + "::test_reinvoke_runtime_surfaces_failed_turn"
        );
    }

    private static void runPythonCase(String nodeId) throws Exception {
        switch (nodeId) {
            case SOURCE + "::test_build_adapter_claude_stream_json" -> testBuildAdapterClaudeStreamJson();
            case SOURCE + "::test_claude_completion_on_result_json" -> testClaudeCompletionOnResultJson();
            case SOURCE + "::test_generic_adapter_marker_completion" -> testGenericAdapterMarkerCompletion();
            case SOURCE + "::test_build_adapter_command_override" -> testBuildAdapterCommandOverride();
            case SOURCE + "::test_build_adapter_unknown_raises" -> testBuildAdapterUnknownRaises();
            case SOURCE + "::test_available_adapters_includes_known_clis" -> testAvailableAdaptersIncludesKnownClis();
            case SOURCE + "::test_claude_mcp_launch_args_use_mcp_config_flag" ->
                    testClaudeMcpLaunchArgsUseMcpConfigFlag();
            case SOURCE + "::test_codex_mcp_launch_args_use_config_override" ->
                    testCodexMcpLaunchArgsUseConfigOverride();
            case SOURCE + "::test_one_shot_adapters_have_no_mcp_injection" ->
                    testOneShotAdaptersHaveNoMcpInjection();
            case SOURCE + "::test_runtime_run_streaming_writes_input_and_consumes_until_complete" ->
                    testRuntimeRunStreamingWritesInputAndConsumesUntilComplete();
            case SOURCE + "::test_runtime_steer_and_follow_up_inject" -> testRuntimeSteerAndFollowUpInject();
            case SOURCE + "::test_runtime_abort_stops_turn" -> testRuntimeAbortStopsTurn();
            case SOURCE + "::test_streaming_premature_eof_crash_raises_with_stderr" ->
                    testStreamingPrematureEofCrashRaisesWithStderr();
            case SOURCE + "::test_streaming_eof_without_process_does_not_raise" ->
                    testStreamingEofWithoutProcessDoesNotRaise();
            case SOURCE + "::test_reinvoke_inactivity_timeout_terminates_silent_process" ->
                    testReinvokeInactivityTimeoutTerminatesSilentProcess();
            case SOURCE + "::test_reinvoke_inactivity_timeout_does_not_kill_active_process" ->
                    testReinvokeInactivityTimeoutDoesNotKillActiveProcess();
            case SOURCE + "::test_reinvoke_abort_terminates_current_subprocess" ->
                    testReinvokeAbortTerminatesCurrentSubprocess();
            case SOURCE + "::test_runtime_conforms_to_member_runtime_protocol" ->
                    testRuntimeConformsToMemberRuntimeProtocol();
            case SOURCE + "::test_team_harness_exposes_member_runtime_surface" ->
                    testTeamHarnessExposesMemberRuntimeSurface();
            case SOURCE + "::test_stdin_pipe_injector_writes_newline_framed" ->
                    testStdinPipeInjectorWritesNewlineFramed();
            case SOURCE + "::test_hermes_build_turn_command_positional_with_continue" ->
                    testHermesBuildTurnCommandPositionalWithContinue();
            case SOURCE + "::test_openclaw_build_turn_command_message_and_session" ->
                    testOpenclawBuildTurnCommandMessageAndSession();
            case SOURCE + "::test_reinvoke_runtime_buffers_followups" -> testReinvokeRuntimeBuffersFollowups();
            case SOURCE + "::test_reinvoke_runtime_surfaces_failed_turn" -> testReinvokeRuntimeSurfacesFailedTurn();
            default -> throw new IllegalArgumentException("Unknown Python node: " + nodeId);
        }
    }

    private static void testBuildAdapterClaudeStreamJson() throws Exception {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("claude");
        assertThat(adapter.buildCommand()).startsWith("claude");
        assertThat(adapter.buildCommand()).contains("--dangerously-skip-permissions");

        JsonNode framed = OBJECT_MAPPER.readTree(adapter.formatInput("hello"));
        assertThat(framed.get("type").asText()).isEqualTo("user");
        assertThat(framed.get("message").get("content").asText()).isEqualTo("hello");
    }

    private static void testClaudeCompletionOnResultJson() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("claude");
        assertThat(adapter.isTurnComplete("{\"type\":\"result\",\"subtype\":\"success\"}")).isTrue();
        assertThat(adapter.isTurnComplete("{\"type\":\"assistant\"}")).isFalse();
        assertThat(adapter.isTurnComplete("plain text")).isFalse();
    }

    private static void testGenericAdapterMarkerCompletion() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("generic");
        assertThat(adapter.formatInput("hi")).isEqualTo("hi");
        assertThat(adapter.isTurnComplete("done <<END_OF_TURN>> now")).isTrue();
        assertThat(adapter.isTurnComplete("still working")).isFalse();
    }

    private static void testBuildAdapterCommandOverride() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("claude", List.of("/usr/local/bin/claude", "-x"));
        assertThat(adapter.buildCommand()).containsExactly("/usr/local/bin/claude", "-x");
    }

    private static void testBuildAdapterUnknownRaises() {
        assertThatThrownBy(() -> CliAgentAdapter.buildAdapter("nope")).isInstanceOf(BaseError.class);
    }

    private static void testAvailableAdaptersIncludesKnownClis() {
        assertThat(CliAgentAdapter.availableAdapters())
                .contains("claude", "codex", "openclaw", "hermes", "generic");
    }

    private static void testClaudeMcpLaunchArgsUseMcpConfigFlag() throws Exception {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("claude");
        List<String> args = adapter.mcpLaunchArgs("openjiuwen-team", List.of("openjiuwen-team-mcp"));
        assertThat(args.get(0)).isEqualTo("--mcp-config");

        JsonNode config = OBJECT_MAPPER.readTree(args.get(1));
        JsonNode server = config.get("mcpServers").get("openjiuwen-team");
        assertThat(server.get("command").asText()).isEqualTo("openjiuwen-team-mcp");
        assertThat(server.get("args")).isEmpty();
    }

    private static void testCodexMcpLaunchArgsUseConfigOverride() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("codex");
        List<String> args = adapter.mcpLaunchArgs("openjiuwen-team", List.of("openjiuwen-team-mcp", "--flag"));
        assertThat(args).containsExactly(
                "-c",
                "mcp_servers.openjiuwen_team.command=\"openjiuwen-team-mcp\"",
                "-c",
                "mcp_servers.openjiuwen_team.args=[\"--flag\"]"
        );
    }

    private static void testOneShotAdaptersHaveNoMcpInjection() {
        for (String name : List.of("openclaw", "hermes", "generic")) {
            assertThat(CliAgentAdapter.buildAdapter(name).mcpLaunchArgs("t", List.of("openjiuwen-team-mcp"))).isEmpty();
        }
    }

    private static void testRuntimeRunStreamingWritesInputAndConsumesUntilComplete() {
        RecordingInjector injector = new RecordingInjector();
        ExternalCliRuntime runtime = new ExternalCliRuntime(
                "dev-1",
                CliAgentAdapter.buildAdapter("generic"),
                injector,
                List.of("thinking...", "more <<END_OF_TURN>>", "next-turn-line").iterator()
        );

        assertThat(drainRuntime(runtime, "do it")).containsExactly("thinking...", "more <<END_OF_TURN>>");
        assertThat(injector.writes).containsExactly("do it");
    }

    private static void testRuntimeSteerAndFollowUpInject() {
        RecordingInjector injector = new RecordingInjector();
        ExternalCliRuntime runtime = new ExternalCliRuntime(
                "dev-1",
                CliAgentAdapter.buildAdapter("generic"),
                injector,
                List.<String>of().iterator()
        );

        runtime.steer("urgent").toCompletableFuture().join();
        runtime.followUp("later").toCompletableFuture().join();

        assertThat(injector.writes).containsExactly("urgent", "later");
    }

    private static void testRuntimeAbortStopsTurn() {
        RecordingInjector injector = new RecordingInjector();
        AtomicReference<ExternalCliRuntime> runtimeRef = new AtomicReference<>();
        Iterator<String> lines = new Iterator<>() {
            private int index;

            @Override
            public boolean hasNext() {
                return index < 3;
            }

            @Override
            public String next() {
                index += 1;
                if (index == 1) {
                    runtimeRef.get().abort().toCompletableFuture().join();
                    return "line-1";
                }
                if (index == 2) {
                    return "line-2";
                }
                return "should-not-matter <<END_OF_TURN>>";
            }
        };
        ExternalCliRuntime runtime = new ExternalCliRuntime(
                "dev-1",
                CliAgentAdapter.buildAdapter("generic"),
                injector,
                lines
        );
        runtimeRef.set(runtime);

        assertThat(drainRuntime(runtime, "go")).containsExactly("line-1");
    }

    private static void testStreamingPrematureEofCrashRaisesWithStderr() {
        ExternalCliRuntime runtime = new ExternalCliRuntime(
                "dev-1",
                CliAgentAdapter.buildAdapter("generic"),
                new RecordingInjector(),
                List.of("partial output", "more partial").iterator(),
                new FakeExitedProcess(1, "Error: invalid api key\n")
        );

        assertThatThrownBy(() -> drainRuntime(runtime, "go"))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("invalid api key");
    }

    private static void testStreamingEofWithoutProcessDoesNotRaise() {
        ExternalCliRuntime runtime = new ExternalCliRuntime(
                "dev-1",
                CliAgentAdapter.buildAdapter("generic"),
                new RecordingInjector(),
                List.of("some output").iterator()
        );

        assertThatCode(() -> drainRuntime(runtime, "go")).doesNotThrowAnyException();
    }

    private static void testReinvokeInactivityTimeoutTerminatesSilentProcess() {
        ReinvokeCliRuntime runtime = new ReinvokeCliRuntime(
                "hang-1",
                plainOneShotAdapter("fake-hang", fakeJavaCommand(SilentCli.class)),
                Map.of(),
                null,
                "sess",
                List.of(),
                0.3d,
                null
        );
        long started = System.nanoTime();
        assertThatCode(() -> drainRuntime(runtime, "do it")).doesNotThrowAnyException();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(elapsedMillis).isLessThan(5_000L);
    }

    private static void testReinvokeInactivityTimeoutDoesNotKillActiveProcess() {
        ReinvokeCliRuntime runtime = new ReinvokeCliRuntime(
                "active-1",
                plainOneShotAdapter("fake-active", fakeJavaCommand(ActiveCli.class)),
                Map.of(),
                null,
                "sess",
                List.of(),
                0.5d,
                null
        );

        assertThat(drainRuntime(runtime, "work")).containsExactly("0", "1", "2", "3", "4");
    }

    private static void testReinvokeAbortTerminatesCurrentSubprocess() {
        ReinvokeCliRuntime runtime = new ReinvokeCliRuntime(
                "long-1",
                plainOneShotAdapter("fake-long", fakeJavaCommand(SilentCli.class)),
                Map.of(),
                null,
                "sess",
                List.of(),
                60.0d,
                null
        );

        CompletableFuture<List<String>> running = CompletableFuture.supplyAsync(() -> drainRuntime(runtime, "long task"));
        sleepMillis(300L);
        runtime.abort().toCompletableFuture().join();

        assertThatCode(() -> running.get(5L, TimeUnit.SECONDS)).doesNotThrowAnyException();
    }

    private static void testRuntimeConformsToMemberRuntimeProtocol() {
        ExternalCliRuntime runtime = new ExternalCliRuntime(
                "dev-1",
                CliAgentAdapter.buildAdapter("generic"),
                new RecordingInjector(),
                List.<String>of().iterator()
        );

        assertThat(MemberRuntime.isRuntime(runtime)).isTrue();
    }

    private static void testTeamHarnessExposesMemberRuntimeSurface() throws Exception {
        for (String method : List.of(
                "runStreaming",
                "steer",
                "followUp",
                "abort",
                "initCwdForRound",
                "hasPendingInterrupt",
                "isPendingInterruptResumeValid",
                "findRails",
                "registerRail",
                "unregisterRail",
                "registerMemberTools",
                "injectMemberMemory",
                "runAgentCustomizer",
                "workspace",
                "sysOperation"
        )) {
            assertThat(Stream.of(TeamHarness.class.getMethods()).map(java.lang.reflect.Method::getName))
                    .contains(method);
        }
    }

    private static void testStdinPipeInjectorWritesNewlineFramed() {
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        com.openjiuwen.agent_teams.external.cli_agent.StdinPipeInjector injector =
                new com.openjiuwen.agent_teams.external.cli_agent.StdinPipeInjector(outputStream);

        injector.write("hello").toCompletableFuture().join();
        injector.aclose().toCompletableFuture().join();

        assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo("hello\n");
    }

    private static void testHermesBuildTurnCommandPositionalWithContinue() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("hermes");
        assertThat(adapter.supportsStdinInjection()).isFalse();
        List<String> first = adapter.buildTurnCommand("do it", "s1", true);
        assertThat(first.get(0)).isEqualTo("hermes");
        assertThat(first.get(first.size() - 1)).isEqualTo("do it");
        assertThat(first).doesNotContain("--continue");

        List<String> later = adapter.buildTurnCommand("again", "s1", false);
        assertThat(later).contains("--continue");
        assertThat(later.get(later.size() - 1)).isEqualTo("again");
    }

    private static void testOpenclawBuildTurnCommandMessageAndSession() {
        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter("openclaw");
        assertThat(adapter.supportsStdinInjection()).isFalse();

        List<String> argv = adapter.buildTurnCommand("review", "sess-9", true);

        assertThat(argv).contains("--session-id", "sess-9", "--message", "review");
        assertThat(argv.get(argv.indexOf("--session-id") + 1)).isEqualTo("sess-9");
        assertThat(argv.get(argv.indexOf("--message") + 1)).isEqualTo("review");
    }

    private static void testReinvokeRuntimeBuffersFollowups() {
        ReinvokeCliRuntime runtime = new ReinvokeCliRuntime(
                "cli-1",
                CliAgentAdapter.buildAdapter("hermes", fakeJavaCommand(EchoArgumentCli.class)),
                Map.of(),
                null,
                "sess",
                List.of(),
                5.0d,
                null
        );

        runtime.steer("a").toCompletableFuture().join();
        runtime.followUp("b").toCompletableFuture().join();

        assertThat(drainRuntime(runtime, "initial"))
                .containsExactly("oneshot: initial", "oneshot: a\\n\\n---\\n\\nb");
    }

    private static void testReinvokeRuntimeSurfacesFailedTurn() {
        ReinvokeCliRuntime runtime = new ReinvokeCliRuntime(
                "codex-1",
                plainOneShotAdapter("fake-broke", fakeJavaCommand(FailingCli.class)),
                Map.of(),
                null,
                "sess",
                List.of(),
                5.0d,
                null
        );

        assertThatCode(() -> drainRuntime(runtime, "write the file")).doesNotThrowAnyException();
    }

    private static CliAgentAdapter plainOneShotAdapter(String name, List<String> command) {
        return new CliAgentAdapter(
                name,
                command,
                CliAgentAdapter.INPUT_TEXT,
                CliAgentAdapter.COMPLETION_NONE,
                false,
                false,
                null,
                null,
                null,
                List.of(),
                CliAgentAdapter.MCP_INJECT_NONE,
                CliAgentAdapter.SYSTEM_PROMPT_NONE,
                List.of()
        );
    }

    private static List<String> fakeJavaCommand(Class<?> mainClass) {
        String executable = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        return List.of(executable, "-cp", System.getProperty("java.class.path"), mainClass.getName());
    }

    private static List<String> drainRuntime(MemberRuntime runtime, String query) {
        Iterator<Object> stream = runtime.runStreaming(Map.of("query", query), "sess-1");
        List<String> contents = new ArrayList<>();
        while (stream.hasNext()) {
            contents.add(contentOf(stream.next()));
        }
        return contents;
    }

    @SuppressWarnings("unchecked")
    private static String contentOf(Object chunk) {
        Map<String, Object> payload = (Map<String, Object>) ((OutputSchema) chunk).getPayload();
        return String.valueOf(payload.get("content"));
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static final class RecordingInjector implements Injector {
        private final List<String> writes = new ArrayList<>();
        private boolean closed;

        @Override
        public CompletionStage<Void> write(String text) {
            writes.add(text);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> aclose() {
            closed = true;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FakeExitedProcess extends Process {
        private final int returnCode;
        private final byte[] stderr;

        private FakeExitedProcess(int returnCode, String stderr) {
            this.returnCode = returnCode;
            this.stderr = stderr.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(stderr);
        }

        @Override
        public int waitFor() {
            return returnCode;
        }

        @Override
        public int exitValue() {
            return returnCode;
        }

        @Override
        public void destroy() {
        }

        @Override
        public boolean isAlive() {
            return false;
        }
    }

    public static final class SilentCli {
        private SilentCli() {
        }

        public static void main(String[] args) throws Exception {
            Thread.sleep(30_000L);
        }
    }

    public static final class ActiveCli {
        private ActiveCli() {
        }

        public static void main(String[] args) throws Exception {
            for (int index = 0; index < 5; index += 1) {
                System.out.println(index);
                System.out.flush();
                Thread.sleep(100L);
            }
        }
    }

    public static final class EchoArgumentCli {
        private EchoArgumentCli() {
        }

        public static void main(String[] args) {
            String prompt = args.length == 0 ? "" : args[args.length - 1];
            System.out.println("oneshot: " + prompt.replace("\n", "\\n"));
        }
    }

    public static final class FailingCli {
        private FailingCli() {
        }

        public static void main(String[] args) {
            System.err.print("X".repeat(200_000));
            System.err.println("\nError: insufficient credits");
            System.exit(1);
        }
    }
}
